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
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.lambda.powertools.parameters.appconfig.AppConfigProvider;
import software.amazon.lambda.powertools.parameters.appconfig.AppConfigProviderBuilder;
import software.amazon.lambda.powertools.parameters.cache.CacheManager;

/**
 * Builder for {@link AppConfigStore}.
 */
public final class AppConfigStoreBuilder {

    private AppConfigProvider provider;
    private AppConfigDataClient client;
    private CacheManager cacheManager;
    private String application;
    private String environment;
    private String name;
    private String envelope;
    private Integer maxAge;
    private ChronoUnit maxAgeUnit;

    AppConfigStoreBuilder() {
    }

    /**
     * Use an already-built {@link AppConfigProvider}. When set, application,
     * environment, client, and cache manager are ignored.
     *
     * @param provider the Parameters AppConfig provider
     * @return this builder
     */
    public AppConfigStoreBuilder withProvider(AppConfigProvider provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Custom AppConfig Data client (region, credentials, retries, …).
     *
     * @param client AWS SDK client
     * @return this builder
     */
    public AppConfigStoreBuilder withClient(AppConfigDataClient client) {
        this.client = client;
        return this;
    }

    /**
     * Cache manager passed through to {@link AppConfigProvider}.
     *
     * @param cacheManager cache manager
     * @return this builder
     */
    public AppConfigStoreBuilder withCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        return this;
    }

    /**
     * AppConfig application name or ID. Required unless {@link #withProvider} is used.
     *
     * @param application application identifier
     * @return this builder
     */
    public AppConfigStoreBuilder withApplication(String application) {
        this.application = application;
        return this;
    }

    /**
     * AppConfig environment name or ID. Required unless {@link #withProvider} is used.
     *
     * @param environment environment identifier
     * @return this builder
     */
    public AppConfigStoreBuilder withEnvironment(String environment) {
        this.environment = environment;
        return this;
    }

    /**
     * AppConfig configuration profile name. This is the document that contains
     * the feature-flag schema (or an envelope around it).
     *
     * @param name configuration profile identifier
     * @return this builder
     */
    public AppConfigStoreBuilder withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Optional JMESPath expression used to unwrap a nested feature-flag object,
     * matching Python {@code AppConfigStore(envelope=...)}.
     *
     * @param envelope JMESPath expression, for example {@code features}
     * @return this builder
     */
    public AppConfigStoreBuilder withEnvelope(String envelope) {
        this.envelope = envelope;
        return this;
    }

    /**
     * Override the Parameters cache TTL for this store (default is 5 seconds).
     *
     * @param maxAge cache duration
     * @param unit   time unit
     * @return this builder
     */
    public AppConfigStoreBuilder withMaxAge(int maxAge, ChronoUnit unit) {
        this.maxAge = maxAge;
        this.maxAgeUnit = unit;
        return this;
    }

    /**
     * @return a configured {@link AppConfigStore}
     * @throws IllegalStateException if required fields are missing
     */
    public AppConfigStore build() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("No configuration profile name provided; please provide one");
        }
        AppConfigProvider resolved = provider;
        if (resolved == null) {
            if (application == null || application.isBlank()) {
                throw new IllegalStateException("No application provided; please provide one");
            }
            if (environment == null || environment.isBlank()) {
                throw new IllegalStateException("No environment provided; please provide one");
            }
            AppConfigProviderBuilder providerBuilder = AppConfigProvider.builder()
                    .withApplication(application)
                    .withEnvironment(environment);
            if (client != null) {
                providerBuilder.withClient(client);
            }
            if (cacheManager != null) {
                providerBuilder.withCacheManager(cacheManager);
            }
            resolved = providerBuilder.build();
        }
        return new AppConfigStore(resolved, name, envelope, maxAge, maxAgeUnit);
    }
}
