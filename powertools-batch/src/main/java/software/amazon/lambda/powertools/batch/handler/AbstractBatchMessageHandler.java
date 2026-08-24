/*
 * Copyright 2023 Amazon.com, Inc. or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package software.amazon.lambda.powertools.batch.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;

import software.amazon.lambda.powertools.batch.internal.MultiThreadMDC;
import software.amazon.lambda.powertools.batch.internal.XRayTraceEntityPropagator;

/**
 * Shared sequential and parallel batch orchestration. Event-source handlers supply
 * record extraction, failure identifiers, and the Lambda partial-failure response.
 *
 * @param <E> The Lambda batch event type
 * @param <T> The type of a single record in the batch
 * @param <M> The user-defined deserialized payload type
 * @param <F> The batch item failure type
 * @param <R> The Lambda batch response type
 */
abstract class AbstractBatchMessageHandler<E, T, M, F, R> implements BatchMessageHandler<E, R> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final BiConsumer<T, Context> rawMessageHandler;
    private final BiConsumer<M, Context> messageHandler;
    private final Class<M> messageClass;
    private final Consumer<T> successHandler;
    private final BiConsumer<T, Throwable> failureHandler;

    protected AbstractBatchMessageHandler(BiConsumer<T, Context> rawMessageHandler,
            BiConsumer<M, Context> messageHandler,
            Class<M> messageClass,
            Consumer<T> successHandler,
            BiConsumer<T, Throwable> failureHandler) {
        this.rawMessageHandler = rawMessageHandler;
        this.messageHandler = messageHandler;
        this.messageClass = messageClass;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    @Override
    public R processBatch(E event, Context context) {
        List<T> records = getRecords(event);
        List<F> batchItemFailures = new ArrayList<>();
        boolean failRemaining = false;

        for (T record : records) {
            if (failRemaining) {
                batchItemFailures.add(createFailure(record));
                continue;
            }

            Optional<F> failure = processBatchItem(record, context);
            if (failure.isPresent()) {
                batchItemFailures.add(failure.get());
                if (shouldFailRemainingOnFailure(record)) {
                    failRemaining = true;
                    onRemainingItemsFailed(record);
                }
            }
        }

        return createResponse(batchItemFailures);
    }

    @Override
    public R processBatchInParallel(E event, Context context) {
        validateParallelProcessing(event);

        MultiThreadMDC multiThreadMDC = new MultiThreadMDC();
        Object capturedSubsegment = XRayTraceEntityPropagator.captureTraceEntity();

        List<F> batchItemFailures = getRecords(event)
                .parallelStream()
                .map(record -> processInWorkerThread(record, context, multiThreadMDC, capturedSubsegment))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        return createResponse(batchItemFailures);
    }

    @Override
    public R processBatchInParallel(E event, Context context, Executor executor) {
        validateParallelProcessing(event);

        MultiThreadMDC multiThreadMDC = new MultiThreadMDC();
        Object capturedSubsegment = XRayTraceEntityPropagator.captureTraceEntity();

        List<F> batchItemFailures = new ArrayList<>();
        List<CompletableFuture<Void>> futures = getRecords(event).stream()
                .map(record -> CompletableFuture.runAsync(() -> processInWorkerThread(record, context, multiThreadMDC,
                        capturedSubsegment).ifPresent(batchItemFailures::add), executor))
                .collect(Collectors.toList());
        futures.forEach(CompletableFuture::join);

        return createResponse(batchItemFailures);
    }

    /**
     * Records in the event, in the order they should be processed sequentially.
     */
    protected abstract List<T> getRecords(E event);

    /**
     * Partial-failure item for the given record.
     */
    protected abstract F createFailure(T record);

    /**
     * Event-source response containing the collected item failures.
     */
    protected abstract R createResponse(List<F> failures);

    /**
     * Identifier used in processing logs for the given record.
     */
    protected abstract String getRecordIdentifier(T record);

    /**
     * Deserializes a record body when a typed message handler is used.
     */
    protected abstract M deserialize(T record);

    /**
     * Whether a failed record should cause the rest of the batch to be marked failed
     * without further processing. Default is false; SQS FIFO overrides this.
     */
    protected boolean shouldFailRemainingOnFailure(T record) {
        return false;
    }

    /**
     * Called once when sequential processing starts failing the remainder of the batch.
     */
    protected void onRemainingItemsFailed(T failedRecord) {
        // Default: no additional event-source-specific action
    }

    /**
     * Validates that the event can be processed in parallel. SQS FIFO overrides this
     * to throw {@link UnsupportedOperationException}.
     */
    protected void validateParallelProcessing(E event) {
        // Default: all event sources support parallel processing
    }

    private Optional<F> processInWorkerThread(T record, Context context, MultiThreadMDC multiThreadMDC,
            Object capturedSubsegment) {
        AtomicReference<Optional<F>> result = new AtomicReference<>();

        XRayTraceEntityPropagator.runWithEntity(capturedSubsegment, () -> {
            multiThreadMDC.copyMDCToThread(Thread.currentThread().getName());
            try {
                result.set(processBatchItem(record, context));
            } finally {
                multiThreadMDC.removeThread(Thread.currentThread().getName());
            }
        });

        return result.get();
    }

    private Optional<F> processBatchItem(T record, Context context) {
        try {
            logger.debug("Processing item {}", getRecordIdentifier(record));

            if (this.rawMessageHandler != null) {
                rawMessageHandler.accept(record, context);
            } else {
                M messageDeserialized = deserialize(record);
                messageHandler.accept(messageDeserialized, context);
            }

            if (this.successHandler != null) {
                this.successHandler.accept(record);
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error while processing record with id {}: {}, adding it to batch item failures",
                    getRecordIdentifier(record), e.getMessage());
            logger.error("Error was", e);

            if (this.failureHandler != null) {
                // A failing failure handler is no reason to fail the batch
                try {
                    this.failureHandler.accept(record, e);
                } catch (Exception e2) {
                    logger.warn("failureHandler threw handling failure", e2);
                }
            }

            return Optional.of(createFailure(record));
        }
    }

    protected Class<M> getMessageClass() {
        return messageClass;
    }
}
