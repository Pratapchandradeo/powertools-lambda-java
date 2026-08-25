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

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Case-insensitive getter for HTTP-style header maps (API Gateway, generic events).
 */
public final class MapTextMapGetter implements TextMapGetter<Map<String, String>> {

    public static final MapTextMapGetter INSTANCE = new MapTextMapGetter();

    private MapTextMapGetter() {
    }

    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
        return carrier == null ? Collections.emptyList() : carrier.keySet();
    }

    @Override
    public String get(Map<String, String> carrier, String key) {
        if (carrier == null || key == null) {
            return null;
        }
        String direct = carrier.get(key);
        if (direct != null) {
            return direct;
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : carrier.entrySet()) {
            if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(lowerKey)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
