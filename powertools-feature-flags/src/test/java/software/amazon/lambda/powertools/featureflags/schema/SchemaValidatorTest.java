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

package software.amazon.lambda.powertools.featureflags.schema;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.featureflags.exception.SchemaValidationException;
import software.amazon.lambda.powertools.featureflags.store.InMemoryStore;

class SchemaValidatorTest {

    @Test
    void acceptsStaticAndDynamicFeatures() {
        Map<String, Object> schema = InMemoryStore.fromJson("{"
                + "\"static\": { \"default\": false },"
                + "\"dynamic\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"r1\": {"
                + "      \"when_match\": true,"
                + "      \"conditions\": [ { \"action\": \"EQUALS\", \"key\": \"tier\", \"value\": \"premium\" } ]"
                + "    }"
                + "  }"
                + "}"
                + "}").getConfiguration();

        assertThatCode(() -> SchemaValidator.validate(schema)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullSchema() {
        assertThatThrownBy(() -> SchemaValidator.validate(null))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void rejectsFeatureWithoutDefault() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("x", Map.of("rules", Map.of()));

        assertThatThrownBy(() -> SchemaValidator.validate(schema))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("default");
    }

    @Test
    void rejectsUnknownAction() {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("action", "REGEX");
        condition.put("key", "name");
        condition.put("value", ".*");
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("when_match", true);
        rule.put("conditions", List.of(condition));
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("default", false);
        feature.put("rules", Map.of("r", rule));

        assertThatThrownBy(() -> SchemaValidator.validate(Map.of("f", feature)))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("REGEX");
    }

    @Test
    void rejectsNonObjectFeature() {
        assertThatThrownBy(() -> SchemaValidator.validate(Map.of("f", "not-an-object")))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("must be an object");
    }

    @Test
    void rejectsMissingConditions() {
        Map<String, Object> rule = Map.of("when_match", true);
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("default", false);
        feature.put("rules", Map.of("r", rule));

        assertThatThrownBy(() -> SchemaValidator.validate(Map.of("f", feature)))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("conditions");
    }

    @Test
    void rejectsRulesThatAreNotAnObject() {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("default", false);
        feature.put("rules", List.of("not-a-map"));

        assertThatThrownBy(() -> SchemaValidator.validate(Map.of("f", feature)))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("rules");
    }

    @Test
    void rejectsMissingWhenMatch() {
        Map<String, Object> rule = Map.of("conditions", List.of());
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("default", false);
        feature.put("rules", Map.of("r", rule));

        assertThatThrownBy(() -> SchemaValidator.validate(Map.of("f", feature)))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("when_match");
    }

    @Test
    void rejectsEmptyConditionKey() {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("action", "EQUALS");
        condition.put("key", "");
        condition.put("value", "x");
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("when_match", true);
        rule.put("conditions", List.of(condition));
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("default", false);
        feature.put("rules", Map.of("r", rule));

        assertThatThrownBy(() -> SchemaValidator.validate(Map.of("f", feature)))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("key");
    }

    @Test
    void rejectsNonBooleanBooleanType() {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("default", false);
        feature.put("boolean_type", "yes");

        assertThatThrownBy(() -> SchemaValidator.validate(Map.of("f", feature)))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("boolean_type");
    }
}
