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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;
import software.amazon.lambda.powertools.featureflags.exception.SchemaValidationException;
import software.amazon.lambda.powertools.featureflags.store.InMemoryStore;
import software.amazon.lambda.powertools.featureflags.store.StoreProvider;

class FeatureFlagsEvaluateTest {

    @Test
    void staticFlagReturnsDefault() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"ten_percent_off_campaign\": { \"default\": true }"
                + "}");

        assertThat(flags.evaluate("ten_percent_off_campaign", false)).isTrue();
    }

    @Test
    void staticFlagFalse() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"ten_percent_off_campaign\": { \"default\": false }"
                + "}");

        assertThat(flags.evaluate("ten_percent_off_campaign", true)).isFalse();
    }

    @Test
    void missingFeatureReturnsCallerDefault() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"existing\": { \"default\": true }"
                + "}");

        assertThat(flags.evaluate("missing", false)).isFalse();
        assertThat(flags.evaluate("missing", true)).isTrue();
    }

    @Test
    void storeFailureReturnsCallerDefault() {
        FeatureFlags flags = FeatureFlags.builder().withStore(new FailingStore()).build();

        assertThat(flags.evaluate("any", Map.of("tier", "premium"), false)).isFalse();
        assertThat(flags.evaluate("any", "fallback")).isEqualTo("fallback");
    }

    @Test
    void equalsRuleMatchesContext() {
        FeatureFlags flags = flagsFromJson(premiumFeaturesSchema());

        assertThat(flags.evaluate("premium_features", Map.of("tier", "premium"), false)).isTrue();
        assertThat(flags.evaluate("premium_features", Map.of("tier", "standard"), false)).isFalse();
        assertThat(flags.evaluate("premium_features", Collections.emptyMap(), false)).isFalse();
    }

    @Test
    void nullContextTreatedAsEmpty() {
        FeatureFlags flags = flagsFromJson(premiumFeaturesSchema());

        assertThat(flags.evaluate("premium_features", null, false)).isFalse();
    }

    @Test
    void firstMatchingRuleWins() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"tiered\": {"
                + "  \"default\": \"standard\","
                + "  \"boolean_type\": false,"
                + "  \"rules\": {"
                + "    \"enterprise\": {"
                + "      \"when_match\": \"enterprise\","
                + "      \"conditions\": [ { \"action\": \"EQUALS\", \"key\": \"tier\", \"value\": \"enterprise\" } ]"
                + "    },"
                + "    \"premium\": {"
                + "      \"when_match\": \"premium\","
                + "      \"conditions\": [ { \"action\": \"EQUALS\", \"key\": \"tier\", \"value\": \"premium\" } ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("tiered", Map.of("tier", "enterprise"), "none")).isEqualTo("enterprise");
        assertThat(flags.evaluate("tiered", Map.of("tier", "premium"), "none")).isEqualTo("premium");
        assertThat(flags.evaluate("tiered", Map.of("tier", "basic"), "none")).isEqualTo("standard");
    }

    @Test
    void conditionsAreAnded() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"nl_premium\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"both\": {"
                + "      \"when_match\": true,"
                + "      \"conditions\": ["
                + "        { \"action\": \"EQUALS\", \"key\": \"tier\", \"value\": \"premium\" },"
                + "        { \"action\": \"EQUALS\", \"key\": \"country\", \"value\": \"NL\" }"
                + "      ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("nl_premium", Map.of("tier", "premium", "country", "NL"), false)).isTrue();
        assertThat(flags.evaluate("nl_premium", Map.of("tier", "premium", "country", "US"), false)).isFalse();
        assertThat(flags.evaluate("nl_premium", Map.of("tier", "standard", "country", "NL"), false)).isFalse();
    }

    @Test
    void emptyConditionsNeverMatch() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"my_feature\": {"
                + "  \"default\": true,"
                + "  \"rules\": {"
                + "    \"empty\": { \"when_match\": false, \"conditions\": [] }"
                + "  }"
                + "}"
                + "}");

        // Python: empty conditions → rule does not match → feature default
        assertThat(flags.evaluate("my_feature", Map.of("x", 1), false)).isTrue();
    }

    @Test
    void keyInValueMatchesGeoList() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"geo_customer_campaign\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"geo\": {"
                + "      \"when_match\": true,"
                + "      \"conditions\": ["
                + "        { \"action\": \"KEY_IN_VALUE\", \"key\": \"CloudFront-Viewer-Country\","
                + "          \"value\": [\"NL\", \"IE\", \"UK\", \"PL\", \"PT\"] }"
                + "      ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("geo_customer_campaign",
                Map.of("CloudFront-Viewer-Country", "NL"), false)).isTrue();
        assertThat(flags.evaluate("geo_customer_campaign",
                Map.of("CloudFront-Viewer-Country", "US"), false)).isFalse();
    }

    @Test
    void invalidSchemaPropagates() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"broken\": { \"rules\": {} }"
                + "}");

        assertThatThrownBy(() -> flags.evaluate("broken", false))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("default");
    }

    @Test
    void comparisonFailureDoesNotThrow() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"starts\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"prefix\": {"
                + "      \"when_match\": true,"
                + "      \"conditions\": ["
                + "        { \"action\": \"STARTSWITH\", \"key\": \"name\", \"value\": \"pre\" }"
                + "      ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("starts", Collections.emptyMap(), false)).isFalse();
        assertThat(flags.evaluate("starts", Map.of("name", 10), false)).isFalse();
    }

    @Test
    void getConfigurationReturnsValidatedDocument() {
        FeatureFlags flags = flagsFromJson(premiumFeaturesSchema());

        assertThat(flags.getConfiguration()).containsKey("premium_features");
    }

    @Test
    void builderRequiresStore() {
        assertThatThrownBy(() -> FeatureFlags.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("store");
    }

    @Test
    void nullClockFallsBackToUtc() {
        FeatureFlags flags = FeatureFlags.builder()
                .withStore(InMemoryStore.fromJson("{\"static\": { \"default\": true }}"))
                .withClock(null)
                .build();

        assertThat(flags.evaluate("static", false)).isTrue();
    }

    @Test
    void booleanCoercionOfZeroDefault() {
        FeatureFlags flags = flagsFromJson("{\"off\": { \"default\": 0 }}");

        assertThat(flags.evaluate("off", true)).isFalse();
    }

    @Test
    void booleanTypeCoercesWhenMatch() {
        FeatureFlags flags = flagsFromJson("{"
                + "\"coerced\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"hit\": {"
                + "      \"when_match\": 1,"
                + "      \"conditions\": [ { \"action\": \"EQUALS\", \"key\": \"ok\", \"value\": true } ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("coerced", Map.of("ok", true), false)).isTrue();
    }

    @Test
    void documentOrderPreservedForFirstMatch() {
        Map<String, Object> schema = new LinkedHashMap<>();
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("default", "none");
        feature.put("boolean_type", false);
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("first", rule("when_match", "first", equalsCondition("id", "x")));
        rules.put("second", rule("when_match", "second", equalsCondition("id", "x")));
        feature.put("rules", rules);
        schema.put("ordered", feature);

        FeatureFlags flags = FeatureFlags.builder().withStore(InMemoryStore.fromMap(schema)).build();
        assertThat(flags.evaluate("ordered", Map.of("id", "x"), "none")).isEqualTo("first");
    }

    private static FeatureFlags flagsFromJson(String json) {
        return FeatureFlags.builder().withStore(InMemoryStore.fromJson(json)).build();
    }

    private static String premiumFeaturesSchema() {
        return "{"
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
                + "}"
                + "}";
    }

    private static Map<String, Object> rule(String matchKey, Object matchValue, Map<String, Object> condition) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put(matchKey, matchValue);
        rule.put("conditions", List.of(condition));
        return rule;
    }

    private static Map<String, Object> equalsCondition(String key, Object value) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("action", "EQUALS");
        condition.put("key", key);
        condition.put("value", value);
        return condition;
    }

    private static final class FailingStore implements StoreProvider {
        @Override
        public Map<String, Object> getConfiguration() {
            throw new ConfigurationStoreException("unavailable");
        }
    }
}
