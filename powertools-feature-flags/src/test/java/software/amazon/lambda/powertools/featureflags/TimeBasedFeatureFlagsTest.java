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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.featureflags.store.InMemoryStore;

class TimeBasedFeatureFlagsTest {

    // Saturday 15 June 2024, 12:00 UTC
    private static final Clock NOON_UTC = Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void timeRangeEnablesFeatureDuringBusinessHours() {
        FeatureFlags flags = flags("{"
                + "\"support_chat\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"office_hours\": {"
                + "      \"when_match\": true,"
                + "      \"conditions\": ["
                + "        { \"action\": \"SCHEDULE_BETWEEN_TIME_RANGE\", \"key\": \"CURRENT_TIME\","
                + "          \"value\": { \"START\": \"09:00\", \"END\": \"17:00\", \"TIMEZONE\": \"UTC\" } }"
                + "      ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("support_chat", false)).isTrue();
    }

    @Test
    void weekendRule() {
        FeatureFlags flags = flags("{"
                + "\"weekend_mode\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"weekend\": {"
                + "      \"when_match\": true,"
                + "      \"conditions\": ["
                + "        { \"action\": \"SCHEDULE_BETWEEN_DAYS_OF_WEEK\", \"key\": \"CURRENT_TIME\","
                + "          \"value\": { \"DAYS\": [\"SATURDAY\", \"SUNDAY\"], \"TIMEZONE\": \"UTC\" } }"
                + "      ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("weekend_mode", false)).isTrue();
    }

    @Test
    void datetimeLaunchWindow() {
        FeatureFlags flags = flags("{"
                + "\"new_checkout\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"launch\": {"
                + "      \"when_match\": true,"
                + "      \"conditions\": ["
                + "        { \"action\": \"SCHEDULE_BETWEEN_DATETIME_RANGE\", \"key\": \"CURRENT_TIME\","
                + "          \"value\": { \"START\": \"2024-06-01T00:00:00\","
                + "                      \"END\": \"2024-06-30T23:59:59\", \"TIMEZONE\": \"UTC\" } }"
                + "      ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("new_checkout", false)).isTrue();
    }

    @Test
    void timeBasedRuleIgnoresUserContext() {
        FeatureFlags flags = flags("{"
                + "\"maintenance\": {"
                + "  \"default\": false,"
                + "  \"rules\": {"
                + "    \"window\": {"
                + "      \"when_match\": true,"
                + "      \"conditions\": ["
                + "        { \"action\": \"SCHEDULE_BETWEEN_TIME_RANGE\", \"key\": \"CURRENT_TIME\","
                + "          \"value\": { \"START\": \"09:00\", \"END\": \"17:00\", \"TIMEZONE\": \"UTC\" } }"
                + "      ]"
                + "    }"
                + "  }"
                + "}"
                + "}");

        assertThat(flags.evaluate("maintenance", Map.of("CURRENT_TIME", "00:00"), false)).isTrue();
    }

    @Test
    void timezoneShiftsEvaluation() {
        // 12:00 UTC is 08:00 America/New_York (EDT in June) — outside 09:00-17:00 ET
        FeatureFlags flags = FeatureFlags.builder()
                .withStore(InMemoryStore.fromJson("{"
                        + "\"us_hours\": {"
                        + "  \"default\": false,"
                        + "  \"rules\": {"
                        + "    \"et\": {"
                        + "      \"when_match\": true,"
                        + "      \"conditions\": ["
                        + "        { \"action\": \"SCHEDULE_BETWEEN_TIME_RANGE\", \"key\": \"CURRENT_TIME\","
                        + "          \"value\": { \"START\": \"09:00\", \"END\": \"17:00\","
                        + "                      \"TIMEZONE\": \"America/New_York\" } }"
                        + "      ]"
                        + "    }"
                        + "  }"
                        + "}"
                        + "}"))
                .withClock(NOON_UTC)
                .build();

        assertThat(flags.evaluate("us_hours", false)).isFalse();
    }

    private static FeatureFlags flags(String json) {
        return FeatureFlags.builder()
                .withStore(InMemoryStore.fromJson(json))
                .withClock(NOON_UTC)
                .build();
    }
}
