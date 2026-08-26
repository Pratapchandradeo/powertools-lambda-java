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

package software.amazon.lambda.powertools.featureflags;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.featureflags.store.InMemoryStore;

class ModuloRangeFeatureFlagsTest {

    private static final String SCHEMA = "{"
            + "\"ten_percent_experiment\": {"
            + "  \"default\": false,"
            + "  \"rules\": {"
            + "    \"bucket\": {"
            + "      \"when_match\": true,"
            + "      \"conditions\": ["
            + "        { \"action\": \"MODULO_RANGE\", \"key\": \"user_id\","
            + "          \"value\": { \"BASE\": 100, \"START\": 0, \"END\": 9 } }"
            + "      ]"
            + "    }"
            + "  }"
            + "}"
            + "}";

    @Test
    void usersInBucketAreEnabled() {
        FeatureFlags flags = FeatureFlags.builder().withStore(InMemoryStore.fromJson(SCHEMA)).build();

        assertThat(flags.evaluate("ten_percent_experiment", Map.of("user_id", 0), false)).isTrue();
        assertThat(flags.evaluate("ten_percent_experiment", Map.of("user_id", 9), false)).isTrue();
        assertThat(flags.evaluate("ten_percent_experiment", Map.of("user_id", 109), false)).isTrue();
    }

    @Test
    void usersOutsideBucketStayDisabled() {
        FeatureFlags flags = FeatureFlags.builder().withStore(InMemoryStore.fromJson(SCHEMA)).build();

        assertThat(flags.evaluate("ten_percent_experiment", Map.of("user_id", 10), false)).isFalse();
        assertThat(flags.evaluate("ten_percent_experiment", Map.of("user_id", 99), false)).isFalse();
    }

    @Test
    void nonNumericContextDoesNotMatch() {
        FeatureFlags flags = FeatureFlags.builder().withStore(InMemoryStore.fromJson(SCHEMA)).build();

        assertThat(flags.evaluate("ten_percent_experiment", Map.of("user_id", "abc"), false)).isFalse();
    }
}
