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
 * How Powertools obtains an OpenTelemetry SDK.
 */
public enum TracingMode {
    /**
     * Use an already-configured global OpenTelemetry instance (ADOT Lambda layer,
     * Java agent, or a user-registered SDK). If none is present, tracing is a no-op.
     */
    AUTO,
    /**
     * Use a Powertools Lambda-optimized SDK (OTLP exporter, W3C + X-Ray propagation)
     * when no global instance is registered. If ADOT or the user already configured
     * OpenTelemetry, that instance is reused.
     */
    MANUAL
}
