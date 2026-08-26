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

package software.amazon.lambda.powertools.featureflags.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPathException;
import io.burt.jmespath.jackson.JacksonRuntime;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;

/**
 * Parses a feature-flag JSON document and optionally unwraps a JMESPath envelope.
 */
final class JsonConfigurationParser {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };
    private static final JacksonRuntime JMES_PATH = new JacksonRuntime();

    private JsonConfigurationParser() {
    }

    static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            throw new ConfigurationStoreException("Feature flag JSON must not be null or blank");
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || node.isMissingNode() || node.isNull()) {
                throw new ConfigurationStoreException("Feature flag JSON must be an object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new ConfigurationStoreException("Unable to parse feature flag JSON", e);
        }
    }

    static JsonNode applyEnvelope(JsonNode document, String envelope) {
        if (envelope == null || envelope.isBlank()) {
            return document;
        }
        try {
            Expression<JsonNode> expression = JMES_PATH.compile(envelope);
            JsonNode extracted = expression.search(document);
            if (extracted == null || extracted.isNull() || extracted.isMissingNode()) {
                throw new ConfigurationStoreException(
                        "Envelope '" + envelope + "' did not match any object in the configuration");
            }
            return extracted;
        } catch (ConfigurationStoreException e) {
            throw e;
        } catch (JmesPathException e) {
            throw new ConfigurationStoreException("Invalid envelope JMESPath expression: " + envelope, e);
        }
    }

    static Map<String, Object> toFeatureMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new ConfigurationStoreException("Feature flag JSON must be an object");
        }
        return MAPPER.convertValue(node, MAP_TYPE);
    }

    static Map<String, Object> parse(String json, String envelope) {
        JsonNode node = applyEnvelope(readTree(json), envelope);
        return toFeatureMap(node);
    }
}
