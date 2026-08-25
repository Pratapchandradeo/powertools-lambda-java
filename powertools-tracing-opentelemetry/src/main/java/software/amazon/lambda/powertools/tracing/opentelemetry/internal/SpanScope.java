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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

/**
 * Makes a span current and ends it when closed.
 */
public final class SpanScope implements AutoCloseable {
    private final Span span;
    private final Scope scope;

    public SpanScope(Span span) {
        this.span = span;
        this.scope = span.makeCurrent();
    }

    public Span span() {
        return span;
    }

    public void recordException(Throwable throwable) {
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
