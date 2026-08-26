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

import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;
import software.amazon.lambda.powertools.parameters.appconfig.AppConfigProvider;

/**
 * {@link StoreProvider} that loads a feature-flag JSON document from AWS AppConfig.
 * <p>
 * Fetching, session tokens, and in-memory cache are delegated to
 * {@link AppConfigProvider}. This class parses the profile payload, optionally
 * unwraps a JMESPath envelope, and maps store failures to
 * {@link ConfigurationStoreException}.
 */
public final class AppConfigStore implements StoreProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfigStore.class);

    private final AppConfigProvider provider;
    private final String name;
    private final String envelope;
    private final Integer maxAge;
    private final ChronoUnit maxAgeUnit;

    AppConfigStore(AppConfigProvider provider, String name, String envelope, Integer maxAge, ChronoUnit maxAgeUnit) {
        this.provider = provider;
        this.name = name;
        this.envelope = envelope;
        this.maxAge = maxAge;
        this.maxAgeUnit = maxAgeUnit;
    }

    /**
     * @return a builder for {@link AppConfigStore}
     */
    public static AppConfigStoreBuilder builder() {
        return new AppConfigStoreBuilder();
    }

    @Override
    public Map<String, Object> getConfiguration() {
        try {
            String raw = fetch();
            LOG.debug("Fetched AppConfig profile '{}'", name);
            return JsonConfigurationParser.parse(raw, envelope);
        } catch (ConfigurationStoreException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConfigurationStoreException(
                    "Unable to fetch feature flags from AppConfig profile '" + name + "'", e);
        }
    }

    private String fetch() {
        if (maxAge != null && maxAgeUnit != null) {
            return provider.withMaxAge(maxAge, maxAgeUnit).get(name);
        }
        return provider.get(name);
    }
}
