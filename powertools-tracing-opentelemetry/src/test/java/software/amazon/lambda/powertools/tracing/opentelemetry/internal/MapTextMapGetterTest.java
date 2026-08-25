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

import java.util.Map;
import org.junit.jupiter.api.Test;

class MapTextMapGetterTest {

    @Test
    void looksUpHeadersCaseInsensitively() {
        Map<String, String> headers = Map.of("TraceParent", "value");

        assertThat(MapTextMapGetter.INSTANCE.get(headers, "traceparent")).isEqualTo("value");
        assertThat(MapTextMapGetter.INSTANCE.get(headers, "TRACEPARENT")).isEqualTo("value");
    }

    @Test
    void returnsNullForMissingCarrierOrKey() {
        assertThat(MapTextMapGetter.INSTANCE.get(null, "traceparent")).isNull();
        assertThat(MapTextMapGetter.INSTANCE.get(Map.of(), "traceparent")).isNull();
        assertThat(MapTextMapGetter.INSTANCE.keys(null)).isEmpty();
    }
}
