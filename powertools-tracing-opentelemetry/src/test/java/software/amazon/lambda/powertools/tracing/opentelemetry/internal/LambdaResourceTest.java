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

package software.amazon.lambda.powertools.tracing.opentelemetry.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_PLATFORM;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_PROVIDER;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_REGION;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_MAX_MEMORY;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_NAME;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.SERVICE_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

class LambdaResourceTest {

    @Test
    @SetEnvironmentVariable(key = "AWS_REGION", value = "eu-west-1")
    @SetEnvironmentVariable(key = "AWS_LAMBDA_FUNCTION_NAME", value = "checkout")
    @SetEnvironmentVariable(key = "AWS_LAMBDA_FUNCTION_MEMORY_SIZE", value = "512")
    void copiesInitTimeSemanticAttributes() {
        Resource resource = LambdaResource.create();

        assertThat(resource.getAttribute(AttributeKey.stringKey(CLOUD_PROVIDER))).isEqualTo("aws");
        assertThat(resource.getAttribute(AttributeKey.stringKey(CLOUD_PLATFORM))).isEqualTo("aws_lambda");
        assertThat(resource.getAttribute(AttributeKey.stringKey(CLOUD_REGION))).isEqualTo("eu-west-1");
        assertThat(resource.getAttribute(AttributeKey.stringKey(FAAS_NAME))).isEqualTo("checkout");
        assertThat(resource.getAttribute(AttributeKey.longKey(FAAS_MAX_MEMORY))).isEqualTo(512L);
        assertThat(resource.getAttribute(AttributeKey.stringKey(SERVICE_NAME))).isNotBlank();
    }
}
