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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import software.amazon.lambda.powertools.featureflags.exception.SchemaValidationException;

/**
 * Validates a feature-flag configuration document against the Powertools schema.
 */
public final class SchemaValidator {

    private SchemaValidator() {
    }

    /**
     * @param schema the document returned by a store (feature name → feature object)
     * @throws SchemaValidationException if the document is not a valid feature-flag schema
     */
    public static void validate(Map<String, Object> schema) {
        if (schema == null) {
            throw new SchemaValidationException("Feature flag schema must not be null");
        }
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            validateFeature(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateFeature(String featureName, Object featureObj) {
        if (!(featureObj instanceof Map)) {
            throw new SchemaValidationException(
                    "Feature '" + featureName + "' must be an object");
        }
        Map<String, Object> feature = (Map<String, Object>) featureObj;
        if (!feature.containsKey(FeatureSchema.FEATURE_DEFAULT_VAL_KEY)) {
            throw new SchemaValidationException(
                    "Feature '" + featureName + "' must contain a '" + FeatureSchema.FEATURE_DEFAULT_VAL_KEY + "' key");
        }
        if (feature.containsKey(FeatureSchema.FEATURE_DEFAULT_VAL_TYPE_KEY)
                && !(feature.get(FeatureSchema.FEATURE_DEFAULT_VAL_TYPE_KEY) instanceof Boolean)) {
            throw new SchemaValidationException(
                    "Feature '" + featureName + "' '" + FeatureSchema.FEATURE_DEFAULT_VAL_TYPE_KEY
                            + "' must be a boolean");
        }
        if (!feature.containsKey(FeatureSchema.RULES_KEY)) {
            return;
        }
        Object rulesObj = feature.get(FeatureSchema.RULES_KEY);
        if (!(rulesObj instanceof Map)) {
            throw new SchemaValidationException(
                    "Feature '" + featureName + "' '" + FeatureSchema.RULES_KEY + "' must be an object");
        }
        Map<String, Object> rules = (Map<String, Object>) rulesObj;
        for (Map.Entry<String, Object> ruleEntry : rules.entrySet()) {
            validateRule(featureName, ruleEntry.getKey(), ruleEntry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateRule(String featureName, String ruleName, Object ruleObj) {
        if (!(ruleObj instanceof Map)) {
            throw new SchemaValidationException(
                    "Rule '" + ruleName + "' on feature '" + featureName + "' must be an object");
        }
        Map<String, Object> rule = (Map<String, Object>) ruleObj;
        if (!rule.containsKey(FeatureSchema.RULE_MATCH_VALUE)) {
            throw new SchemaValidationException(
                    "Rule '" + ruleName + "' on feature '" + featureName + "' must contain '"
                            + FeatureSchema.RULE_MATCH_VALUE + "'");
        }
        if (!rule.containsKey(FeatureSchema.CONDITIONS_KEY)) {
            throw new SchemaValidationException(
                    "Rule '" + ruleName + "' on feature '" + featureName + "' must contain '"
                            + FeatureSchema.CONDITIONS_KEY + "'");
        }
        Object conditionsObj = rule.get(FeatureSchema.CONDITIONS_KEY);
        if (!(conditionsObj instanceof List)) {
            throw new SchemaValidationException(
                    "Rule '" + ruleName + "' on feature '" + featureName + "' '"
                            + FeatureSchema.CONDITIONS_KEY + "' must be a list");
        }
        List<Object> conditions = (List<Object>) conditionsObj;
        for (Object condition : conditions) {
            validateCondition(featureName, ruleName, condition);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateCondition(String featureName, String ruleName, Object conditionObj) {
        if (!(conditionObj instanceof Map)) {
            throw new SchemaValidationException(
                    "Condition on rule '" + ruleName + "' of feature '" + featureName + "' must be an object");
        }
        Map<String, Object> condition = (Map<String, Object>) conditionObj;
        Object actionObj = condition.get(FeatureSchema.CONDITION_ACTION);
        if (!(actionObj instanceof String) || ((String) actionObj).isEmpty()) {
            throw new SchemaValidationException(
                    "Condition on rule '" + ruleName + "' of feature '" + featureName
                            + "' must contain a non-empty '" + FeatureSchema.CONDITION_ACTION + "'");
        }
        String action = (String) actionObj;
        if (RuleAction.fromValue(action) == null) {
            throw new SchemaValidationException(
                    "'action' value must be either " + Arrays.toString(RuleAction.values())
                            + ", rule_name=" + ruleName + ", action=" + action);
        }
        Object key = condition.get(FeatureSchema.CONDITION_KEY);
        if (!(key instanceof String) || ((String) key).isEmpty()) {
            throw new SchemaValidationException(
                    "Condition on rule '" + ruleName + "' of feature '" + featureName
                            + "' must contain a non-empty '" + FeatureSchema.CONDITION_KEY + "'");
        }
    }
}
