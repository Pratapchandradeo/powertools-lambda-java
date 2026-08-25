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

package software.amazon.lambda.powertools.streaming.internal;

import static software.amazon.lambda.powertools.common.internal.LambdaConstants.AWS_REGION_ENV;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Lazy singleton default {@link S3Client} used when the caller does not inject one.
 */
public final class DefaultS3ClientFactory {
    private static S3Client defaultClient;

    private DefaultS3ClientFactory() {
        // utility class
    }

    public static synchronized S3Client get() {
        if (defaultClient == null) {
            S3ClientBuilder builder = S3Client.builder()
                    .httpClient(UrlConnectionHttpClient.builder().build());
            String region = System.getenv(AWS_REGION_ENV);
            if (region != null && !region.isEmpty()) {
                builder.region(Region.of(region));
            }
            defaultClient = builder.build();
        }
        return defaultClient;
    }

    // Visible for tests
    static synchronized void reset() {
        defaultClient = null;
    }
}
