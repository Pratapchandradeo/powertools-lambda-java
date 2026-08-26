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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;
import software.amazon.lambda.powertools.featureflags.FeatureFlags;
import software.amazon.lambda.powertools.featureflags.exception.ConfigurationStoreException;
import software.amazon.lambda.powertools.parameters.appconfig.AppConfigProvider;
import software.amazon.lambda.powertools.parameters.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class AppConfigStoreTest {

    private static final String APPLICATION = "product-catalogue";
    private static final String ENVIRONMENT = "dev";
    private static final String PROFILE = "features";
    private static final String PREMIUM_SCHEMA = "{"
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

    @Mock
    AppConfigDataClient client;

    @Test
    void fetchesAndParsesFeatureDocument() {
        stubConfiguration(PREMIUM_SCHEMA);

        Map<String, Object> config = store().getConfiguration();

        assertThat(config).containsKeys("premium_features", "ten_percent_off_campaign");
        ArgumentCaptor<StartConfigurationSessionRequest> sessionCaptor =
                ArgumentCaptor.forClass(StartConfigurationSessionRequest.class);
        verify(client).startConfigurationSession(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().applicationIdentifier()).isEqualTo(APPLICATION);
        assertThat(sessionCaptor.getValue().environmentIdentifier()).isEqualTo(ENVIRONMENT);
        assertThat(sessionCaptor.getValue().configurationProfileIdentifier()).isEqualTo(PROFILE);
    }

    @Test
    void usesParameterCacheOnRepeatedFetch() {
        stubConfiguration(PREMIUM_SCHEMA);
        AppConfigStore store = store();

        store.getConfiguration();
        store.getConfiguration();

        verify(client, times(1)).getLatestConfiguration(any(GetLatestConfigurationRequest.class));
    }

    @Test
    void unwrapsEnvelope() {
        stubConfiguration("{"
                + "\"meta\": { \"version\": 1 },"
                + "\"features\": " + PREMIUM_SCHEMA
                + "}");

        Map<String, Object> config = AppConfigStore.builder()
                .withClient(client)
                .withApplication(APPLICATION)
                .withEnvironment(ENVIRONMENT)
                .withName(PROFILE)
                .withEnvelope("features")
                .withCacheManager(new CacheManager())
                .build()
                .getConfiguration();

        assertThat(config).containsKeys("premium_features", "ten_percent_off_campaign");
        assertThat(config).doesNotContainKey("meta");
    }

    @Test
    void unwrapsNestedEnvelope() {
        stubConfiguration("{\"config\": {\"flags\": " + PREMIUM_SCHEMA + "}}");

        Map<String, Object> config = AppConfigStore.builder()
                .withClient(client)
                .withApplication(APPLICATION)
                .withEnvironment(ENVIRONMENT)
                .withName(PROFILE)
                .withEnvelope("config.flags")
                .withCacheManager(new CacheManager())
                .build()
                .getConfiguration();

        assertThat(config).containsKey("premium_features");
    }

    @Test
    void missingEnvelopeThrows() {
        stubConfiguration("{\"other\": {}}");

        AppConfigStore store = AppConfigStore.builder()
                .withClient(client)
                .withApplication(APPLICATION)
                .withEnvironment(ENVIRONMENT)
                .withName(PROFILE)
                .withEnvelope("features")
                .withCacheManager(new CacheManager())
                .build();

        assertThatThrownBy(store::getConfiguration)
                .isInstanceOf(ConfigurationStoreException.class)
                .hasMessageContaining("Envelope");
    }

    @Test
    void invalidEnvelopeExpressionThrows() {
        stubConfiguration(PREMIUM_SCHEMA);

        AppConfigStore store = AppConfigStore.builder()
                .withClient(client)
                .withApplication(APPLICATION)
                .withEnvironment(ENVIRONMENT)
                .withName(PROFILE)
                .withEnvelope("[")
                .withCacheManager(new CacheManager())
                .build();

        assertThatThrownBy(store::getConfiguration)
                .isInstanceOf(ConfigurationStoreException.class)
                .hasMessageContaining("envelope");
    }

    @Test
    void emptyConfigurationThrows() {
        stubConfiguration("");

        assertThatThrownBy(() -> store().getConfiguration())
                .isInstanceOf(ConfigurationStoreException.class);
    }

    @Test
    void invalidJsonThrows() {
        stubConfiguration("{not-json");

        assertThatThrownBy(() -> store().getConfiguration())
                .isInstanceOf(ConfigurationStoreException.class)
                .hasMessageContaining("parse");
    }

    @Test
    void appConfigClientFailureIsWrapped() {
        when(client.startConfigurationSession(any(StartConfigurationSessionRequest.class)))
                .thenReturn(StartConfigurationSessionResponse.builder()
                        .initialConfigurationToken("token-1")
                        .build());
        when(client.getLatestConfiguration(any(GetLatestConfigurationRequest.class)))
                .thenThrow(new RuntimeException("throttled"));

        assertThatThrownBy(() -> store().getConfiguration())
                .isInstanceOf(ConfigurationStoreException.class)
                .hasMessageContaining(PROFILE)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void injectedProviderIsUsed() {
        AppConfigProvider provider = AppConfigProvider.builder()
                .withClient(client)
                .withApplication(APPLICATION)
                .withEnvironment(ENVIRONMENT)
                .withCacheManager(new CacheManager())
                .build();
        stubConfiguration(PREMIUM_SCHEMA);

        Map<String, Object> config = AppConfigStore.builder()
                .withProvider(provider)
                .withName(PROFILE)
                .build()
                .getConfiguration();

        assertThat(config).containsKey("premium_features");
    }

    @Test
    void evaluateWorksThroughAppConfigStore() {
        stubConfiguration(PREMIUM_SCHEMA);
        FeatureFlags flags = FeatureFlags.builder().withStore(store()).build();

        assertThat(flags.evaluate("premium_features", Map.of("tier", "premium"), false)).isTrue();
        assertThat(flags.evaluate("premium_features", Map.of("tier", "standard"), false)).isFalse();
        assertThat(flags.getEnabledFeatures(Map.of("tier", "premium")))
                .containsExactly("premium_features", "ten_percent_off_campaign");
    }

    @Test
    void maxAgeIsAppliedOnFetch() {
        stubConfiguration(PREMIUM_SCHEMA);

        AppConfigStore store = AppConfigStore.builder()
                .withClient(client)
                .withApplication(APPLICATION)
                .withEnvironment(ENVIRONMENT)
                .withName(PROFILE)
                .withMaxAge(10, ChronoUnit.MINUTES)
                .withCacheManager(new CacheManager())
                .build();

        assertThat(store.getConfiguration()).containsKey("premium_features");
        verify(client).getLatestConfiguration(any(GetLatestConfigurationRequest.class));
    }

    @Test
    void builderRequiresName() {
        assertThatThrownBy(() -> AppConfigStore.builder()
                .withApplication(APPLICATION)
                .withEnvironment(ENVIRONMENT)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name");
    }

    @Test
    void builderRequiresApplicationWhenProviderMissing() {
        assertThatThrownBy(() -> AppConfigStore.builder()
                .withEnvironment(ENVIRONMENT)
                .withName(PROFILE)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("application");
    }

    @Test
    void builderRequiresEnvironmentWhenProviderMissing() {
        assertThatThrownBy(() -> AppConfigStore.builder()
                .withApplication(APPLICATION)
                .withName(PROFILE)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("environment");
    }

    private AppConfigStore store() {
        return AppConfigStore.builder()
                .withClient(client)
                .withApplication(APPLICATION)
                .withEnvironment(ENVIRONMENT)
                .withName(PROFILE)
                .withCacheManager(new CacheManager())
                .build();
    }

    private void stubConfiguration(String body) {
        when(client.startConfigurationSession(any(StartConfigurationSessionRequest.class)))
                .thenReturn(StartConfigurationSessionResponse.builder()
                        .initialConfigurationToken("token-1")
                        .build());
        GetLatestConfigurationResponse.Builder builder = GetLatestConfigurationResponse.builder()
                .nextPollConfigurationToken("token-2");
        if (body != null) {
            builder.configuration(SdkBytes.fromUtf8String(body));
        }
        when(client.getLatestConfiguration(any(GetLatestConfigurationRequest.class)))
                .thenReturn(builder.build());
    }
}
