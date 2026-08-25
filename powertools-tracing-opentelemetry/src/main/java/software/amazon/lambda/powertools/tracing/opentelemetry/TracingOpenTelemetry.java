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

package software.amazon.lambda.powertools.tracing.opentelemetry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables OpenTelemetry tracing for the annotated method.
 *
 * <p>Use this annotation instead of {@code software.amazon.lambda.powertools.tracing.Tracing}
 * when you want vendor-neutral OpenTelemetry spans rather than AWS X-Ray subsegments.
 * Do not apply both annotations to the same method.</p>
 *
 * <p>On a Lambda handler, a root/consumer span is created, cold start and invocation
 * attributes are recorded, and parent context is extracted from known event sources
 * (API Gateway, SQS). On other methods, an internal child span is created.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TracingOpenTelemetry {
    /**
     * Service name recorded on the span. When empty, {@code POWERTOOLS_SERVICE_NAME} is used.
     */
    String namespace() default "";

    /**
     * Span name. When empty, the annotated method name is used.
     */
    String spanName() default "";

    /**
     * Controls whether the method response and/or errors are captured as span data.
     */
    CaptureMode captureMode() default CaptureMode.ENVIRONMENT_VAR;
}
