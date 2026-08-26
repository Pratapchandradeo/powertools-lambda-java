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

package software.amazon.lambda.powertools.featureflags.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;

class InMemoryStoreTest {

    @Test
    void parsesJsonObjectAndPreservesOrder() {
        InMemoryStore store = InMemoryStore.fromJson("{"
                + "\"first\": { \"default\": true },"
                + "\"second\": { \"default\": false }"
                + "}");

        assertThat(store.getConfiguration().keySet()).containsExactly("first", "second");
        assertThat(store.getConfiguration()).containsKey("first");
    }

    @Test
    void fromMapCopiesInput() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("a", Map.of("default", true));
        InMemoryStore store = InMemoryStore.fromMap(source);
        source.put("b", Map.of("default", false));

        assertThat(store.getConfiguration()).containsOnlyKeys("a");
    }

    @Test
    void rejectsNullMap() {
        assertThatThrownBy(() -> InMemoryStore.fromMap(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankJson() {
        assertThatThrownBy(() -> InMemoryStore.fromJson("  "))
                .isInstanceOf(ConfigurationStoreException.class);
    }

    @Test
    void rejectsJsonArray() {
        assertThatThrownBy(() -> InMemoryStore.fromJson("[1, 2]"))
                .isInstanceOf(ConfigurationStoreException.class)
                .hasMessageContaining("object");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> InMemoryStore.fromJson("{not-json"))
                .isInstanceOf(ConfigurationStoreException.class);
    }
}
