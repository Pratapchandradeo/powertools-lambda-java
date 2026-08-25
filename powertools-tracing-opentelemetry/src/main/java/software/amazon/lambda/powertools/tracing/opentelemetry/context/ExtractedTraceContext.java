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

package software.amazon.lambda.powertools.tracing.opentelemetry.context;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.List;

public final class ExtractedTraceContext {
    private final Context context;
    private final List<SpanContext> spanContexts;
    private final SpanKind spanKind;

    public ExtractedTraceContext(Context context, List<SpanContext> spanContexts, SpanKind spanKind) {
        this.context = context;
        this.spanContexts = spanContexts == null ? Collections.emptyList() : List.copyOf(spanContexts);
        this.spanKind = spanKind == null ? SpanKind.SERVER : spanKind;
    }

    public ExtractedTraceContext(Context context, List<SpanContext> spanContexts) {
        this(context, spanContexts, SpanKind.SERVER);
    }

    public Context context() {
        return context;
    }

    public List<SpanContext> spanContexts() {
        return spanContexts;
    }

    public SpanKind spanKind() {
        return spanKind;
    }
}
