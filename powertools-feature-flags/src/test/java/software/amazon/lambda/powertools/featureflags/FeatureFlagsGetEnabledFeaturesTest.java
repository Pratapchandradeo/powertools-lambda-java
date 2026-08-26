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
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;
import software.amazon.lambda.powertools.featureflags.store.InMemoryStore;
import software.amazon.lambda.powertools.featureflags.store.StoreProvider;

class FeatureFlagsGetEnabledFeaturesTest {

    private static final String SCHEMA = "{"
            + "\"premium_features\": {"
            + "  \"default\": false,"
            + "  \"rules\": {"
            + "    \"customer tier equals premium\": {"
            + "      \"when_match\": true,"
            + "      \"conditions\": ["
            + "        { \"action\": \"EQUALS\", \"key\": \"tier\", \"value\": \"premium\" }"
            + "      ]"
            + "    }"
            + "  }"
            + "},"
            + "\"ten_percent_off_campaign\": { \"default\": true },"
            + "\"geo_customer_campaign\": {"
            + "  \"default\": false,"
            + "  \"rules\": {"
            + "    \"customer in temporary discount geo\": {"
            + "      \"when_match\": true,"
            + "      \"conditions\": ["
            + "        { \"action\": \"KEY_IN_VALUE\", \"key\": \"CloudFront-Viewer-Country\","
            + "          \"value\": [\"NL\", \"IE\", \"UK\", \"PL\", \"PT\"] }"
            + "      ]"
            + "    }"
            + "  }"
            + "},"
            + "\"disabled_static\": { \"default\": false }"
            + "}";

    @Test
    void returnsStaticAndMatchingDynamicFlags() {
        FeatureFlags flags = FeatureFlags.builder().withStore(InMemoryStore.fromJson(SCHEMA)).build();

        List<String> enabled = flags.getEnabledFeatures(Map.of(
                "tier", "premium",
                "CloudFront-Viewer-Country", "NL"));

        assertThat(enabled).containsExactly(
                "premium_features",
                "ten_percent_off_campaign",
                "geo_customer_campaign");
    }

    @Test
    void staticOnlyWhenContextDoesNotMatch() {
        FeatureFlags flags = FeatureFlags.builder().withStore(InMemoryStore.fromJson(SCHEMA)).build();

        assertThat(flags.getEnabledFeatures(Map.of("tier", "standard")))
                .containsExactly("ten_percent_off_campaign");
    }

    @Test
    void emptyContextUsesStaticDefaults() {
        FeatureFlags flags = FeatureFlags.builder().withStore(InMemoryStore.fromJson(SCHEMA)).build();

        assertThat(flags.getEnabledFeatures()).containsExactly("ten_percent_off_campaign");
    }

    @Test
    void storeFailureReturnsEmptyList() {
        FeatureFlags flags = FeatureFlags.builder()
                .withStore(new StoreProvider() {
                    @Override
                    public Map<String, Object> getConfiguration() {
                        throw new ConfigurationStoreException("down");
                    }
                })
                .build();

        assertThat(flags.getEnabledFeatures(Map.of("tier", "premium"))).isEmpty();
    }

    @Test
    void defaultTrueWithUnmatchedRulesStillEnabled() {
        // Python returns feat_default when no rule matches; truthy default keeps the feature enabled.
        FeatureFlags flags = FeatureFlags.builder()
                .withStore(InMemoryStore.fromJson("{"
                        + "\"always_on_unless_blocked\": {"
                        + "  \"default\": true,"
                        + "  \"rules\": {"
                        + "    \"blocklist\": {"
                        + "      \"when_match\": false,"
                        + "      \"conditions\": ["
                        + "        { \"action\": \"EQUALS\", \"key\": \"user\", \"value\": \"blocked\" }"
                        + "      ]"
                        + "    }"
                        + "  }"
                        + "}"
                        + "}"))
                .build();

        assertThat(flags.getEnabledFeatures(Map.of("user", "ok")))
                .containsExactly("always_on_unless_blocked");
        assertThat(flags.getEnabledFeatures(Map.of("user", "blocked"))).isEmpty();
    }
}
