---
title: Tracing (OpenTelemetry)
description: Core utility
---

The OpenTelemetry tracing utility is an optional, vendor-neutral alternative to the [X-Ray Tracing utility](tracing.md).
It creates OpenTelemetry spans for Lambda handlers and methods, captures cold start and invocation attributes,
and can export via ADOT or a Powertools-configured OTLP exporter.

**Key Features**

* Same jobs as X-Ray tracing: handler span, method spans, response/error capture, cold start
* `AUTO` mode sits beside the [ADOT Lambda layer](https://aws-otel.github.io/docs/getting-started/lambda) or any pre-configured global SDK
* `MANUAL` mode builds a Lambda-optimized SDK and flushes spans at the end of the handler
* W3C Trace Context and AWS X-Ray propagation
* Parent extraction from API Gateway and SQS events
* GraalVM and SnapStart priming support

<!-- prettier-ignore -->
!!! warning "Do not combine with `@Tracing`"
    `@TracingOpenTelemetry` and the X-Ray `@Tracing` annotation are separate utilities.
    Applying both to the same handler creates two traces. Pick one.

## Install

=== "Maven"

    ```xml hl_lines="3-7 25-28"
    <dependencies>
        ...
        <dependency>
            <groupId>software.amazon.lambda</groupId>
            <artifactId>powertools-tracing-opentelemetry</artifactId>
            <version>{{ powertools.version }}</version>
        </dependency>
        ...
    </dependencies>
    ...
    <!-- configure the aspectj-maven-plugin to compile-time weave (CTW) the aspect into your project -->
    <!-- Note: This AspectJ configuration is not needed when using the functional approach -->
    <build>
        <plugins>
            ...
            <plugin>
                 <groupId>dev.aspectj</groupId>
                 <artifactId>aspectj-maven-plugin</artifactId>
                 <version>1.14</version>
                 <configuration>
                     <source>11</source> <!-- or higher -->
                     <target>11</target> <!-- or higher -->
                     <complianceLevel>11</complianceLevel> <!-- or higher -->
                     <aspectLibraries>
                         <aspectLibrary>
                             <groupId>software.amazon.lambda</groupId>
                             <artifactId>powertools-tracing-opentelemetry</artifactId>
                         </aspectLibrary>
                     </aspectLibraries>
                 </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.aspectj</groupId>
                        <artifactId>aspectjtools</artifactId>
                        <version>1.9.22</version>
                    </dependency>
                </dependencies>
                 <executions>
                     <execution>
                         <goals>
                             <goal>compile</goal>
                         </goals>
                     </execution>
                 </executions>
            </plugin>
            ...
        </plugins>
    </build>
    ```

=== "Gradle"

    ```groovy hl_lines="3 11 12"
        plugins {
            id 'java'
            id 'io.freefair.aspectj.post-compile-weaving' version '8.1.0' // Not needed when using the functional approach
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            aspect 'software.amazon.lambda:powertools-tracing-opentelemetry:{{ powertools.version }}' // Not needed when using the functional approach
            implementation 'software.amazon.lambda:powertools-tracing-opentelemetry:{{ powertools.version }}' // Use this instead of 'aspect' when using the functional approach
        }
    ```

## Modes

| Mode | When to use | SDK |
|---|---|---|
| `AUTO` (default) | ADOT layer, Java agent, or your own `GlobalOpenTelemetry` | Never created by Powertools. No-op if none is registered |
| `MANUAL` | No ADOT / no global SDK | Powertools creates an OTLP SDK and force-flushes at the end of the handler |

Set the default with `POWERTOOLS_OTEL_TRACING_MODE=auto|manual`, or build an instance:

```java
TracerOpenTelemetry tracer = TracerOpenTelemetry.create(TracingMode.MANUAL);
TracerOpenTelemetry.configure(tracer);
```

<!-- prettier-ignore -->
!!! note "ADOT"
    Powertools does not ship a collector. Attach the [ADOT Lambda layer](https://aws-otel.github.io/docs/getting-started/lambda)
    (or point `OTEL_EXPORTER_OTLP_ENDPOINT` at a collector) yourself.

## Lambda handler

=== "@TracingOpenTelemetry annotation"

    ```java hl_lines="3 10"
    public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

        @TracingOpenTelemetry
        public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
            businessLogic();
            return response();
        }

        @TracingOpenTelemetry
        public void businessLogic() {
        }
    }
    ```

=== "Functional API"

    ```java hl_lines="1 6 7 8"
    import software.amazon.lambda.powertools.tracing.opentelemetry.TracerOpenTelemetry;

    public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

        public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
            return TracerOpenTelemetry.withSpan("handleRequest", span -> {
                span.setAttribute("order.id", "123");
                return response();
            });
        }
    }
    ```

On a handler method the utility:

* Creates a span (kind `SERVER`, or `CONSUMER` for SQS)
* Sets `faas.coldstart`, `faas.invocation_id`, `cloud.resource_id` (function ARN), and `powertools.service`
* Extracts parent context from API Gateway headers or SQS message attributes
* Force-flushes if it owns the SDK (`MANUAL` mode)

## Capture response and errors

Same environment variables as X-Ray tracing:

* `POWERTOOLS_TRACER_CAPTURE_RESPONSE`
* `POWERTOOLS_TRACER_CAPTURE_ERROR`

Or set `captureMode` on the annotation (`RESPONSE`, `ERROR`, `RESPONSE_AND_ERROR`, `DISABLED`).

## Context propagation

The default propagator is W3C Trace Context + the AWS X-Ray propagator.

For SQS batches, `POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE` selects:

* `PARENT` (default) — first valid remote context becomes the parent
* `LINK` — start a new trace and add remote contexts as span links

Use `tracer.extract(...)` / `tracer.inject(...)` to propagate context across messaging boundaries.

## Migration from X-Ray tracing

| X-Ray | OpenTelemetry |
|---|---|
| `@Tracing` | `@TracingOpenTelemetry` |
| `TracingUtils.withSubsegment(name, ...)` | `TracerOpenTelemetry.withSpan(name, ...)` |
| `putAnnotation` / `putMetadata` | `span.setAttribute(...)` |
| Cold start annotation `ColdStart` | Attribute `faas.coldstart` |
| `powertools-tracing` | `powertools-tracing-opentelemetry` |

## SnapStart priming

Call `TracerOpenTelemetry.init()` from a handler constructor or static initializer so CRaC hooks register before the snapshot.

## Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `POWERTOOLS_OTEL_TRACING_MODE` | `auto` or `manual` | `auto` |
| `POWERTOOLS_SERVICE_NAME` | Service name | `service_undefined` |
| `POWERTOOLS_TRACER_CAPTURE_RESPONSE` | Capture method return value | unset (off) |
| `POWERTOOLS_TRACER_CAPTURE_ERROR` | Record exceptions | unset (off) |
| `POWERTOOLS_TRACE_CONTEXT_PROPAGATION_MODE` | `PARENT` or `LINK` | `PARENT` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP collector (MANUAL) | SDK default |
| `OTEL_EXPORTER_OTLP_TRACES_PROTOCOL` | `grpc` or `http/protobuf` | `grpc` |
