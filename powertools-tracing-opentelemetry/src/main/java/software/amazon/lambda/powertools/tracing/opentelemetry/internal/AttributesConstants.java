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

public final class AttributesConstants {

    private AttributesConstants() {
    }

    public static final String AWS_LAMBDA_FUNCTION_NAME = "AWS_LAMBDA_FUNCTION_NAME";
    public static final String AWS_LAMBDA_FUNCTION_VERSION = "AWS_LAMBDA_FUNCTION_VERSION";
    public static final String AWS_LAMBDA_FUNCTION_MEMORY_SIZE = "AWS_LAMBDA_FUNCTION_MEMORY_SIZE";
    public static final String AWS_LAMBDA_LOG_STREAM_NAME = "AWS_LAMBDA_LOG_STREAM_NAME";
    public static final String AWS_REGION = "AWS_REGION";
    public static final String AWS_XRAY_TRACE_HEADER = "X-Amzn-Trace-Id";

    public static final String POWERTOOLS_OTEL_TRACING_MODE = "POWERTOOLS_OTEL_TRACING_MODE";
    public static final String POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE =
            "POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE";
    public static final String CAPTURE_RESPONSE_ENV = "POWERTOOLS_TRACER_CAPTURE_RESPONSE";
    public static final String CAPTURE_ERROR_ENV = "POWERTOOLS_TRACER_CAPTURE_ERROR";
    public static final String OTEL_EXPORTER_OTLP_TRACES_PROTOCOL = "OTEL_EXPORTER_OTLP_TRACES_PROTOCOL";

    public static final String CLOUD_PROVIDER = "cloud.provider";
    public static final String CLOUD_PLATFORM = "cloud.platform";
    public static final String CLOUD_REGION = "cloud.region";
    public static final String CLOUD_ACCOUNT_ID = "cloud.account.id";
    public static final String CLOUD_RESOURCE_ID = "cloud.resource_id";
    public static final String SERVICE_NAME = "service.name";
    public static final String FAAS_NAME = "faas.name";
    public static final String FAAS_VERSION = "faas.version";
    public static final String FAAS_INSTANCE = "faas.instance";
    public static final String FAAS_MAX_MEMORY = "faas.max_memory";
    public static final String FAAS_COLDSTART = "faas.coldstart";
    public static final String FAAS_INVOCATION_ID = "faas.invocation_id";
    public static final String RESPONSE_ATTRIBUTE = "aws.lambda.powertools.response";
    public static final String POWERTOOLS_SERVICE = "powertools.service";

    public static final String TELEMETRY_SDK_NAME = "telemetry.sdk.name";
    public static final String TELEMETRY_SDK_LANGUAGE = "telemetry.sdk.language";
    public static final String TELEMETRY_DISTRO_NAME = "telemetry.distro.name";
    public static final String TELEMETRY_DISTRO_VALUE = "powertools-for-aws-lambda";

    public static final String INSTRUMENTATION_NAME = "aws-lambda-powertools";
}
