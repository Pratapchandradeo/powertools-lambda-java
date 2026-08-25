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

package software.amazon.lambda.powertools.tracing.opentelemetry.support;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import software.amazon.lambda.powertools.tracing.opentelemetry.TraceContextPropagationMode;
import software.amazon.lambda.powertools.tracing.opentelemetry.TracerOpenTelemetry;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.OpenTelemetryProvider;

public final class InMemoryTracing {
    private final InMemorySpanExporter exporter;
    private final OpenTelemetrySdk sdk;
    private final TracerOpenTelemetry tracer;

    private InMemoryTracing(TraceContextPropagationMode mode) {
        this.exporter = InMemorySpanExporter.create();
        this.sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(OpenTelemetryProvider.defaultPropagator()))
                .build();
        this.tracer = TracerOpenTelemetry.builder()
                .openTelemetry(sdk)
                .ownsSdk(true)
                .propagationMode(mode)
                .build();
    }

    public static InMemoryTracing install() {
        return install(TraceContextPropagationMode.PARENT);
    }

    public static InMemoryTracing install(TraceContextPropagationMode mode) {
        InMemoryTracing tracing = new InMemoryTracing(mode);
        TracerOpenTelemetry.configure(tracing.tracer);
        return tracing;
    }

    public TracerOpenTelemetry tracer() {
        return tracer;
    }

    public InMemorySpanExporter exporter() {
        return exporter;
    }

    public SpanData finishedSpan(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finished span named " + name
                        + " in " + exporter.getFinishedSpanItems()));
    }

    public static String attribute(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.stringKey(key));
    }

    public void close() {
        exporter.reset();
        TracerOpenTelemetry.reset();
        sdk.close();
    }
}
