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

package software.amazon.lambda.powertools.featureflags.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.lambda.powertools.common.internal.UserAgentConfigurator;

class FeatureFlagsUserAgentInterceptorTest {

    @Test
    void interceptorIsRegisteredWithAwsSdk() throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("software/amazon/awssdk/global/handlers/execution.interceptors")) {
            assertThat(in).isNotNull();
            String listing = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(listing).contains(FeatureFlagsUserAgentInterceptor.class.getName());
        }
    }

    @Test
    void interceptorClassIsLoadable() {
        assertThatNoException().isThrownBy(() -> Class.forName(
                FeatureFlagsUserAgentInterceptor.class.getName()));
    }

    @Test
    void shouldConfigureUserAgentWhenCreatingAwsSdkClient() {
        // WHEN creating an AWS SDK client, global interceptors are loaded.
        // We use S3 but any AWS SDK client will do.
        S3Client.builder().region(Region.US_EAST_1).build();
        // parameters-appconfig is also on the classpath, so force this module's feature token
        UserAgentConfigurator.configureUserAgent("feature-flags");

        assertThat(System.getProperty("sdk.ua.appId")).contains("PT/FEATURE-FLAGS/");
    }

    @Test
    void getUserAgentUsesFeatureFlagsToken() {
        assertThat(UserAgentConfigurator.getUserAgent("feature-flags"))
                .startsWith("PT/FEATURE-FLAGS/")
                .contains("PTENV/");
    }
}
