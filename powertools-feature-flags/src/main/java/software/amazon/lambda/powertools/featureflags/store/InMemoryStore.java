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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link StoreProvider} backed by an in-memory JSON document.
 * <p>
 * Intended for unit tests and for injecting a known schema without AWS.
 */
public final class InMemoryStore implements StoreProvider {

    private final Map<String, Object> configuration;

    private InMemoryStore(Map<String, Object> configuration) {
        this.configuration = Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
    }

    /**
     * Create a store from an already-parsed document. Iteration order is preserved.
     *
     * @param configuration feature name → feature object
     * @return a store that returns a defensive copy of {@code configuration}
     */
    public static InMemoryStore fromMap(Map<String, Object> configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        return new InMemoryStore(configuration);
    }

    /**
     * Parse a JSON object into a store.
     *
     * @param json a JSON object whose keys are feature names
     * @return a store holding the parsed document
     * @throws ConfigurationStoreException if {@code json} is not a JSON object
     */
    public static InMemoryStore fromJson(String json) {
        return new InMemoryStore(JsonConfigurationParser.parse(json, null));
    }

    @Override
    public Map<String, Object> getConfiguration() {
        return configuration;
    }
}
