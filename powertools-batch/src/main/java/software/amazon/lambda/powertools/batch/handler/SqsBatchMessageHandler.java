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

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import software.amazon.lambda.powertools.utilities.EventDeserializer;

/**
 * A batch message processor for SQS batches.
 *
 * @param <M> The user-defined type of the message payload
 * @see <a href="https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html#services-sqs-batchfailurereporting">SQS Batch failure reporting</a>
 */
public class SqsBatchMessageHandler<M> extends
        AbstractBatchMessageHandler<SQSEvent, SQSEvent.SQSMessage, M, SQSBatchResponse.BatchItemFailure, SQSBatchResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqsBatchMessageHandler.class);

    // The attribute on an SQS-FIFO message used to record the message group ID
    // https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html#sample-fifo-queues-message-event
    private static final String MESSAGE_GROUP_ID_KEY = "MessageGroupId";

    public SqsBatchMessageHandler(BiConsumer<M, Context> messageHandler, Class<M> messageClass,
            BiConsumer<SQSEvent.SQSMessage, Context> rawMessageHandler,
            Consumer<SQSEvent.SQSMessage> successHandler,
            BiConsumer<SQSEvent.SQSMessage, Throwable> failureHandler) {
        super(rawMessageHandler, messageHandler, messageClass, successHandler, failureHandler);
    }

    @Override
    protected List<SQSEvent.SQSMessage> getRecords(SQSEvent event) {
        return event.getRecords();
    }

    @Override
    protected SQSBatchResponse.BatchItemFailure createFailure(SQSEvent.SQSMessage message) {
        return SQSBatchResponse.BatchItemFailure.builder().withItemIdentifier(message.getMessageId()).build();
    }

    @Override
    protected SQSBatchResponse createResponse(List<SQSBatchResponse.BatchItemFailure> failures) {
        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    @Override
    protected String getRecordIdentifier(SQSEvent.SQSMessage message) {
        return message.getMessageId();
    }

    @Override
    protected M deserialize(SQSEvent.SQSMessage message) {
        return EventDeserializer.extractDataFrom(message).as(getMessageClass());
    }

    @Override
    protected boolean shouldFailRemainingOnFailure(SQSEvent.SQSMessage message) {
        return getMessageGroupId(message) != null;
    }

    @Override
    protected void onRemainingItemsFailed(SQSEvent.SQSMessage failedMessage) {
        LOGGER.info(
                "A message in a batch with messageGroupId {} and messageId {} failed; failing the rest of the batch too",
                getMessageGroupId(failedMessage), failedMessage.getMessageId());
    }

    @Override
    protected void validateParallelProcessing(SQSEvent event) {
        if (isFIFOEnabled(event)) {
            throw new UnsupportedOperationException(
                    "FIFO queues are not supported in parallel mode, use the processBatch method instead");
        }
    }

    private String getMessageGroupId(SQSEvent.SQSMessage message) {
        return message.getAttributes() != null ? message.getAttributes().get(MESSAGE_GROUP_ID_KEY) : null;
    }

    private boolean isFIFOEnabled(SQSEvent sqsEvent) {
        return !sqsEvent.getRecords().isEmpty()
                && sqsEvent.getRecords().get(0).getAttributes().get(MESSAGE_GROUP_ID_KEY) != null;
    }
}
