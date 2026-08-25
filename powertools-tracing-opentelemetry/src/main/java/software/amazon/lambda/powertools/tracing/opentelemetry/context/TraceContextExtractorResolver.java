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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.List;

public final class TraceContextExtractorResolver {
    private final List<TraceContextExtractor> extractors;

    public TraceContextExtractorResolver(List<TraceContextExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    public static TraceContextExtractorResolver create() {
        return new TraceContextExtractorResolver(List.of(
                new ApiGatewayTraceContextExtractor(),
                new SqsTraceContextExtractor(),
                new MapTraceContextExtractor()
        ));
    }

    public ExtractedTraceContext extract(Object event, Context parentContext, TextMapPropagator propagator) {
        if (event == null) {
            return new ExtractedTraceContext(parentContext, List.of(), SpanKind.SERVER);
        }
        for (TraceContextExtractor extractor : extractors) {
            if (extractor.supports(event)) {
                return extractor.extract(event, parentContext, propagator);
            }
        }
        return new ExtractedTraceContext(parentContext, List.of(), SpanKind.SERVER);
    }

    public void enrichSpan(Object event, Span span) {
        if (event == null) {
            return;
        }
        for (TraceContextExtractor extractor : extractors) {
            if (extractor.supports(event)) {
                extractor.enrichSpan(event, span);
                return;
            }
        }
    }
}
