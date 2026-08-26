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

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.common.internal.ClassPreLoader;
import software.amazon.lambda.powertools.featureflags.comparators.ConditionMatcher;
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;
import software.amazon.lambda.powertools.featureflags.schema.FeatureSchema;
import software.amazon.lambda.powertools.featureflags.schema.RuleAction;
import software.amazon.lambda.powertools.featureflags.schema.SchemaValidator;
import software.amazon.lambda.powertools.featureflags.store.InMemoryStore;
import software.amazon.lambda.powertools.featureflags.store.StoreProvider;

/**
 * Evaluates feature flags from a store using the Powertools JSON schema.
 * <p>
 * Evaluation rules (compatible with Powertools for AWS Lambda (Python)):
 * <ol>
 *     <li>Feature exists and a rule matches → return that rule's {@code when_match}</li>
 *     <li>Feature exists but has no rules or no match → return the feature {@code default}</li>
 *     <li>Feature is missing, or the store fails → return the caller-supplied default</li>
 * </ol>
 * Invalid schema documents raise
 * {@link software.amazon.lambda.powertools.featureflags.exception.SchemaValidationException}.
 */
public final class FeatureFlags implements Resource {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureFlags.class);
    private static final String PRIME_SCHEMA = "{"
            + "\"static_flag\": { \"default\": true },"
            + "\"dynamic_flag\": {"
            + "  \"default\": false,"
            + "  \"rules\": {"
            + "    \"match\": {"
            + "      \"when_match\": true,"
            + "      \"conditions\": ["
            + "        { \"action\": \"EQUALS\", \"key\": \"tier\", \"value\": \"premium\" }"
            + "      ]"
            + "    }"
            + "  }"
            + "}"
            + "}";

    // Dummy instance so CRaC registration happens at class load (SnapStart priming).
    private static final FeatureFlags CRAC_INSTANCE =
            new FeatureFlags(InMemoryStore.fromJson("{}"), Clock.systemUTC());

    static {
        Core.getGlobalContext().register(CRAC_INSTANCE);
    }

    private final StoreProvider store;
    private final Clock clock;

    FeatureFlags(StoreProvider store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * Create a builder for {@link FeatureFlags}.
     *
     * @return a new builder
     */
    public static FeatureFlagsBuilder builder() {
        return new FeatureFlagsBuilder();
    }

    /**
     * Evaluate a feature with an empty context.
     *
     * @param name         feature name
     * @param defaultValue value used when the feature is missing or the store fails
     * @param <T>          boolean or any JSON value for non-boolean features
     * @return the evaluated value
     */
    public <T> T evaluate(String name, T defaultValue) {
        return evaluate(name, Collections.emptyMap(), defaultValue);
    }

    /**
     * Evaluate a feature against the given context.
     *
     * @param name         feature name
     * @param context      attributes matched against rule conditions; {@code null} is treated as empty
     * @param defaultValue value used when the feature is missing or the store fails
     * @param <T>          boolean or any JSON value for non-boolean features
     * @return the evaluated value
     */
    @SuppressWarnings("unchecked")
    public <T> T evaluate(String name, Map<String, Object> context, T defaultValue) {
        Map<String, Object> safeContext = context == null ? Collections.emptyMap() : context;
        Map<String, Object> features;
        try {
            features = getConfiguration();
        } catch (ConfigurationStoreException err) {
            LOG.debug("Failed to fetch feature flags from store, returning default provided, reason={}",
                    err.getMessage());
            return defaultValue;
        }

        Object featureObj = features.get(name);
        if (!(featureObj instanceof Map)) {
            LOG.debug("Feature not found; returning default provided, name={}, default={}", name, defaultValue);
            return defaultValue;
        }

        Map<String, Object> feature = (Map<String, Object>) featureObj;
        Map<String, Object> rules = rulesOf(feature);
        Object featDefault = feature.get(FeatureSchema.FEATURE_DEFAULT_VAL_KEY);
        boolean booleanFeature = isBooleanFeature(feature);

        if (rules.isEmpty()) {
            LOG.debug("no rules found, returning feature default, name={}, boolean_feature={}", name, booleanFeature);
            return (T) (booleanFeature ? asBoolean(featDefault) : featDefault);
        }

        return (T) evaluateRules(name, safeContext, featDefault, rules, booleanFeature);
    }

    /**
     * Return the names of features that evaluate to a truthy value for an empty context.
     *
     * @return enabled feature names, in document order
     */
    public List<String> getEnabledFeatures() {
        return getEnabledFeatures(Collections.emptyMap());
    }

    /**
     * Return the names of features that evaluate to a truthy value for {@code context}.
     * <p>
     * A feature is included when it is enabled by default with no rules, or when
     * rule evaluation yields a truthy result (Python-compatible).
     *
     * @param context attributes matched against rule conditions; {@code null} is treated as empty
     * @return enabled feature names, in document order
     */
    @SuppressWarnings("unchecked")
    public List<String> getEnabledFeatures(Map<String, Object> context) {
        Map<String, Object> safeContext = context == null ? Collections.emptyMap() : context;
        List<String> enabled = new ArrayList<>();
        Map<String, Object> features;
        try {
            features = getConfiguration();
        } catch (ConfigurationStoreException err) {
            LOG.debug("Failed to fetch feature flags from store, returning empty list, reason={}", err.getMessage());
            return enabled;
        }

        for (Map.Entry<String, Object> entry : features.entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> feature = (Map<String, Object>) entry.getValue();
            Map<String, Object> rules = rulesOf(feature);
            Object featureDefault = feature.get(FeatureSchema.FEATURE_DEFAULT_VAL_KEY);
            boolean booleanFeature = isBooleanFeature(feature);

            if (isTruthy(featureDefault) && rules.isEmpty()) {
                LOG.debug("feature is enabled by default and has no defined rules, name={}", name);
                enabled.add(name);
            } else if (isTruthy(evaluateRules(name, safeContext, featureDefault, rules, booleanFeature))) {
                LOG.debug("feature's calculated value is True, name={}", name);
                enabled.add(name);
            }
        }
        return enabled;
    }

    /**
     * Fetch and validate the schema from the configured store.
     *
     * @return the validated document
     */
    public Map<String, Object> getConfiguration() {
        LOG.debug("Fetching schema from registered store, store={}", store);
        Map<String, Object> config = store.getConfiguration();
        SchemaValidator.validate(config);
        return config;
    }

    /**
     * SnapStart / CRaC hook: prime JSON parsing, schema validation, and evaluation
     * so those classes are loaded before restore.
     */
    @Override
    public void beforeCheckpoint(org.crac.Context<? extends Resource> context) {
        InMemoryStore primingStore = InMemoryStore.fromJson(PRIME_SCHEMA);
        FeatureFlags primer = new FeatureFlags(primingStore, Clock.systemUTC());
        primer.evaluate("static_flag", false);
        primer.evaluate("dynamic_flag", Map.of("tier", "premium"), false);
        primer.getEnabledFeatures(Map.of("tier", "premium"));
        RuleAction.fromValue(RuleAction.EQUALS.getValue());
        SchemaValidator.validate(primingStore.getConfiguration());
        try {
            ClassPreLoader.preloadClasses();
        } catch (LinkageError e) {
            // ClassPreLoader only swallows ClassNotFoundException. A generated
            // classesloaded entry can still throw NoClassDefFoundError for an
            // optional dependency; SnapStart must not fail because of priming.
            LOG.debug("Feature flags: class preload skipped: {}", e.toString());
        }
        LOG.debug("Feature flags: beforeCheckpoint completed");
    }

    @Override
    public void afterRestore(org.crac.Context<? extends Resource> context) {
        // No action needed after restore
    }

    @SuppressWarnings("unchecked")
    private Object evaluateRules(String featureName, Map<String, Object> context, Object featDefault,
            Map<String, Object> rules, boolean booleanFeature) {
        for (Map.Entry<String, Object> ruleEntry : rules.entrySet()) {
            String ruleName = ruleEntry.getKey();
            if (!(ruleEntry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> rule = (Map<String, Object>) ruleEntry.getValue();
            if (evaluateConditions(ruleName, featureName, rule, context)) {
                Object matchValue = rule.get(FeatureSchema.RULE_MATCH_VALUE);
                return booleanFeature ? asBoolean(matchValue) : matchValue;
            }
        }
        LOG.debug("no rule matched, returning feature default, name={}, boolean_feature={}",
                featureName, booleanFeature);
        return featDefault;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateConditions(String ruleName, String featureName, Map<String, Object> rule,
            Map<String, Object> context) {
        Object conditionsObj = rule.get(FeatureSchema.CONDITIONS_KEY);
        if (!(conditionsObj instanceof List) || ((List<?>) conditionsObj).isEmpty()) {
            LOG.debug("rule did not match, no conditions to match, rule_name={}, name={}", ruleName, featureName);
            return false;
        }

        for (Object conditionObj : (List<?>) conditionsObj) {
            if (!(conditionObj instanceof Map)) {
                return false;
            }
            Map<String, Object> condition = (Map<String, Object>) conditionObj;
            String actionName = stringOrEmpty(condition.get(FeatureSchema.CONDITION_ACTION));
            RuleAction action = RuleAction.fromValue(actionName);
            String key = stringOrEmpty(condition.get(FeatureSchema.CONDITION_KEY));
            Object conditionValue = condition.get(FeatureSchema.CONDITION_VALUE);
            Object contextValue = action != null && action.isTimeBased()
                    ? key
                    : context.get(key);

            if (!matchByAction(action, conditionValue, contextValue)) {
                LOG.debug("rule did not match action, rule_name={}, name={}", ruleName, featureName);
                return false;
            }
        }
        LOG.debug("rule matched, rule_name={}, name={}", ruleName, featureName);
        return true;
    }

    private boolean matchByAction(RuleAction action, Object conditionValue, Object contextValue) {
        try {
            return ConditionMatcher.matches(action, contextValue, conditionValue, clock);
        } catch (RuntimeException exc) {
            LOG.debug("caught exception while matching action: action={}, exception={}", action, exc.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rulesOf(Map<String, Object> feature) {
        Object rules = feature.get(FeatureSchema.RULES_KEY);
        if (!(rules instanceof Map) || ((Map<?, ?>) rules).isEmpty()) {
            return Collections.emptyMap();
        }
        // Preserve document order for first-match-wins.
        return (Map<String, Object>) rules;
    }

    private static boolean isBooleanFeature(Map<String, Object> feature) {
        Object type = feature.get(FeatureSchema.FEATURE_DEFAULT_VAL_TYPE_KEY);
        if (type instanceof Boolean) {
            return (Boolean) type;
        }
        return true;
    }

    /**
     * Python {@code bool(value)} used when {@code boolean_type} is true.
     */
    static boolean asBoolean(Object value) {
        return isTruthy(value);
    }

    /**
     * Python truthiness: {@code None}/empty/zero/false are false; everything else is true.
     */
    static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length() > 0;
        }
        if (value instanceof Collection) {
            return !((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return true;
    }

    private static String stringOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
