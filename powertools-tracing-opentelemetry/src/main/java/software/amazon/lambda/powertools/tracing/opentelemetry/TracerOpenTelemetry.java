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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.crac.Core;
import org.crac.Resource;
import software.amazon.lambda.powertools.common.internal.ClassPreLoader;
import software.amazon.lambda.powertools.tracing.opentelemetry.context.TraceContextExtractorResolver;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.OpenTelemetryProvider;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.OpenTelemetryProvider.ResolvedOpenTelemetry;
import software.amazon.lambda.powertools.tracing.opentelemetry.internal.SpanScope;

/**
 * OpenTelemetry tracer for AWS Lambda with the same jobs as {@code TracingUtils},
 * using spans instead of X-Ray subsegments.
 *
 * <p>Default mode is {@link TracingMode#AUTO}: sit beside ADOT or a user-configured
 * global SDK. {@link TracingMode#MANUAL} builds a Lambda-optimized SDK when none
 * is registered, and flushes spans at the end of the handler.</p>
 */
public final class TracerOpenTelemetry implements Resource {
    private static final TracerOpenTelemetry INSTANCE = new TracerOpenTelemetry(false);
    private static volatile TracerOpenTelemetry defaultInstance;

    static {
        Core.getGlobalContext().register(INSTANCE);
    }

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final TextMapPropagator propagator;
    private final TraceContextExtractorResolver extractorResolver;
    private final TraceContextPropagationMode propagationMode;
    private final boolean ownsSdk;

    private TracerOpenTelemetry(boolean initialize) {
        if (!initialize) {
            // CRaC registration instance only
            this.openTelemetry = OpenTelemetry.noop();
            this.tracer = openTelemetry.getTracer("noop");
            this.propagator = OpenTelemetryProvider.defaultPropagator();
            this.extractorResolver = TraceContextExtractorResolver.create();
            this.propagationMode = TraceContextPropagationMode.PARENT;
            this.ownsSdk = false;
            return;
        }
        ResolvedOpenTelemetry resolved = OpenTelemetryProvider.resolve(OpenTelemetryProvider.resolveMode());
        this.openTelemetry = resolved.openTelemetry();
        this.tracer = OpenTelemetryProvider.tracer(openTelemetry);
        this.propagator = resolved.propagator();
        this.extractorResolver = TraceContextExtractorResolver.create();
        this.propagationMode = OpenTelemetryProvider.resolvePropagationMode();
        this.ownsSdk = resolved.ownsSdk();
    }

    private TracerOpenTelemetry(Builder builder) {
        this.openTelemetry = Objects.requireNonNull(builder.openTelemetry, "openTelemetry must not be null");
        this.tracer = builder.tracer == null ? OpenTelemetryProvider.tracer(openTelemetry) : builder.tracer;
        this.propagator = builder.propagator == null
                ? OpenTelemetryProvider.defaultPropagator()
                : builder.propagator;
        this.extractorResolver = builder.extractorResolver == null
                ? TraceContextExtractorResolver.create()
                : builder.extractorResolver;
        this.propagationMode = builder.propagationMode == null
                ? OpenTelemetryProvider.resolvePropagationMode()
                : builder.propagationMode;
        this.ownsSdk = builder.ownsSdk;
    }

    /**
     * Placeholder used so SnapStart / CRaC hooks run. Call from a handler constructor
     * or static initializer when using SnapStart.
     */
    public static void init() {
        getDefault();
    }

    public static TracerOpenTelemetry create() {
        return builder().build();
    }

    public static TracerOpenTelemetry create(TracingMode mode) {
        return builder().mode(mode).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Replaces the instance used by {@link TracingOpenTelemetry} and the static helpers.
     * Intended for tests and advanced setup.
     */
    public static synchronized void configure(TracerOpenTelemetry instance) {
        defaultInstance = Objects.requireNonNull(instance, "instance must not be null");
    }

    public static synchronized void reset() {
        defaultInstance = null;
    }

    public static synchronized TracerOpenTelemetry getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new TracerOpenTelemetry(true);
        }
        return defaultInstance;
    }

    public static Span currentSpan() {
        return Span.current();
    }

    public static SpanScope addSpan(String name) {
        return getDefault().startSpan(name);
    }

    public static SpanScope addSpan(String name, SpanKind kind) {
        return getDefault().startSpan(name, kind);
    }

    public static void runWithSpan(String name, Consumer<Span> consumer) {
        getDefault().runSpan(name, consumer);
    }

    public static <T> T withSpan(String name, Function<Span, T> function) {
        return getDefault().callSpan(name, function);
    }

    public static void flush() {
        getDefault().flushOwned();
    }

    public Tracer tracer() {
        return tracer;
    }

    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    public TextMapPropagator propagator() {
        return propagator;
    }

    public TraceContextExtractorResolver extractorResolver() {
        return extractorResolver;
    }

    public TraceContextPropagationMode propagationMode() {
        return propagationMode;
    }

    public boolean ownsSdk() {
        return ownsSdk;
    }

    public SpanScope startSpan(String name) {
        return startSpan(name, SpanKind.INTERNAL, Attributes.empty(), Context.current(), Collections.emptyList());
    }

    public SpanScope startSpan(String name, SpanKind kind) {
        return startSpan(name, kind, Attributes.empty(), Context.current(), Collections.emptyList());
    }

    public SpanScope startSpan(String name, SpanKind kind, Attributes attributes, Context parentContext,
                               List<SpanContext> links) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        Objects.requireNonNull(parentContext, "parentContext must not be null");
        Objects.requireNonNull(links, "links must not be null");

        SpanBuilder spanBuilder = tracer.spanBuilder(name)
                .setSpanKind(kind)
                .setParent(parentContext)
                .setAllAttributes(attributes);
        links.forEach(spanBuilder::addLink);
        return new SpanScope(spanBuilder.startSpan());
    }

    public void runSpan(String name, Consumer<Span> consumer) {
        callSpan(name, span -> {
            consumer.accept(span);
            return null;
        });
    }

    public <T> T callSpan(String name, Function<Span, T> function) {
        try (SpanScope scope = startSpan(name)) {
            try {
                return function.apply(scope.span());
            } catch (RuntimeException exception) {
                scope.recordException(exception);
                throw exception;
            }
        }
    }

    public <T> Context extract(T carrier, TextMapGetter<T> getter) {
        return extract(Context.current(), carrier, getter);
    }

    public <T> Context extract(Context context, T carrier, TextMapGetter<T> getter) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(carrier, "carrier must not be null");
        Objects.requireNonNull(getter, "getter must not be null");
        return propagator.extract(context, carrier, getter);
    }

    public <T> void inject(T carrier, TextMapSetter<T> setter) {
        inject(Context.current(), carrier, setter);
    }

    public <T> void inject(Context context, T carrier, TextMapSetter<T> setter) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(carrier, "carrier must not be null");
        Objects.requireNonNull(setter, "setter must not be null");
        propagator.inject(context, carrier, setter);
    }

    public void flushOwned() {
        OpenTelemetryProvider.flush(openTelemetry, ownsSdk);
    }

    @Override
    public void beforeCheckpoint(org.crac.Context<? extends Resource> context) {
        init();
        ClassPreLoader.preloadClasses();
    }

    @Override
    public void afterRestore(org.crac.Context<? extends Resource> context) {
        // no-op
    }

    public static final class Builder {
        private TracingMode mode = OpenTelemetryProvider.resolveMode();
        private OpenTelemetry openTelemetry;
        private Tracer tracer;
        private TextMapPropagator propagator;
        private TraceContextExtractorResolver extractorResolver;
        private TraceContextPropagationMode propagationMode;
        private boolean ownsSdk;

        public Builder mode(TracingMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode must not be null");
            return this;
        }

        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
            return this;
        }

        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        public Builder propagator(TextMapPropagator propagator) {
            this.propagator = propagator;
            return this;
        }

        public Builder extractorResolver(TraceContextExtractorResolver extractorResolver) {
            this.extractorResolver = extractorResolver;
            return this;
        }

        public Builder propagationMode(TraceContextPropagationMode propagationMode) {
            this.propagationMode = propagationMode;
            return this;
        }

        /**
         * Marks this instance as owning the SDK so {@link TracerOpenTelemetry#flush()}
         * will force-export remaining spans. Used when tests or callers supply an SDK.
         */
        public Builder ownsSdk(boolean ownsSdk) {
            this.ownsSdk = ownsSdk;
            return this;
        }

        public TracerOpenTelemetry build() {
            if (openTelemetry == null) {
                ResolvedOpenTelemetry resolved = OpenTelemetryProvider.resolve(mode);
                openTelemetry = resolved.openTelemetry();
                if (propagator == null) {
                    propagator = resolved.propagator();
                }
                ownsSdk = resolved.ownsSdk();
            }
            return new TracerOpenTelemetry(this);
        }
    }
}
