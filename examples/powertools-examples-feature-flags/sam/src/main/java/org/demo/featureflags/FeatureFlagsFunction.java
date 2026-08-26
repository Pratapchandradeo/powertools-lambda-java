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

package org.demo.featureflags;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.featureflags.FeatureFlags;
import software.amazon.lambda.powertools.featureflags.store.AppConfigStore;

/**
 * Evaluates feature flags from AppConfig using API Gateway query parameters as context.
 */
public class FeatureFlagsFunction
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureFlagsFunction.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeatureFlags featureFlags = FeatureFlags.builder()
            .withStore(AppConfigStore.builder()
                    .withApplication(System.getenv("POWERTOOLS_APPCONFIG_APPLICATION"))
                    .withEnvironment(System.getenv("POWERTOOLS_APPCONFIG_ENVIRONMENT"))
                    .withName(System.getenv("POWERTOOLS_APPCONFIG_NAME"))
                    .build())
            .build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        Map<String, Object> evaluationContext = toContext(input.getQueryStringParameters());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("premium_features",
                featureFlags.evaluate("premium_features", evaluationContext, false));
        body.put("ten_percent_off_campaign",
                featureFlags.evaluate("ten_percent_off_campaign", evaluationContext, false));
        body.put("geo_customer_campaign",
                featureFlags.evaluate("geo_customer_campaign", evaluationContext, false));
        body.put("enabled_features", featureFlags.getEnabledFeatures(evaluationContext));

        LOG.info("Evaluated flags for context {}: {}", evaluationContext, body);

        try {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(headers)
                    .withStatusCode(200)
                    .withBody(MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(headers)
                    .withStatusCode(500)
                    .withBody("{\"message\":\"failed to serialize response\"}");
        }
    }

    private static Map<String, Object> toContext(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(query);
    }
}
