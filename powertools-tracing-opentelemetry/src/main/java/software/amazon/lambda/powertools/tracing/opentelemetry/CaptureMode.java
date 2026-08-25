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

/**
 * Controls whether method responses and errors are captured as OpenTelemetry span attributes.
 *
 * <p>Same knobs as the X-Ray tracing utility, kept in this module so OpenTelemetry users
 * do not need a dependency on {@code powertools-tracing}.</p>
 */
public enum CaptureMode {
    /**
     * Capture only the method response.
     */
    RESPONSE,
    /**
     * Capture only the method error.
     */
    ERROR,
    /**
     * Capture both response and error.
     */
    RESPONSE_AND_ERROR,
    /**
     * Capture neither response nor error.
     */
    DISABLED,
    /**
     * Honor {@code POWERTOOLS_TRACER_CAPTURE_RESPONSE} and {@code POWERTOOLS_TRACER_CAPTURE_ERROR}.
     */
    ENVIRONMENT_VAR
}
