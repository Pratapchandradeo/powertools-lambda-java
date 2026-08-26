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
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.crac.Context;
import org.crac.Resource;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.featureflags.store.InMemoryStore;

class FeatureFlagsCracTest {

    @Test
    void beforeCheckpointDoesNotThrow() {
        FeatureFlags flags = FeatureFlags.builder()
                .withStore(InMemoryStore.fromJson("{\"ok\": { \"default\": true }}"))
                .build();

        @SuppressWarnings("unchecked")
        Context<Resource> context = mock(Context.class);
        assertThatNoException().isThrownBy(() -> flags.beforeCheckpoint(context));
    }

    @Test
    void afterRestoreDoesNotThrow() {
        FeatureFlags flags = FeatureFlags.builder()
                .withStore(InMemoryStore.fromJson("{\"ok\": { \"default\": true }}"))
                .build();

        @SuppressWarnings("unchecked")
        Context<Resource> context = mock(Context.class);
        assertThatNoException().isThrownBy(() -> flags.afterRestore(context));
    }

    @Test
    void evaluateStillWorksAfterPriming() {
        FeatureFlags flags = FeatureFlags.builder()
                .withStore(InMemoryStore.fromJson("{"
                        + "\"premium_features\": {"
                        + "  \"default\": false,"
                        + "  \"rules\": {"
                        + "    \"tier\": {"
                        + "      \"when_match\": true,"
                        + "      \"conditions\": ["
                        + "        { \"action\": \"EQUALS\", \"key\": \"tier\", \"value\": \"premium\" }"
                        + "      ]"
                        + "    }"
                        + "  }"
                        + "}"
                        + "}"))
                .build();

        @SuppressWarnings("unchecked")
        Context<Resource> context = mock(Context.class);
        flags.beforeCheckpoint(context);

        assertThat(flags.evaluate("premium_features", Map.of("tier", "premium"), false)).isTrue();
    }
}
