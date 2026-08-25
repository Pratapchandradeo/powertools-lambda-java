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

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.MapTextMapGetter;

/**
 * Fallback extractor for generic {@code Map} events that carry a {@code headers} map.
 */
public final class MapTraceContextExtractor implements TraceContextExtractor {

    @Override
    public boolean supports(Object event) {
        return event instanceof Map && ((Map<?, ?>) event).containsKey("headers");
    }

    @Override
    public ExtractedTraceContext extract(Object event, Context parentContext, TextMapPropagator propagator) {
        Map<String, String> headers = stringHeaders((Map<?, ?>) event);
        Context extracted = propagator.extract(parentContext, headers, MapTextMapGetter.INSTANCE);
        return new ExtractedTraceContext(extracted, Collections.emptyList(), SpanKind.SERVER);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringHeaders(Map<?, ?> event) {
        Object headers = event.get("headers");
        if (!(headers instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<String, String> carrier = new HashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<Object, Object>) headers).entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                carrier.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return carrier;
    }
}
