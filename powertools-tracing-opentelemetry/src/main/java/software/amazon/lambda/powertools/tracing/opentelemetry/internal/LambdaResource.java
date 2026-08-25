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

import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.AWS_LAMBDA_FUNCTION_MEMORY_SIZE;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.AWS_LAMBDA_FUNCTION_NAME;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.AWS_LAMBDA_FUNCTION_VERSION;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.AWS_LAMBDA_LOG_STREAM_NAME;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.AWS_REGION;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_PLATFORM;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_PROVIDER;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_REGION;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_INSTANCE;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_MAX_MEMORY;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_NAME;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_VERSION;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.SERVICE_NAME;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.TELEMETRY_DISTRO_NAME;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.TELEMETRY_DISTRO_VALUE;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.TELEMETRY_SDK_LANGUAGE;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.TELEMETRY_SDK_NAME;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import software.amazon.lambda.powertools.common.internal.LambdaConstants;
import software.amazon.lambda.powertools.common.internal.LambdaHandlerProcessor;
import software.amazon.lambda.powertools.common.internal.SystemWrapper;

/**
 * OpenTelemetry resource attributes available at function init time.
 *
 * <p>Invocation-specific values such as the function ARN and request id are set
 * on the handler span instead, because they are only available from
 * {@link com.amazonaws.services.lambda.runtime.Context}.</p>
 */
public final class LambdaResource {

    private LambdaResource() {
    }

    public static Resource create() {
        AttributesBuilder attributes = Attributes.builder();
        attributes.put(CLOUD_PROVIDER, "aws");
        attributes.put(CLOUD_PLATFORM, "aws_lambda");
        attributes.put(TELEMETRY_SDK_NAME, "opentelemetry");
        attributes.put(TELEMETRY_SDK_LANGUAGE, "java");
        attributes.put(TELEMETRY_DISTRO_NAME, TELEMETRY_DISTRO_VALUE);

        putIfPresent(attributes, CLOUD_REGION, SystemWrapper.getenv(AWS_REGION));

        String functionName = SystemWrapper.getenv(AWS_LAMBDA_FUNCTION_NAME);
        putIfPresent(attributes, FAAS_NAME, functionName);

        String serviceName = LambdaHandlerProcessor.serviceName();
        if (serviceName != null && !LambdaConstants.SERVICE_UNDEFINED.equals(serviceName)) {
            attributes.put(SERVICE_NAME, serviceName);
        } else if (functionName != null && !functionName.isBlank()) {
            attributes.put(SERVICE_NAME, functionName);
        }

        putIfPresent(attributes, FAAS_VERSION, SystemWrapper.getenv(AWS_LAMBDA_FUNCTION_VERSION));
        putIfPresent(attributes, FAAS_INSTANCE, SystemWrapper.getenv(AWS_LAMBDA_LOG_STREAM_NAME));

        String memory = SystemWrapper.getenv(AWS_LAMBDA_FUNCTION_MEMORY_SIZE);
        if (memory != null && !memory.isBlank()) {
            try {
                attributes.put(FAAS_MAX_MEMORY, Long.parseLong(memory));
            } catch (NumberFormatException ignored) {
                // Ignore malformed memory size values
            }
        }

        return Resource.create(attributes.build());
    }

    private static void putIfPresent(AttributesBuilder attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
