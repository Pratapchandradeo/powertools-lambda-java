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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.OpenTelemetryProvider;

class MapTraceContextExtractorTest {
    private final MapTraceContextExtractor extractor = new MapTraceContextExtractor();

    @Test
    void supportsMapsWithHeadersOnly() {
        assertThat(extractor.supports(Map.of("headers", Map.of()))).isTrue();
        assertThat(extractor.supports(Map.of("body", "{}"))).isFalse();
        assertThat(extractor.supports("string")).isFalse();
    }

    @Test
    void extractsFromGenericHeaderMap() {
        Map<String, Object> event = Map.of(
                "headers", Map.of(
                        "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
                )
        );

        ExtractedTraceContext extracted = extractor.extract(
                event, Context.root(), OpenTelemetryProvider.defaultPropagator());

        assertThat(Span.fromContext(extracted.context()).getSpanContext().getTraceId())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }
}
