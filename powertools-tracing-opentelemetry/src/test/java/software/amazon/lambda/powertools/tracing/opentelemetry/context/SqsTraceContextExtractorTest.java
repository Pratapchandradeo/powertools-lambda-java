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

package software.amazon.lambda.powertools.tracing.opentelemetry.context;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.lambda.powertools.tracing.opentelemetry.support.InMemoryTracing.attribute;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.common.stubs.TestLambdaContext;
import software.amazon.lambda.powertools.tracing.opentelemetry.TraceContextPropagationMode;
import software.amazon.lambda.powertools.tracing.opentelemetry.TracingOpenTelemetry;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.OpenTelemetryProvider;
import software.amazon.lambda.powertools.tracing.opentelemetry.support.InMemoryTracing;

class SqsTraceContextExtractorTest {
    private static final String TRACEPARENT_ONE =
            "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01";
    private static final String TRACEPARENT_TWO =
            "00-cccccccccccccccccccccccccccccccc-dddddddddddddddd-01";

    private final SqsTraceContextExtractor extractor = new SqsTraceContextExtractor();
    private InMemoryTracing tracing;

    @BeforeEach
    void setUp() {
        tracing = InMemoryTracing.install();
    }

    @AfterEach
    void tearDown() {
        tracing.close();
    }

    @Test
    void extractsAllRemoteContextsFromBatch() {
        ExtractedTraceContext extracted = extractor.extract(
                batchEvent(), io.opentelemetry.context.Context.root(),
                OpenTelemetryProvider.defaultPropagator());

        assertThat(extracted.spanKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(extracted.spanContexts()).hasSize(2);
        assertThat(extracted.spanContexts())
                .extracting(ctx -> ctx.getTraceId())
                .containsExactly(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "cccccccccccccccccccccccccccccccc"
                );
    }

    @Test
    void parentModeUsesFirstMessageAsParent() {
        new SqsHandler().handleRequest(batchEvent(), new TestLambdaContext());

        SpanData span = tracing.finishedSpan("handleRequest");
        assertThat(span.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(span.getParentSpanContext().getTraceId())
                .isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(span.getLinks()).isEmpty();
        assertThat(span.getAttributes().get(AttributeKey.longKey("messaging.batch.message_count")))
                .isEqualTo(2L);
        assertThat(attribute(span, "messaging.system")).isEqualTo("aws_sqs");
        assertThat(attribute(span, "messaging.destination.name")).isEqualTo("orders");
    }

    @Test
    void linkModeAddsRemoteContextsAsLinks() {
        tracing.close();
        tracing = InMemoryTracing.install(TraceContextPropagationMode.LINK);

        new SqsHandler().handleRequest(batchEvent(), new TestLambdaContext());

        SpanData span = tracing.finishedSpan("handleRequest");
        assertThat(span.getLinks())
                .extracting(link -> link.getSpanContext().getTraceId())
                .containsExactly(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "cccccccccccccccccccccccccccccccc"
                );
        assertThat(span.getLinks()).extracting(LinkData::getSpanContext).allMatch(ctx -> ctx.isValid());
    }

    private static SQSEvent batchEvent() {
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(
                message("aaaaaaaa", TRACEPARENT_ONE),
                message("cccccccc", TRACEPARENT_TWO)
        ));
        return event;
    }

    private static SQSEvent.SQSMessage message(String id, String traceparent) {
        SQSEvent.MessageAttribute attribute = new SQSEvent.MessageAttribute();
        attribute.setStringValue(traceparent);
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId(id);
        message.setEventSourceArn("arn:aws:sqs:us-east-1:123456789012:orders");
        message.setMessageAttributes(Map.of("traceparent", attribute));
        return message;
    }

    static class SqsHandler implements RequestHandler<SQSEvent, String> {
        @Override
        @TracingOpenTelemetry
        public String handleRequest(SQSEvent input, Context context) {
            return "ok";
        }
    }
}
