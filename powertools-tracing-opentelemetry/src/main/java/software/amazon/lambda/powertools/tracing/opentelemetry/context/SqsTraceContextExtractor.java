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

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.MapTextMapGetter;

/**
 * Extracts remote context from SQS message attributes and records batch semantic attributes.
 */
public final class SqsTraceContextExtractor implements TraceContextExtractor {

    @Override
    public boolean supports(Object event) {
        return event instanceof SQSEvent;
    }

    @Override
    public ExtractedTraceContext extract(Object event, Context parentContext, TextMapPropagator propagator) {
        SQSEvent sqsEvent = (SQSEvent) event;
        if (sqsEvent.getRecords() == null || sqsEvent.getRecords().isEmpty()) {
            return new ExtractedTraceContext(parentContext, List.of(), SpanKind.CONSUMER);
        }

        List<SpanContext> spanContexts = new ArrayList<>();
        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            if (message == null || message.getMessageAttributes() == null) {
                continue;
            }
            Map<String, String> carrier = toCarrier(message.getMessageAttributes());
            if (carrier.isEmpty()) {
                continue;
            }
            Context extracted = propagator.extract(Context.root(), carrier, MapTextMapGetter.INSTANCE);
            SpanContext spanContext = Span.fromContext(extracted).getSpanContext();
            if (spanContext.isValid()) {
                spanContexts.add(spanContext);
            }
        }

        Context parent = spanContexts.isEmpty()
                ? parentContext
                : Context.root().with(Span.wrap(spanContexts.get(0)));
        return new ExtractedTraceContext(parent, spanContexts, SpanKind.CONSUMER);
    }

    @Override
    public void enrichSpan(Object event, Span span) {
        SQSEvent sqsEvent = (SQSEvent) event;
        if (sqsEvent.getRecords() == null || sqsEvent.getRecords().isEmpty()) {
            return;
        }
        span.setAttribute("messaging.system", "aws_sqs");
        span.setAttribute("messaging.operation.type", "process");
        span.setAttribute("messaging.batch.message_count", sqsEvent.getRecords().size());

        SQSEvent.SQSMessage message = sqsEvent.getRecords().get(0);
        if (message != null && message.getEventSourceArn() != null) {
            span.setAttribute("messaging.destination.name", queueName(message.getEventSourceArn()));
        }
    }

    private static Map<String, String> toCarrier(Map<String, SQSEvent.MessageAttribute> attributes) {
        Map<String, String> carrier = new HashMap<>();
        for (Map.Entry<String, SQSEvent.MessageAttribute> entry : attributes.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getStringValue() != null) {
                carrier.put(entry.getKey(), entry.getValue().getStringValue());
            }
        }
        return carrier;
    }

    private static String queueName(String arn) {
        int separator = arn.lastIndexOf(':');
        return separator >= 0 ? arn.substring(separator + 1) : arn;
    }
}
