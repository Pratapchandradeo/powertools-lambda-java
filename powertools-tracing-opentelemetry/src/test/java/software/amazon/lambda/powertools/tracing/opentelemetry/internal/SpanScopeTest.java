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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpanScopeTest {

    @Test
    void recordsExceptionAndSetsErrorStatus() {
        Span span = mock(Span.class);
        Scope scope = mock(Scope.class);
        when(span.makeCurrent()).thenReturn(scope);

        SpanScope spanScope = new SpanScope(span);
        RuntimeException error = new RuntimeException("fail");
        spanScope.recordException(error);

        assertThat(spanScope.span()).isSameAs(span);
        verify(span).recordException(error);
        verify(span).setStatus(StatusCode.ERROR);
    }

    @Test
    void closeEndsScopeAndSpan() {
        Span span = mock(Span.class);
        Scope scope = mock(Scope.class);
        when(span.makeCurrent()).thenReturn(scope);

        new SpanScope(span).close();

        verify(scope).close();
        verify(span).end();
    }
}
