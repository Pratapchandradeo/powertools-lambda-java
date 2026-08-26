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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.featureflags.store.InMemoryStore;

class NonBooleanFeatureFlagsTest {

    private static final String SCHEMA = "{"
            + "\"checkout_config\": {"
            + "  \"default\": { \"group\": \"read-only\" },"
            + "  \"boolean_type\": false,"
            + "  \"rules\": {"
            + "    \"admin\": {"
            + "      \"when_match\": { \"group\": \"admin\" },"
            + "      \"conditions\": ["
            + "        { \"action\": \"EQUALS\", \"key\": \"role\", \"value\": \"admin\" }"
            + "      ]"
            + "    }"
            + "  }"
            + "},"
            + "\"perk_list\": {"
            + "  \"default\": [],"
            + "  \"boolean_type\": false,"
            + "  \"rules\": {"
            + "    \"premium\": {"
            + "      \"when_match\": [\"remove_limits\", \"remove_ads\"],"
            + "      \"conditions\": ["
            + "        { \"action\": \"EQUALS\", \"key\": \"tier\", \"value\": \"premium\" }"
            + "      ]"
            + "    }"
            + "  }"
            + "}"
            + "}";

    @Test
    void returnsObjectWhenRuleMatches() {
        FeatureFlags flags = flags();

        Map<String, Object> value = flags.evaluate("checkout_config", Map.of("role", "admin"),
                Map.of("group", "none"));

        assertThat(value).containsEntry("group", "admin");
    }

    @Test
    void returnsDefaultObjectWhenNoMatch() {
        FeatureFlags flags = flags();

        Map<String, Object> value = flags.evaluate("checkout_config", Map.of("role", "user"),
                Map.of("group", "none"));

        assertThat(value).containsEntry("group", "read-only");
    }

    @Test
    void returnsListWhenRuleMatches() {
        FeatureFlags flags = flags();

        List<String> perks = flags.evaluate("perk_list", Map.of("tier", "premium"), List.of());

        assertThat(perks).containsExactly("remove_limits", "remove_ads");
    }

    @Test
    void getEnabledFeaturesUsesPythonTruthiness() {
        FeatureFlags flags = flags();

        // checkout_config default is a non-empty object (truthy) so it is enabled
        // even when no rule matches. perk_list default is [] (falsy).
        assertThat(flags.getEnabledFeatures(Map.of("tier", "standard", "role", "user")))
                .containsExactly("checkout_config");
        assertThat(flags.getEnabledFeatures(Map.of("tier", "premium", "role", "user")))
                .containsExactly("checkout_config", "perk_list");
    }

    private static FeatureFlags flags() {
        return FeatureFlags.builder().withStore(InMemoryStore.fromJson(SCHEMA)).build();
    }
}
