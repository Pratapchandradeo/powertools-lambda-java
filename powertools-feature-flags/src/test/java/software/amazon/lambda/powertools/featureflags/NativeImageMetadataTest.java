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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NativeImageMetadataTest {

    @Test
    void reflectConfigListsModuleClasses() throws IOException {
        String json = readResource(
                "META-INF/native-image/software.amazon.lambda/powertools-feature-flags/reflect-config.json");

        assertThat(json)
                .contains("software.amazon.lambda.powertools.featureflags.FeatureFlags")
                .contains("software.amazon.lambda.powertools.featureflags.internal.FeatureFlagsUserAgentInterceptor")
                .contains("software.amazon.lambda.powertools.featureflags.store.AppConfigStore")
                .contains("software.amazon.lambda.powertools.featureflags.store.InMemoryStore");
    }

    @Test
    void resourceConfigIncludesInterceptorAndClassesLoaded() throws IOException {
        String json = readResource(
                "META-INF/native-image/software.amazon.lambda/powertools-feature-flags/resource-config.json");

        assertThat(json)
                .contains("execution.interceptors")
                .contains("classesloaded.txt");
    }

    @Test
    void classesLoadedListIncludesFeatureFlags() throws IOException {
        String listing = readResource("classesloaded.txt");

        assertThat(listing)
                .contains("software.amazon.lambda.powertools.featureflags.FeatureFlags")
                .contains("software.amazon.lambda.powertools.featureflags.store.AppConfigStore")
                .contains("software.amazon.lambda.powertools.featureflags.internal.FeatureFlagsUserAgentInterceptor");
    }

    @Test
    void jniConfigIsPresent() {
        assertThat(Thread.currentThread().getContextClassLoader().getResource(
                "META-INF/native-image/software.amazon.lambda/powertools-feature-flags/jni-config.json"))
                .isNotNull();
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
