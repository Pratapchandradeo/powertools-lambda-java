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

package software.amazon.lambda.powertools.tracing.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static software.amazon.lambda.powertools.tracing.opentelemetry.support.InMemoryTracing.attribute;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.MapTextMapGetter;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.SpanScope;
import software.amazon.lambda.powertools.tracing.opentelemetry.support.InMemoryTracing;

class TracerOpenTelemetryTest {
    private InMemoryTracing tracing;

    @AfterEach
    void tearDown() {
        if (tracing != null) {
            tracing.close();
        }
        TracerOpenTelemetry.reset();
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void withSpanRecordsAttributesAndEndsSpan() {
        tracing = InMemoryTracing.install();

        String result = TracerOpenTelemetry.withSpan("charge", span -> {
            span.setAttribute("order.id", "123");
            return "paid";
        });

        assertThat(result).isEqualTo("paid");
        SpanData span = tracing.finishedSpan("charge");
        assertThat(span.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(attribute(span, "order.id")).isEqualTo("123");
    }

    @Test
    void withSpanRecordsExceptionAndRethrows() {
        tracing = InMemoryTracing.install();

        assertThatThrownBy(() -> TracerOpenTelemetry.runWithSpan("charge", span -> {
            throw new IllegalArgumentException("bad order");
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad order");

        SpanData span = tracing.finishedSpan("charge");
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).isNotEmpty();
    }

    @Test
    void addSpanMakesSpanCurrentUntilClosed() {
        tracing = InMemoryTracing.install();

        try (SpanScope scope = TracerOpenTelemetry.addSpan("nested")) {
            assertThat(Span.current()).isEqualTo(scope.span());
            scope.span().setAttribute("inside", true);
        }

        assertThat(tracing.finishedSpan("nested").getName()).isEqualTo("nested");
    }

    @Test
    void extractAndInjectRoundTripW3cContext() {
        tracing = InMemoryTracing.install();
        Map<String, String> incoming = Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        );

        Context extracted = tracing.tracer().extract(incoming, MapTextMapGetter.INSTANCE);
        assertThat(Span.fromContext(extracted).getSpanContext().isValid()).isTrue();
        assertThat(Span.fromContext(extracted).getSpanContext().getTraceId())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");

        try (SpanScope scope = tracing.tracer().startSpan("child", SpanKind.INTERNAL,
                io.opentelemetry.api.common.Attributes.empty(), extracted, java.util.List.of())) {
            Map<String, String> outgoing = new HashMap<>();
            tracing.tracer().inject(outgoing, Map::put);
            assertThat(outgoing.get("traceparent")).contains("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(scope.span().getSpanContext().getTraceId())
                    .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        }
    }

    @Test
    void autoModeWithoutGlobalSdkIsNoop() {
        GlobalOpenTelemetry.resetForTest();
        TracerOpenTelemetry.reset();

        TracerOpenTelemetry tracer = TracerOpenTelemetry.create(TracingMode.AUTO);

        assertThat(tracer.openTelemetry()).isEqualTo(OpenTelemetry.noop());
        assertThat(tracer.ownsSdk()).isFalse();
        tracer.runSpan("ignored", span -> span.setAttribute("x", "y"));
    }

    @Test
    void createWithSuppliedSdkDoesNotOwnFlushUnlessRequested() {
        tracing = InMemoryTracing.install();
        TracerOpenTelemetry supplied = TracerOpenTelemetry.builder()
                .openTelemetry(tracing.tracer().openTelemetry())
                .ownsSdk(false)
                .build();

        assertThat(supplied.ownsSdk()).isFalse();
        supplied.flushOwned();
    }

    @Test
    void currentSpanMatchesActiveScope() {
        tracing = InMemoryTracing.install();
        try (SpanScope scope = tracing.tracer().startSpan("active")) {
            assertThat(TracerOpenTelemetry.currentSpan()).isEqualTo(scope.span());
        }
    }

    @Test
    void initReturnsConfiguredDefault() {
        tracing = InMemoryTracing.install();
        TracerOpenTelemetry.init();
        assertThat(TracerOpenTelemetry.getDefault()).isSameAs(tracing.tracer());
    }
}
