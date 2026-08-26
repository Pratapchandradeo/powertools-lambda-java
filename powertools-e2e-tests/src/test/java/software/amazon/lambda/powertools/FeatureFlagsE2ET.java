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

package software.amazon.lambda.powertools;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.lambda.powertools.testutils.Infrastructure.FUNCTION_NAME_OUTPUT;
import static software.amazon.lambda.powertools.testutils.lambda.LambdaInvoker.invokeFunction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import software.amazon.lambda.powertools.testutils.AppConfig;
import software.amazon.lambda.powertools.testutils.Infrastructure;
import software.amazon.lambda.powertools.testutils.lambda.InvocationResult;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeatureFlagsE2ET {

    private static final String PROFILE = "features";
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
            + "\"ten_percent_off_campaign\": { \"default\": true }"
            + "}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppConfig appConfig;
    private Infrastructure infrastructure;
    private String functionName;

    FeatureFlagsE2ET() {
        Map<String, String> params = new HashMap<>();
        params.put(PROFILE, SCHEMA);
        appConfig = new AppConfig(UUID.randomUUID().toString(), "e2etest", params);
    }

    @BeforeAll
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void setup() {
        infrastructure = Infrastructure.builder()
                .testName(FeatureFlagsE2ET.class.getSimpleName())
                .pathToFunction("feature-flags")
                .appConfig(appConfig)
                .environmentVariables(
                        Stream.of(new String[][] {
                                { "POWERTOOLS_LOG_LEVEL", "INFO" },
                                { "POWERTOOLS_SERVICE_NAME", FeatureFlagsE2ET.class.getSimpleName() }
                        })
                                .collect(Collectors.toMap(data -> data[0], data -> data[1])))
                .build();
        Map<String, String> outputs = infrastructure.deploy();
        functionName = outputs.get(FUNCTION_NAME_OUTPUT);
    }

    @AfterAll
    void tearDown() {
        if (infrastructure != null) {
            infrastructure.destroy();
        }
    }

    @Test
    void premiumContextEnablesDynamicAndStaticFlags() throws JsonProcessingException {
        JsonNode result = invoke("premium");

        assertThat(result.get("premium_features").booleanValue()).isTrue();
        assertThat(result.get("ten_percent_off_campaign").booleanValue()).isTrue();
        assertThat(enabledFeatures(result)).containsExactly("premium_features", "ten_percent_off_campaign");
    }

    @Test
    void standardContextKeepsOnlyStaticFlag() throws JsonProcessingException {
        JsonNode result = invoke("standard");

        assertThat(result.get("premium_features").booleanValue()).isFalse();
        assertThat(result.get("ten_percent_off_campaign").booleanValue()).isTrue();
        assertThat(enabledFeatures(result)).containsExactly("ten_percent_off_campaign");
    }

    private JsonNode invoke(String tier) throws JsonProcessingException {
        String event = "{"
                + "\"application\": \"" + appConfig.getApplication() + "\","
                + "\"environment\": \"" + appConfig.getEnvironment() + "\","
                + "\"name\": \"" + PROFILE + "\","
                + "\"context\": { \"tier\": \"" + tier + "\" }"
                + "}";
        InvocationResult invocation = invokeFunction(functionName, event);
        assertThat(invocation.getFunctionError())
                .as("function error: %s body=%s", invocation.getFunctionError(), invocation.getResult())
                .isNull();
        return MAPPER.readTree(invocation.getResult());
    }

    private static List<String> enabledFeatures(JsonNode result) {
        return MAPPER.convertValue(result.get("enabled_features"), new TypeReference<List<String>>() {
        });
    }
}
