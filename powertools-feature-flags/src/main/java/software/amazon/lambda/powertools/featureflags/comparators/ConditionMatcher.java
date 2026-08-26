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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import software.amazon.lambda.powertools.featureflags.schema.ModuloRangeValues;
import software.amazon.lambda.powertools.featureflags.schema.RuleAction;
import software.amazon.lambda.powertools.featureflags.schema.TimeValues;

/**
 * Evaluates a single condition against a context value.
 * <p>
 * Matching Python Powertools, unexpected types or comparison failures return {@code false}
 * rather than throwing (the evaluator catches those failures).
 */
public final class ConditionMatcher {

    private ConditionMatcher() {
    }

    /**
     * @param action         condition action
     * @param contextValue   value taken from the evaluation context (or the condition key for time actions)
     * @param conditionValue value from the schema condition
     * @param clock          clock used by time-based actions
     * @return whether the condition matches
     */
    public static boolean matches(RuleAction action, Object contextValue, Object conditionValue, Clock clock) {
        if (action == null) {
            return false;
        }
        switch (action) {
            case EQUALS:
                return valuesEqual(contextValue, conditionValue);
            case NOT_EQUALS:
                return !valuesEqual(contextValue, conditionValue);
            case KEY_GREATER_THAN_VALUE:
                return compare(contextValue, conditionValue) > 0;
            case KEY_GREATER_THAN_OR_EQUAL_VALUE:
                return compare(contextValue, conditionValue) >= 0;
            case KEY_LESS_THAN_VALUE:
                return compare(contextValue, conditionValue) < 0;
            case KEY_LESS_THAN_OR_EQUAL_VALUE:
                return compare(contextValue, conditionValue) <= 0;
            case STARTSWITH:
                return asString(contextValue).startsWith(asString(conditionValue));
            case ENDSWITH:
                return asString(contextValue).endsWith(asString(conditionValue));
            case IN:
            case KEY_IN_VALUE:
                return isContainedIn(contextValue, conditionValue);
            case NOT_IN:
            case KEY_NOT_IN_VALUE:
                return !isContainedIn(contextValue, conditionValue);
            case VALUE_IN_KEY:
                return isContainedIn(conditionValue, contextValue);
            case VALUE_NOT_IN_KEY:
                return !isContainedIn(conditionValue, contextValue);
            case ANY_IN_VALUE:
                return compareAnyInList(contextValue, conditionValue);
            case ALL_IN_VALUE:
                return compareAllInList(contextValue, conditionValue);
            case NONE_IN_VALUE:
                return compareNoneInList(contextValue, conditionValue);
            case SCHEDULE_BETWEEN_TIME_RANGE:
                return compareTimeRange(conditionValue, clock);
            case SCHEDULE_BETWEEN_DATETIME_RANGE:
                return compareDateTimeRange(conditionValue, clock);
            case SCHEDULE_BETWEEN_DAYS_OF_WEEK:
                return compareDaysOfWeek(conditionValue, clock);
            case MODULO_RANGE:
                return compareModuloRange(contextValue, conditionValue);
            default:
                return false;
        }
    }

    static boolean valuesEqual(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        if (left instanceof Number && right instanceof Number) {
            return toBigDecimal((Number) left).compareTo(toBigDecimal((Number) right)) == 0;
        }
        if (left instanceof Boolean && right instanceof Number) {
            return booleanAsNumber((Boolean) left).compareTo(toBigDecimal((Number) right)) == 0;
        }
        if (right instanceof Boolean && left instanceof Number) {
            return toBigDecimal((Number) left).compareTo(booleanAsNumber((Boolean) right)) == 0;
        }
        return false;
    }

    static int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return toBigDecimal((Number) left).compareTo(toBigDecimal((Number) right));
        }
        if (left instanceof String && right instanceof String) {
            return ((String) left).compareTo((String) right);
        }
        throw new IllegalArgumentException("Cannot compare " + typeName(left) + " with " + typeName(right));
    }

    static boolean isContainedIn(Object item, Object container) {
        if (container instanceof Map) {
            return mapContainsKey((Map<?, ?>) container, item);
        }
        if (container instanceof Collection) {
            return collectionContains((Collection<?>) container, item);
        }
        if (container instanceof String && item instanceof String) {
            return ((String) container).contains((String) item);
        }
        throw new IllegalArgumentException("Cannot test membership of " + typeName(item)
                + " in " + typeName(container));
    }

    static boolean compareAnyInList(Object contextValue, Object conditionValue) {
        List<?> contextList = requireList(contextValue, "ANY_IN_VALUE");
        Collection<?> conditionList = requireCollection(conditionValue, "ANY_IN_VALUE");
        for (Object key : contextList) {
            if (collectionContains(conditionList, key)) {
                return true;
            }
        }
        return false;
    }

    static boolean compareAllInList(Object contextValue, Object conditionValue) {
        List<?> contextList = requireList(contextValue, "ALL_IN_VALUE");
        Collection<?> conditionList = requireCollection(conditionValue, "ALL_IN_VALUE");
        for (Object key : contextList) {
            if (!collectionContains(conditionList, key)) {
                return false;
            }
        }
        return true;
    }

    static boolean compareNoneInList(Object contextValue, Object conditionValue) {
        List<?> contextList = requireList(contextValue, "NONE_IN_VALUE");
        Collection<?> conditionList = requireCollection(conditionValue, "NONE_IN_VALUE");
        for (Object key : contextList) {
            if (collectionContains(conditionList, key)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    static boolean compareTimeRange(Object conditionValue, Clock clock) {
        Map<String, Object> value = requireMap(conditionValue, "SCHEDULE_BETWEEN_TIME_RANGE");
        ZoneId zone = zoneId(value);
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        LocalTime start = parseHourMinute(stringValue(value, TimeValues.START));
        LocalTime end = parseHourMinute(stringValue(value, TimeValues.END));
        LocalTime current = now.toLocalTime();
        if (end.isBefore(start)) {
            return !current.isBefore(start) || !current.isAfter(end);
        }
        return !current.isBefore(start) && !current.isAfter(end);
    }

    @SuppressWarnings("unchecked")
    static boolean compareDateTimeRange(Object conditionValue, Clock clock) {
        Map<String, Object> value = requireMap(conditionValue, "SCHEDULE_BETWEEN_DATETIME_RANGE");
        ZoneId zone = zoneId(value);
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        ZonedDateTime start = parseDateTime(stringValue(value, TimeValues.START), zone);
        ZonedDateTime end = parseDateTime(stringValue(value, TimeValues.END), zone);
        return !now.isBefore(start) && !now.isAfter(end);
    }

    @SuppressWarnings("unchecked")
    static boolean compareDaysOfWeek(Object conditionValue, Clock clock) {
        Map<String, Object> value = requireMap(conditionValue, "SCHEDULE_BETWEEN_DAYS_OF_WEEK");
        ZoneId zone = zoneId(value);
        String currentDay = ZonedDateTime.now(clock)
                .withZoneSameInstant(zone)
                .getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                .toUpperCase(Locale.ROOT);
        Object daysObj = value.get(TimeValues.DAYS);
        if (!(daysObj instanceof Collection)) {
            return false;
        }
        for (Object day : (Collection<?>) daysObj) {
            if (day != null && currentDay.equals(day.toString().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static boolean compareModuloRange(Object contextValue, Object conditionValue) {
        if (!(contextValue instanceof Number)) {
            throw new IllegalArgumentException("MODULO_RANGE context must be a number");
        }
        Map<String, Object> value = requireMap(conditionValue, "MODULO_RANGE");
        long base = longValue(value.get(ModuloRangeValues.BASE), 1);
        long start = longValue(value.get(ModuloRangeValues.START), 1);
        long end = longValue(value.get(ModuloRangeValues.END), 1);
        if (base == 0) {
            throw new IllegalArgumentException("MODULO_RANGE base must not be zero");
        }
        long remainder = ((Number) contextValue).longValue() % base;
        return start <= remainder && remainder <= end;
    }

    private static boolean collectionContains(Collection<?> collection, Object item) {
        for (Object candidate : collection) {
            if (valuesEqual(candidate, item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mapContainsKey(Map<?, ?> map, Object key) {
        if (map.containsKey(key)) {
            return true;
        }
        for (Object candidate : map.keySet()) {
            if (valuesEqual(candidate, key)) {
                return true;
            }
        }
        return false;
    }

    private static List<?> requireList(Object value, String action) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(
                    "Context provided must be a list. Unable to compare " + action + " action.");
        }
        return (List<?>) value;
    }

    private static Collection<?> requireCollection(Object value, String action) {
        if (!(value instanceof Collection)) {
            throw new IllegalArgumentException(
                    "Condition value provided must be a list. Unable to compare " + action + " action.");
        }
        return (Collection<?>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Object value, String action) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(action + " condition value must be an object");
        }
        return (Map<String, Object>) value;
    }

    private static String asString(Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected a string, got " + typeName(value));
        }
        return (String) value;
    }

    private static String stringValue(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        return raw == null ? "" : raw.toString();
    }

    private static ZoneId zoneId(Map<String, Object> value) {
        Object raw = value.get(TimeValues.TIMEZONE);
        String name = raw == null || raw.toString().isBlank() ? TimeValues.DEFAULT_TIMEZONE : raw.toString();
        return ZoneId.of(name);
    }

    private static LocalTime parseHourMinute(String value) {
        String[] parts = value.split(TimeValues.HOUR_MIN_SEPARATOR);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Time must be HH:MM, got '" + value + "'");
        }
        return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static ZonedDateTime parseDateTime(String value, ZoneId zone) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone);
        } catch (DateTimeParseException ignored) {
            // fall through to offset-aware parse
        }
        return ZonedDateTime.parse(value).withZoneSameInstant(zone);
    }

    private static long longValue(Object raw, long defaultValue) {
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw == null) {
            return defaultValue;
        }
        return Long.parseLong(raw.toString());
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        if (number instanceof Float || number instanceof Double) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.valueOf(number.longValue());
    }

    private static BigDecimal booleanAsNumber(Boolean value) {
        return value ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
