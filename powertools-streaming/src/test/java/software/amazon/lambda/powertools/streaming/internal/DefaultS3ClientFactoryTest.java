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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import software.amazon.awssdk.services.s3.S3Client;

class DefaultS3ClientFactoryTest {

    @AfterEach
    void resetFactory() {
        DefaultS3ClientFactory.reset();
    }

    @Test
    @SetEnvironmentVariable(key = "AWS_REGION", value = "eu-west-1")
    void getReturnsSameClientInstance() {
        S3Client first = DefaultS3ClientFactory.get();
        S3Client second = DefaultS3ClientFactory.get();

        assertThat(first).isNotNull();
        assertThat(second).isSameAs(first);
    }

    @Test
    @SetEnvironmentVariable(key = "AWS_REGION", value = "us-east-1")
    void resetCreatesNewClient() {
        S3Client first = DefaultS3ClientFactory.get();
        DefaultS3ClientFactory.reset();
        S3Client second = DefaultS3ClientFactory.get();

        assertThat(second).isNotNull().isNotSameAs(first);
    }
}
