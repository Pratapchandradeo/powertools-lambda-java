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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;

class JsonConfigurationParserTest {

    @Test
    void parsePreservesKeyOrder() {
        Map<String, Object> parsed = JsonConfigurationParser.parse(
                "{\"first\": {\"default\": true}, \"second\": {\"default\": false}}", null);

        assertThat(parsed.keySet()).containsExactly("first", "second");
    }

    @Test
    void parseAppliesEnvelope() {
        Map<String, Object> parsed = JsonConfigurationParser.parse(
                "{\"wrap\": {\"flag\": {\"default\": true}}}", "wrap");

        assertThat(parsed).containsKey("flag");
    }

    @Test
    void parseRejectsJsonArray() {
        assertThatThrownBy(() -> JsonConfigurationParser.parse("[1, 2]", null))
                .isInstanceOf(ConfigurationStoreException.class)
                .hasMessageContaining("object");
    }

    @Test
    void parseRejectsEnvelopeThatYieldsArray() {
        assertThatThrownBy(() -> JsonConfigurationParser.parse("{\"items\": [1, 2]}", "items"))
                .isInstanceOf(ConfigurationStoreException.class)
                .hasMessageContaining("object");
    }
}
