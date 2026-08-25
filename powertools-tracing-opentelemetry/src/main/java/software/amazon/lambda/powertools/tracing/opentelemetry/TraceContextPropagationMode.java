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
 * How extracted remote contexts from batch event sources (for example SQS) are applied.
 *
 * <p>Configured with {@code POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE}.</p>
 */
public enum TraceContextPropagationMode {
    /**
     * Use the first valid remote context as the parent of the handler span.
     */
    PARENT,
    /**
     * Start a new trace and add remote contexts as span links. Preferred for batches.
     */
    LINK
}
