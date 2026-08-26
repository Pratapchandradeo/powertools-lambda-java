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
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Condition actions supported by the Powertools feature-flag schema.
 */
public enum RuleAction {
    EQUALS("EQUALS"),
    NOT_EQUALS("NOT_EQUALS"),
    KEY_GREATER_THAN_VALUE("KEY_GREATER_THAN_VALUE"),
    KEY_GREATER_THAN_OR_EQUAL_VALUE("KEY_GREATER_THAN_OR_EQUAL_VALUE"),
    KEY_LESS_THAN_VALUE("KEY_LESS_THAN_VALUE"),
    KEY_LESS_THAN_OR_EQUAL_VALUE("KEY_LESS_THAN_OR_EQUAL_VALUE"),
    STARTSWITH("STARTSWITH"),
    ENDSWITH("ENDSWITH"),
    IN("IN"),
    NOT_IN("NOT_IN"),
    KEY_IN_VALUE("KEY_IN_VALUE"),
    KEY_NOT_IN_VALUE("KEY_NOT_IN_VALUE"),
    VALUE_IN_KEY("VALUE_IN_KEY"),
    VALUE_NOT_IN_KEY("VALUE_NOT_IN_KEY"),
    ALL_IN_VALUE("ALL_IN_VALUE"),
    ANY_IN_VALUE("ANY_IN_VALUE"),
    NONE_IN_VALUE("NONE_IN_VALUE"),
    SCHEDULE_BETWEEN_TIME_RANGE("SCHEDULE_BETWEEN_TIME_RANGE"),
    SCHEDULE_BETWEEN_DATETIME_RANGE("SCHEDULE_BETWEEN_DATETIME_RANGE"),
    SCHEDULE_BETWEEN_DAYS_OF_WEEK("SCHEDULE_BETWEEN_DAYS_OF_WEEK"),
    MODULO_RANGE("MODULO_RANGE");

    private static final Map<String, RuleAction> BY_VALUE = Collections.unmodifiableMap(
            Arrays.stream(values()).collect(Collectors.toMap(RuleAction::getValue, Function.identity())));

    private final String value;

    RuleAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * @return the action for {@code value}, or {@code null} if unknown
     */
    public static RuleAction fromValue(String value) {
        if (value == null) {
            return null;
        }
        return BY_VALUE.get(value);
    }

    public boolean isTimeBased() {
        return this == SCHEDULE_BETWEEN_TIME_RANGE
                || this == SCHEDULE_BETWEEN_DATETIME_RANGE
                || this == SCHEDULE_BETWEEN_DAYS_OF_WEEK;
    }
}
