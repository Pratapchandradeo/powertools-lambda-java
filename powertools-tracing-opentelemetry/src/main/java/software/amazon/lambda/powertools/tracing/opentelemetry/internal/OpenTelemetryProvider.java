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

import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.INSTRUMENTATION_NAME;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.OTEL_EXPORTER_OTLP_TRACES_PROTOCOL;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.POWERTOOLS_OTEL_TRACING_MODE;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.common.internal.SystemWrapper;
import software.amazon.lambda.powertools.tracing.opentelemetry.TraceContextPropagationMode;
import software.amazon.lambda.powertools.tracing.opentelemetry.TracingMode;

/**
 * Resolves the OpenTelemetry instance used by Powertools.
 *
 * <p>{@link TracingMode#AUTO} never creates an SDK. {@link TracingMode#MANUAL} creates a
 * Lambda-tuned SDK only when no global instance is registered, and flushes it on demand.</p>
 */
public final class OpenTelemetryProvider {
    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryProvider.class);

    private static final long EXPORT_TIMEOUT_MILLIS = 3_000;
    private static final int MAX_EXPORT_BATCH_SIZE = 10;
    private static final int MAX_QUEUE_SIZE = 100;
    private static final long SCHEDULE_DELAY_MILLIS = 1_000;

    private OpenTelemetryProvider() {
    }

    public static TracingMode resolveMode() {
        String value = SystemWrapper.getenv(POWERTOOLS_OTEL_TRACING_MODE);
        if (value == null || value.isBlank()) {
            return TracingMode.AUTO;
        }
        try {
            return TracingMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            LOG.warn("Unknown {}: {}. Falling back to AUTO.", POWERTOOLS_OTEL_TRACING_MODE, value);
            return TracingMode.AUTO;
        }
    }

    public static TraceContextPropagationMode resolvePropagationMode() {
        String value = SystemWrapper.getenv(POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE);
        if (value == null || value.isBlank()) {
            return TraceContextPropagationMode.PARENT;
        }
        try {
            return TraceContextPropagationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            LOG.warn("Unknown {}: {}. Falling back to PARENT.",
                    POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE, value);
            return TraceContextPropagationMode.PARENT;
        }
    }

    public static ResolvedOpenTelemetry resolve(TracingMode mode) {
        OpenTelemetry global = GlobalOpenTelemetry.get();
        if (!isNoop(global)) {
            return new ResolvedOpenTelemetry(global, defaultPropagator(), false);
        }
        if (mode == TracingMode.AUTO) {
            return new ResolvedOpenTelemetry(OpenTelemetry.noop(), defaultPropagator(), false);
        }
        OpenTelemetrySdk sdk = createDefaultSdk();
        registerGlobal(sdk);
        return new ResolvedOpenTelemetry(sdk, defaultPropagator(), true);
    }

    public static Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    public static TextMapPropagator defaultPropagator() {
        return TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                AwsXrayPropagator.getInstance()
        );
    }

    public static void flush(OpenTelemetry openTelemetry, boolean ownsSdk) {
        if (!ownsSdk || !(openTelemetry instanceof OpenTelemetrySdk)) {
            return;
        }
        OpenTelemetrySdk sdk = (OpenTelemetrySdk) openTelemetry;
        sdk.getSdkTracerProvider().forceFlush().join(EXPORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static boolean isNoop(OpenTelemetry openTelemetry) {
        return openTelemetry == OpenTelemetry.noop();
    }

    private static void registerGlobal(OpenTelemetrySdk sdk) {
        try {
            GlobalOpenTelemetry.set(sdk);
        } catch (IllegalStateException exception) {
            LOG.debug("Global OpenTelemetry already registered; using existing instance", exception);
        }
    }

    private static OpenTelemetrySdk createDefaultSdk() {
        SpanExporter exporter = createExporter(SystemWrapper.getenv(OTEL_EXPORTER_OTLP_TRACES_PROTOCOL));
        BatchSpanProcessor processor = BatchSpanProcessor.builder(exporter)
                .setMaxExportBatchSize(MAX_EXPORT_BATCH_SIZE)
                .setMaxQueueSize(MAX_QUEUE_SIZE)
                .setScheduleDelay(SCHEDULE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .setExporterTimeout(EXPORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(LambdaResource.create())
                .addSpanProcessor(processor)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(defaultPropagator()))
                .build();
    }

    private static SpanExporter createExporter(String protocol) {
        if (protocol == null || protocol.isBlank() || "grpc".equalsIgnoreCase(protocol.trim())) {
            return OtlpGrpcSpanExporter.builder()
                    .setTimeout(EXPORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .build();
        }
        if ("http/protobuf".equalsIgnoreCase(protocol.trim())) {
            return OtlpHttpSpanExporter.builder()
                    .setTimeout(EXPORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                    .build();
        }
        throw new IllegalArgumentException("Unsupported OTLP protocol: " + protocol);
    }

    public static final class ResolvedOpenTelemetry {
        private final OpenTelemetry openTelemetry;
        private final TextMapPropagator propagator;
        private final boolean ownsSdk;

        ResolvedOpenTelemetry(OpenTelemetry openTelemetry, TextMapPropagator propagator, boolean ownsSdk) {
            this.openTelemetry = openTelemetry;
            this.propagator = propagator;
            this.ownsSdk = ownsSdk;
        }

        public OpenTelemetry openTelemetry() {
            return openTelemetry;
        }

        public TextMapPropagator propagator() {
            return propagator;
        }

        public boolean ownsSdk() {
            return ownsSdk;
        }
    }
}
