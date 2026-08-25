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

import static software.amazon.lambda.powertools.common.internal.LambdaHandlerProcessor.coldStartDone;
import static software.amazon.lambda.powertools.common.internal.LambdaHandlerProcessor.isColdStart;
import static software.amazon.lambda.powertools.common.internal.LambdaHandlerProcessor.isHandlerMethod;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.AWS_XRAY_TRACE_HEADER;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CAPTURE_ERROR_ENV;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CAPTURE_RESPONSE_ENV;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_ACCOUNT_ID;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.CLOUD_RESOURCE_ID;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_COLDSTART;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.FAAS_INVOCATION_ID;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.POWERTOOLS_SERVICE;
import static software.amazon.lambda.powertools.tracing.opentelemetry.internal.AttributesConstants.RESPONSE_ATTRIBUTE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import software.amazon.lambda.powertools.common.internal.LambdaConstants;
import software.amazon.lambda.powertools.common.internal.LambdaHandlerProcessor;
import software.amazon.lambda.powertools.common.internal.SystemWrapper;
import software.amazon.lambda.powertools.tracing.opentelemetry.CaptureMode;
import software.amazon.lambda.powertools.tracing.opentelemetry.TraceContextPropagationMode;
import software.amazon.lambda.powertools.tracing.opentelemetry.TracerOpenTelemetry;
import software.amazon.lambda.powertools.tracing.opentelemetry.TracingOpenTelemetry;
import software.amazon.lambda.powertools.tracing.opentelemetry.context.ExtractedTraceContext;

@Aspect
public final class TracingOpenTelemetryAspect {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @SuppressWarnings("EmptyMethod")
    @Pointcut("@annotation(tracingOpenTelemetry)")
    public void callAt(TracingOpenTelemetry tracingOpenTelemetry) {
    }

    @Around(value = "callAt(tracingOpenTelemetry) && execution(@TracingOpenTelemetry * *.*(..))",
            argNames = "pjp,tracingOpenTelemetry")
    public Object around(ProceedingJoinPoint pjp, TracingOpenTelemetry tracingOpenTelemetry) throws Throwable {
        TracerOpenTelemetry tracer = TracerOpenTelemetry.getDefault();
        String spanName = tracingOpenTelemetry.spanName().isEmpty()
                ? pjp.getSignature().getName()
                : tracingOpenTelemetry.spanName();

        if (isHandlerMethod(pjp)) {
            return traceHandler(pjp, tracingOpenTelemetry, tracer, spanName);
        }
        return traceMethod(pjp, tracingOpenTelemetry, tracer, spanName);
    }

    private Object traceHandler(ProceedingJoinPoint pjp, TracingOpenTelemetry annotation,
                                TracerOpenTelemetry tracer, String spanName) throws Throwable {
        ExtractedTraceContext extracted = extractHandlerContext(pjp, tracer);
        try (SpanScope scope = startHandlerSpan(tracer, spanName, extracted)) {
            Span span = scope.span();
            if (pjp.getArgs().length > 0) {
                tracer.extractorResolver().enrichSpan(pjp.getArgs()[0], span);
            }
            addHandlerAttributes(pjp, annotation, span);
            try {
                Object result = pjp.proceed(pjp.getArgs());
                captureResponse(span, annotation, result);
                coldStartDone();
                return result;
            } catch (Throwable throwable) {
                captureError(scope, annotation, throwable);
                throw throwable;
            }
        } finally {
            tracer.flushOwned();
        }
    }

    private Object traceMethod(ProceedingJoinPoint pjp, TracingOpenTelemetry annotation,
                               TracerOpenTelemetry tracer, String spanName) throws Throwable {
        try (SpanScope scope = tracer.startSpan(spanName, SpanKind.INTERNAL)) {
            try {
                Object result = pjp.proceed(pjp.getArgs());
                captureResponse(scope.span(), annotation, result);
                return result;
            } catch (Throwable throwable) {
                captureError(scope, annotation, throwable);
                throw throwable;
            }
        }
    }

    private SpanScope startHandlerSpan(TracerOpenTelemetry tracer, String spanName,
                                       ExtractedTraceContext extracted) {
        Attributes attributes = Attributes.builder()
                .put(FAAS_COLDSTART, isColdStart())
                .build();

        if (shouldUseLinks(tracer, extracted)) {
            return tracer.startSpan(
                    spanName,
                    extracted.spanKind(),
                    attributes,
                    io.opentelemetry.context.Context.current(),
                    extracted.spanContexts()
            );
        }
        return tracer.startSpan(
                spanName,
                extracted.spanKind(),
                attributes,
                extracted.context(),
                Collections.emptyList()
        );
    }

    private boolean shouldUseLinks(TracerOpenTelemetry tracer, ExtractedTraceContext extracted) {
        return tracer.propagationMode() == TraceContextPropagationMode.LINK
                && !extracted.spanContexts().isEmpty();
    }

    private ExtractedTraceContext extractHandlerContext(ProceedingJoinPoint pjp, TracerOpenTelemetry tracer) {
        Object event = pjp.getArgs().length > 0 ? pjp.getArgs()[0] : null;
        ExtractedTraceContext extracted = tracer.extractorResolver()
                .extract(event, io.opentelemetry.context.Context.current(), tracer.propagator());

        if (Span.fromContext(extracted.context()).getSpanContext().isValid()) {
            return extracted;
        }
        return extractFromXrayEnvironment(tracer, extracted);
    }

    private ExtractedTraceContext extractFromXrayEnvironment(TracerOpenTelemetry tracer,
                                                             ExtractedTraceContext fallback) {
        String header = SystemWrapper.getenv(LambdaConstants.X_AMZN_TRACE_ID);
        if (header == null || header.isBlank()) {
            return fallback;
        }
        TextMapPropagator propagator = tracer.propagator();
        io.opentelemetry.context.Context extracted = propagator.extract(
                io.opentelemetry.context.Context.current(),
                Map.of(AWS_XRAY_TRACE_HEADER, header),
                MapTextMapGetter.INSTANCE
        );
        return new ExtractedTraceContext(extracted, fallback.spanContexts(), fallback.spanKind());
    }

    private void addHandlerAttributes(ProceedingJoinPoint pjp, TracingOpenTelemetry annotation, Span span) {
        span.setAttribute(POWERTOOLS_SERVICE, namespace(annotation));
        Optional.ofNullable(LambdaHandlerProcessor.extractContext(pjp)).ifPresent(context -> {
            span.setAttribute(FAAS_INVOCATION_ID, context.getAwsRequestId());
            String arn = context.getInvokedFunctionArn();
            if (arn != null && !arn.isBlank()) {
                span.setAttribute(CLOUD_RESOURCE_ID, arn);
                String accountId = accountIdFromArn(arn);
                if (accountId != null) {
                    span.setAttribute(CLOUD_ACCOUNT_ID, accountId);
                }
            }
        });
    }

    private void captureResponse(Span span, TracingOpenTelemetry annotation, Object response) throws Exception {
        if (!isCaptureResponseEnabled(annotation) || response == null) {
            return;
        }
        span.setAttribute(RESPONSE_ATTRIBUTE, OBJECT_MAPPER.writeValueAsString(response));
    }

    private void captureError(SpanScope scope, TracingOpenTelemetry annotation, Throwable throwable) {
        if (isCaptureErrorEnabled(annotation)) {
            scope.recordException(throwable);
        }
    }

    private boolean isCaptureResponseEnabled(TracingOpenTelemetry annotation) {
        switch (annotation.captureMode()) {
            case ENVIRONMENT_VAR:
                return isEnvironmentVariableSet(CAPTURE_RESPONSE_ENV)
                        && environmentVariable(CAPTURE_RESPONSE_ENV);
            case RESPONSE:
            case RESPONSE_AND_ERROR:
                return true;
            case DISABLED:
            case ERROR:
            default:
                return false;
        }
    }

    private boolean isCaptureErrorEnabled(TracingOpenTelemetry annotation) {
        switch (annotation.captureMode()) {
            case ENVIRONMENT_VAR:
                return isEnvironmentVariableSet(CAPTURE_ERROR_ENV)
                        && environmentVariable(CAPTURE_ERROR_ENV);
            case ERROR:
            case RESPONSE_AND_ERROR:
                return true;
            case DISABLED:
            case RESPONSE:
            default:
                return false;
        }
    }

    private String namespace(TracingOpenTelemetry annotation) {
        return annotation.namespace().isEmpty()
                ? LambdaHandlerProcessor.serviceName()
                : annotation.namespace();
    }

    private boolean environmentVariable(String key) {
        return Boolean.parseBoolean(SystemWrapper.getenv(key));
    }

    private boolean isEnvironmentVariableSet(String key) {
        return SystemWrapper.containsKey(key);
    }

    private static String accountIdFromArn(String arn) {
        String[] parts = arn.split(":");
        return parts.length > 4 ? parts[4] : null;
    }
}
