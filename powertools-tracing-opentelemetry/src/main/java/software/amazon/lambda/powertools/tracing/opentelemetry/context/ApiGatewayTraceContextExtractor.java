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

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Collections;
import java.util.Map;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.MapTextMapGetter;

/**
 * Extracts W3C / X-Ray context from API Gateway REST (v1) and HTTP API (v2) events.
 */
public final class ApiGatewayTraceContextExtractor implements TraceContextExtractor {

    @Override
    public boolean supports(Object event) {
        return event instanceof APIGatewayProxyRequestEvent
                || event instanceof APIGatewayV2HTTPEvent;
    }

    @Override
    public ExtractedTraceContext extract(Object event, Context parentContext, TextMapPropagator propagator) {
        Map<String, String> headers = headers(event);
        Context extracted = propagator.extract(parentContext, headers, MapTextMapGetter.INSTANCE);
        return new ExtractedTraceContext(extracted, Collections.emptyList(), SpanKind.SERVER);
    }

    @Override
    public void enrichSpan(Object event, Span span) {
        if (event instanceof APIGatewayProxyRequestEvent) {
            APIGatewayProxyRequestEvent restEvent = (APIGatewayProxyRequestEvent) event;
            if (restEvent.getHttpMethod() != null) {
                span.setAttribute("http.request.method", restEvent.getHttpMethod());
            }
            if (restEvent.getPath() != null) {
                span.setAttribute("url.path", restEvent.getPath());
            }
            return;
        }
        if (event instanceof APIGatewayV2HTTPEvent) {
            APIGatewayV2HTTPEvent httpEvent = (APIGatewayV2HTTPEvent) event;
            if (httpEvent.getRequestContext() != null && httpEvent.getRequestContext().getHttp() != null) {
                String method = httpEvent.getRequestContext().getHttp().getMethod();
                String path = httpEvent.getRequestContext().getHttp().getPath();
                if (method != null) {
                    span.setAttribute("http.request.method", method);
                }
                if (path != null) {
                    span.setAttribute("url.path", path);
                }
            }
        }
    }

    private static Map<String, String> headers(Object event) {
        if (event instanceof APIGatewayProxyRequestEvent) {
            Map<String, String> headers = ((APIGatewayProxyRequestEvent) event).getHeaders();
            return headers == null ? Collections.emptyMap() : headers;
        }
        if (event instanceof APIGatewayV2HTTPEvent) {
            Map<String, String> headers = ((APIGatewayV2HTTPEvent) event).getHeaders();
            return headers == null ? Collections.emptyMap() : headers;
        }
        return Collections.emptyMap();
    }
}
