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

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.OpenTelemetryProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiGatewayTraceContextExtractorTest {
    private final ApiGatewayTraceContextExtractor extractor = new ApiGatewayTraceContextExtractor();

    @Test
    void supportsRestAndHttpApiEvents() {
        assertThat(extractor.supports(new APIGatewayProxyRequestEvent())).isTrue();
        assertThat(extractor.supports(new APIGatewayV2HTTPEvent())).isTrue();
        assertThat(extractor.supports("nope")).isFalse();
    }

    @Test
    void extractsParentFromCaseInsensitiveTraceparentHeader() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(Map.of(
                "Traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        ));

        ExtractedTraceContext extracted = extractor.extract(
                event, Context.root(), OpenTelemetryProvider.defaultPropagator());

        assertThat(extracted.spanKind()).isEqualTo(SpanKind.SERVER);
        assertThat(Span.fromContext(extracted.context()).getSpanContext().isValid()).isTrue();
        assertThat(Span.fromContext(extracted.context()).getSpanContext().getTraceId())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void enrichesHttpApiV2Span() {
        APIGatewayV2HTTPEvent.RequestContext.Http http = APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                .withMethod("POST")
                .withPath("/orders")
                .build();
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder().withHttp(http).build())
                .build();

        Span span = mock(Span.class);
        extractor.enrichSpan(event, span);

        verify(span).setAttribute("http.request.method", "POST");
        verify(span).setAttribute("url.path", "/orders");
    }
}
