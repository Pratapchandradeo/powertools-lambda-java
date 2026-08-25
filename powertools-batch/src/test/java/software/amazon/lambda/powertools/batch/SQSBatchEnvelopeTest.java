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

package software.amazon.lambda.powertools.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.tests.annotations.Event;

import software.amazon.lambda.powertools.batch.handler.BatchMessageHandler;
import software.amazon.lambda.powertools.batch.model.DummyInput;
import software.amazon.lambda.powertools.batch.model.Product;
import software.amazon.lambda.powertools.utilities.EventPaths;

class SQSBatchEnvelopeTest {
    @Mock
    private Context context;

    private final List<DummyInput> processed = Collections.synchronizedList(new ArrayList<>());

    private void processDummy(DummyInput input) {
        processed.add(input);
        if ("fail".equals(input.getField())) {
            throw new RuntimeException("forced failure");
        }
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_event.json", type = SQSEvent.class)
    void snsInSqsWithEnvelope_shouldDeserializeInnerPayload(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .withEnvelope(EventPaths.SQS_SNS)
                .buildWithMessageHandler(this::processDummy, DummyInput.class);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(response.getBatchItemFailures()).isEmpty();
        assertThat(processed).containsExactly(new DummyInput("dummy", "dummy-name"));
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_event.json", type = SQSEvent.class)
    void snsInSqsWithThreeArgBuild_shouldDeserializeInnerPayload(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .buildWithMessageHandler(this::processDummy, DummyInput.class, EventPaths.SQS_SNS);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(response.getBatchItemFailures()).isEmpty();
        assertThat(processed).containsExactly(new DummyInput("dummy", "dummy-name"));
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_event.json", type = SQSEvent.class)
    void snsInSqsWithoutEnvelope_shouldNotUnwrapSnsMessage(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .buildWithMessageHandler(this::processDummy, DummyInput.class);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(response.getBatchItemFailures()).isEmpty();
        assertThat(processed).containsExactly(new DummyInput(null, null));
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_event_batch.json", type = SQSEvent.class)
    void snsInSqsPartialFailure_shouldReportOnlyFailedItem(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .withEnvelope(EventPaths.SQS_SNS)
                .buildWithMessageHandler(this::processDummy, DummyInput.class);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(processed).containsExactly(
                new DummyInput("dummy", "dummy-name"),
                new DummyInput("fail", "fail-name"));
        assertThat(response.getBatchItemFailures()).hasSize(1);
        assertThat(response.getBatchItemFailures().get(0).getItemIdentifier())
                .isEqualTo("e9144555-9a4f-4ec3-99a0-34ce359b4b54");
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_event_batch.json", type = SQSEvent.class)
    void snsInSqsParallel_shouldUnwrapEachRecord(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .withEnvelope(EventPaths.SQS_SNS)
                .buildWithMessageHandler(this::processDummy, DummyInput.class);

        SQSBatchResponse response = handler.processBatchInParallel(event, context);

        assertThat(processed).containsExactlyInAnyOrder(
                new DummyInput("dummy", "dummy-name"),
                new DummyInput("fail", "fail-name"));
        assertThat(response.getBatchItemFailures()).hasSize(1);
        assertThat(response.getBatchItemFailures().get(0).getItemIdentifier())
                .isEqualTo("e9144555-9a4f-4ec3-99a0-34ce359b4b54");
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_fifo_event.json", type = SQSEvent.class)
    void snsInSqsFifo_shouldFailRemainingAfterFirstFailure(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .withEnvelope(EventPaths.SQS_SNS)
                .buildWithMessageHandler(this::processDummy, DummyInput.class);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(processed).containsExactly(
                new DummyInput("dummy", "dummy-name"),
                new DummyInput("fail", "fail-name"));
        assertThat(response.getBatchItemFailures())
                .extracting(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
                .containsExactly(
                        "e9144555-9a4f-4ec3-99a0-34ce359b4b54",
                        "f9144555-9a4f-4ec3-99a0-34ce359b4b54");
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_mixed_event.json", type = SQSEvent.class)
    void mixedSnsAndPlainSqs_shouldFailPlainRecordWhenEnvelopeSet(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .withEnvelope(EventPaths.SQS_SNS)
                .buildWithMessageHandler(this::processDummy, DummyInput.class);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(processed).containsExactly(new DummyInput("dummy", "dummy-name"));
        assertThat(response.getBatchItemFailures()).hasSize(1);
        assertThat(response.getBatchItemFailures().get(0).getItemIdentifier())
                .isEqualTo("e9144555-9a4f-4ec3-99a0-34ce359b4b54");
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_event.json", type = SQSEvent.class)
    void badEnvelope_shouldMarkItemFailed(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .withEnvelope("does.not.exist")
                .buildWithMessageHandler(this::processDummy, DummyInput.class);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(processed).isEmpty();
        assertThat(response.getBatchItemFailures()).hasSize(1);
        assertThat(response.getBatchItemFailures().get(0).getItemIdentifier())
                .isEqualTo("dummy-message-id");
    }

    @ParameterizedTest
    @Event(value = "sqs_event.json", type = SQSEvent.class)
    void plainSqsWithoutEnvelope_shouldStillDeserializeProduct(SQSEvent event) {
        List<Product> products = new ArrayList<>();
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .buildWithMessageHandler((Product product) -> products.add(product), Product.class);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(response.getBatchItemFailures()).isEmpty();
        assertThat(products).hasSize(3);
        assertThat(products.get(0)).isEqualTo(new Product(1234, "product", 42));
    }

    @ParameterizedTest
    @Event(value = "sqs_event.json", type = SQSEvent.class)
    void plainSqsWithSnsEnvelope_shouldFailAllItems(SQSEvent event) {
        BatchMessageHandler<SQSEvent, SQSBatchResponse> handler = new BatchMessageHandlerBuilder()
                .withSqsBatchHandler()
                .withEnvelope(EventPaths.SQS_SNS)
                .buildWithMessageHandler(this::processDummy, DummyInput.class);

        SQSBatchResponse response = handler.processBatch(event, context);

        assertThat(processed).isEmpty();
        assertThat(response.getBatchItemFailures()).hasSize(3);
    }
}
