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

package software.amazon.lambda.powertools.tracing.opentelemetry.internal;

import static org.apache.commons.lang3.reflect.FieldUtils.writeStaticField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_ACCOUNT_ID;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_RESOURCE_ID;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_COLDSTART;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_INVOCATION_ID;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.POWERTOOLS_SERVICE;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.RESPONSE_ATTRIBUTE;
import static software.amazon.lambda.powertools.tracing.opentelemetry.support.InMemoryTracing.attribute;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import software.amazon.lambda.powertools.common.internal.LambdaHandlerProcessor;
import software.amazon.lambda.powertools.common.stubs.TestLambdaContext;
import software.amazon.lambda.powertools.tracing.opentelemetry.handlers.OtelHandlerCustomSpanName;
import software.amazon.lambda.powertools.tracing.opentelemetry.handlers.OtelHandlerDisabled;
import software.amazon.lambda.powertools.tracing.opentelemetry.handlers.OtelHandlerEnabled;
import software.amazon.lambda.powertools.tracing.opentelemetry.handlers.OtelHandlerForError;
import software.amazon.lambda.powertools.tracing.opentelemetry.handlers.OtelHandlerForResponse;
import software.amazon.lambda.powertools.tracing.opentelemetry.handlers.OtelStreamHandlerEnabled;
import software.amazon.lambda.powertools.tracing.opentelemetry.nonhandler.OtelNonHandler;
import software.amazon.lambda.powertools.tracing.opentelemetry.support.InMemoryTracing;

@SetEnvironmentVariable(key = "POWERTOOLS_TRACER_CAPTURE_RESPONSE", value = "false")
@SetEnvironmentVariable(key = "POWERTOOLS_TRACER_CAPTURE_ERROR", value = "false")
class TracingOpenTelemetryAspectTest {
    private InMemoryTracing tracing;
    private Context context;

    @BeforeEach
    void setUp() throws IllegalAccessException {
        writeStaticField(LambdaHandlerProcessor.class, "isColdStart", null, true);
        tracing = InMemoryTracing.install();
        context = new TestLambdaContext();
    }

    @AfterEach
    void tearDown() {
        tracing.close();
    }

    @Test
    void shouldCaptureHandlerColdStartAndInvocationAttributes() {
        new OtelHandlerEnabled().handleRequest("in", context);

        SpanData span = tracing.finishedSpan("handleRequest");
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getAttributes().get(AttributeKey.booleanKey(FAAS_COLDSTART))).isTrue();
        assertThat(attribute(span, FAAS_INVOCATION_ID)).isEqualTo("test-request-id");
        assertThat(attribute(span, CLOUD_RESOURCE_ID))
                .isEqualTo("arn:aws:lambda:us-east-1:123456789012:function:test");
        assertThat(attribute(span, CLOUD_ACCOUNT_ID)).isEqualTo("123456789012");
        assertThat(attribute(span, POWERTOOLS_SERVICE)).isEqualTo("lambdaHandler");
        assertThat(attribute(span, RESPONSE_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldUseCustomSpanName() {
        new OtelHandlerCustomSpanName().handleRequest("in", context);

        assertThat(tracing.exporter().getFinishedSpanItems())
                .extracting(SpanData::getName)
                .containsExactly("checkout");
    }

    @Test
    void shouldCaptureNonHandlerMethodAsInternalSpan() {
        new OtelNonHandler().doSomething();

        SpanData span = tracing.finishedSpan("doSomething");
        assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(span.getAttributes().get(AttributeKey.booleanKey(FAAS_COLDSTART))).isNull();
    }

    @Test
    void shouldCaptureNonHandlerCustomName() {
        new OtelNonHandler().doSomethingCustomName();

        assertThat(tracing.finishedSpan("custom").getName()).isEqualTo("custom");
    }

    @Test
    void shouldCaptureResponseWhenEnabled() {
        new OtelHandlerForResponse().handleRequest("in", context);

        assertThat(attribute(tracing.finishedSpan("handleRequest"), RESPONSE_ATTRIBUTE))
                .contains("captured-response");
    }

    @Test
    @SetEnvironmentVariable(key = "POWERTOOLS_TRACER_CAPTURE_RESPONSE", value = "true")
    void shouldCaptureResponseFromEnvironmentVariable() {
        new OtelHandlerEnabled().handleRequest("in", context);

        assertThat(attribute(tracing.finishedSpan("handleRequest"), RESPONSE_ATTRIBUTE))
                .contains("ok");
    }

    @Test
    void shouldRecordExceptionWhenCaptureErrorEnabled() {
        assertThatThrownBy(() -> new OtelHandlerForError().handleRequest("in", context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        SpanData span = tracing.finishedSpan("handleRequest");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).isNotEmpty();
    }

    @Test
    void shouldNotRecordExceptionWhenCaptureDisabled() {
        assertThatThrownBy(() -> new OtelHandlerDisabled().handleRequest("in", context))
                .isInstanceOf(IllegalStateException.class);

        SpanData span = tracing.finishedSpan("handleRequest");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        assertThat(span.getEvents()).isEmpty();
    }

    @Test
    void shouldTraceStreamHandler() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new OtelStreamHandlerEnabled().handleRequest(
                new ByteArrayInputStream(new byte[0]), output, context);

        SpanData span = tracing.finishedSpan("handleRequest");
        assertThat(attribute(span, POWERTOOLS_SERVICE)).isEqualTo("streamHandler");
        assertThat(output.toString()).isEqualTo("ok");
    }

    @Test
    void shouldExtractApiGatewayParentAndEnrichSpan() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("GET");
        event.setPath("/checkout");
        event.setHeaders(Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        ));

        new OtelHandlerEnabled().handleRequest(event, context);

        SpanData span = tracing.finishedSpan("handleRequest");
        assertThat(span.getParentSpanContext().isValid()).isTrue();
        assertThat(span.getParentSpanContext().getTraceId())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(attribute(span, "http.request.method")).isEqualTo("GET");
        assertThat(attribute(span, "url.path")).isEqualTo("/checkout");
    }
}
