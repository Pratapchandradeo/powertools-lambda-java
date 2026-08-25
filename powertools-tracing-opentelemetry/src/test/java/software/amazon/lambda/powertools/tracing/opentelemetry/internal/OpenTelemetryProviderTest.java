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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import software.amazon.lambda.powertools.tracing.opentelemetry.TraceContextPropagationMode;
import software.amazon.lambda.powertools.tracing.opentelemetry.TracingMode;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.OpenTelemetryProvider.ResolvedOpenTelemetry;

class OpenTelemetryProviderTest {

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void autoModeWithoutGlobalIsNoopAndDoesNotOwnSdk() {
        ResolvedOpenTelemetry resolved = OpenTelemetryProvider.resolve(TracingMode.AUTO);

        assertThat(resolved.openTelemetry()).isEqualTo(OpenTelemetry.noop());
        assertThat(resolved.ownsSdk()).isFalse();
    }

    @Test
    void autoModeReusesConfiguredGlobal() {
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .build();
        GlobalOpenTelemetry.set(sdk);

        ResolvedOpenTelemetry resolved = OpenTelemetryProvider.resolve(TracingMode.AUTO);

        assertThat(resolved.openTelemetry()).isNotEqualTo(OpenTelemetry.noop());
        assertThat(resolved.ownsSdk()).isFalse();
    }

    @Test
    void unknownModeEnvFallsBackToAuto() {
        assertThat(OpenTelemetryProvider.resolveMode()).isEqualTo(TracingMode.AUTO);
    }

    @Test
    @SetEnvironmentVariable(key = "POWERTOOLS_OTEL_TRACING_MODE", value = "manual")
    void readsManualModeFromEnvironment() {
        assertThat(OpenTelemetryProvider.resolveMode()).isEqualTo(TracingMode.MANUAL);
    }

    @Test
    @SetEnvironmentVariable(key = "POWERTOOLS_OTEL_TRACING_MODE", value = "not-a-mode")
    void invalidModeFallsBackToAuto() {
        assertThat(OpenTelemetryProvider.resolveMode()).isEqualTo(TracingMode.AUTO);
    }

    @Test
    @SetEnvironmentVariable(key = "POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE", value = "link")
    void readsLinkPropagationMode() {
        assertThat(OpenTelemetryProvider.resolvePropagationMode()).isEqualTo(TraceContextPropagationMode.LINK);
    }

    @Test
    void defaultPropagatorIsComposite() {
        assertThat(OpenTelemetryProvider.defaultPropagator().fields())
                .contains("traceparent", "X-Amzn-Trace-Id");
    }

    @Test
    void flushIsNoOpWhenSdkIsNotOwned() {
        OpenTelemetryProvider.flush(OpenTelemetry.noop(), false);
        OpenTelemetryProvider.flush(OpenTelemetry.noop(), true);
    }

    @Test
    @SetEnvironmentVariable(key = "OTEL_EXPORTER_OTLP_TRACES_PROTOCOL", value = "not-real")
    void rejectsUnknownOtlpProtocolWhenCreatingManualSdk() {
        assertThatThrownBy(() -> OpenTelemetryProvider.resolve(TracingMode.MANUAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported OTLP protocol");
    }
}
