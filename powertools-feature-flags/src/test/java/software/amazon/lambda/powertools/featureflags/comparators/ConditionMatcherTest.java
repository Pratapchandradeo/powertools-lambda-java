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

package software.amazon.lambda.powertools.featureflags.comparators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.lambda.powertools.featureflags.schema.RuleAction;

class ConditionMatcherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void equalsHandlesNumericWidening() {
        assertThat(ConditionMatcher.matches(RuleAction.EQUALS, 1, 1L, CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.EQUALS, 1, 1.0d, CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.EQUALS, true, 1, CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.EQUALS, false, 0, CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.EQUALS, "1", 1, CLOCK)).isFalse();
        assertThat(ConditionMatcher.matches(RuleAction.NOT_EQUALS, "a", "b", CLOCK)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "KEY_GREATER_THAN_VALUE, 10, 5, true",
            "KEY_GREATER_THAN_VALUE, 5, 10, false",
            "KEY_GREATER_THAN_OR_EQUAL_VALUE, 5, 5, true",
            "KEY_LESS_THAN_VALUE, 1, 2, true",
            "KEY_LESS_THAN_OR_EQUAL_VALUE, 2, 2, true"
    })
    void numericComparisons(RuleAction action, int left, int right, boolean expected) {
        assertThat(ConditionMatcher.matches(action, left, right, CLOCK)).isEqualTo(expected);
    }

    @Test
    void stringComparisons() {
        assertThat(ConditionMatcher.matches(RuleAction.STARTSWITH, "premium-user", "premium", CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.ENDSWITH, "user@example.com", ".com", CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.STARTSWITH, "user", "x", CLOCK)).isFalse();
    }

    @Test
    void inAndKeyInValue() {
        assertThat(ConditionMatcher.matches(RuleAction.KEY_IN_VALUE, "NL", List.of("NL", "IE"), CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.IN, "a", "abc", CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.KEY_NOT_IN_VALUE, "US", List.of("NL", "IE"), CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.VALUE_IN_KEY, List.of("admin", "user"), "admin", CLOCK))
                .isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.VALUE_NOT_IN_KEY, List.of("user"), "admin", CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.IN, "x", Map.of("x", 1), CLOCK)).isTrue();
    }

    @Test
    void listComparators() {
        assertThat(ConditionMatcher.matches(RuleAction.ANY_IN_VALUE,
                List.of("a", "z"), List.of("a", "b"), CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.ALL_IN_VALUE,
                List.of("a", "b"), List.of("a", "b", "c"), CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.ALL_IN_VALUE,
                List.of("a", "z"), List.of("a", "b"), CLOCK)).isFalse();
        assertThat(ConditionMatcher.matches(RuleAction.NONE_IN_VALUE,
                List.of("x", "y"), List.of("a", "b"), CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.NONE_IN_VALUE,
                List.of("a"), List.of("a", "b"), CLOCK)).isFalse();
    }

    @Test
    void listComparatorsRejectNonListContext() {
        assertThatThrownBy(() -> ConditionMatcher.compareAnyInList("not-a-list", List.of("a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a list");
    }

    @Test
    void moduloRange() {
        assertThat(ConditionMatcher.matches(RuleAction.MODULO_RANGE, 15,
                Map.of("BASE", 100, "START", 0, "END", 20), CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.MODULO_RANGE, 50,
                Map.of("BASE", 100, "START", 0, "END", 20), CLOCK)).isFalse();
    }

    @Test
    void timeRangeInclusiveSameDay() {
        // 12:00 UTC is inside 09:00-17:00
        assertThat(ConditionMatcher.matches(RuleAction.SCHEDULE_BETWEEN_TIME_RANGE, "CURRENT_TIME",
                Map.of("START", "09:00", "END", "17:00", "TIMEZONE", "UTC"), CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.SCHEDULE_BETWEEN_TIME_RANGE, "CURRENT_TIME",
                Map.of("START", "18:00", "END", "20:00", "TIMEZONE", "UTC"), CLOCK)).isFalse();
    }

    @Test
    void timeRangeCrossingMidnight() {
        assertThat(ConditionMatcher.matches(RuleAction.SCHEDULE_BETWEEN_TIME_RANGE, "CURRENT_TIME",
                Map.of("START", "22:00", "END", "04:00", "TIMEZONE", "UTC"), CLOCK)).isFalse();
        Clock late = Clock.fixed(Instant.parse("2024-06-15T23:00:00Z"), ZoneOffset.UTC);
        assertThat(ConditionMatcher.matches(RuleAction.SCHEDULE_BETWEEN_TIME_RANGE, "CURRENT_TIME",
                Map.of("START", "22:00", "END", "04:00", "TIMEZONE", "UTC"), late)).isTrue();
    }

    @Test
    void datetimeRange() {
        assertThat(ConditionMatcher.matches(RuleAction.SCHEDULE_BETWEEN_DATETIME_RANGE, "CURRENT_TIME",
                Map.of("START", "2024-06-01T00:00:00", "END", "2024-06-30T23:59:59", "TIMEZONE", "UTC"),
                CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.SCHEDULE_BETWEEN_DATETIME_RANGE, "CURRENT_TIME",
                Map.of("START", "2025-01-01T00:00:00", "END", "2025-12-31T23:59:59", "TIMEZONE", "UTC"),
                CLOCK)).isFalse();
    }

    @Test
    void daysOfWeek() {
        // 2024-06-15 is a Saturday
        assertThat(ConditionMatcher.matches(RuleAction.SCHEDULE_BETWEEN_DAYS_OF_WEEK, "CURRENT_TIME",
                Map.of("DAYS", List.of("SATURDAY", "SUNDAY"), "TIMEZONE", "UTC"), CLOCK)).isTrue();
        assertThat(ConditionMatcher.matches(RuleAction.SCHEDULE_BETWEEN_DAYS_OF_WEEK, "CURRENT_TIME",
                Map.of("DAYS", List.of("MONDAY"), "TIMEZONE", "UTC"), CLOCK)).isFalse();
    }

    @Test
    void unknownActionIsFalse() {
        assertThat(ConditionMatcher.matches(null, "a", "a", CLOCK)).isFalse();
    }
}
