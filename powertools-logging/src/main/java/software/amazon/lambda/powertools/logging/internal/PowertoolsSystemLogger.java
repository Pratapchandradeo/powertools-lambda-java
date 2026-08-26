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

package software.amazon.lambda.powertools.logging.internal;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import software.amazon.lambda.powertools.logging.argument.StructuredArgument;

/**
 * {@link System.Logger} adapter that forwards to SLF4J so {@code System.getLogger}
 * emits through the same Powertools backends (Log4j2 / Logback), MDC context,
 * and {@link StructuredArgument} rendering as {@code LoggerFactory.getLogger}.
 *
 * <p>{@code System.Logger} uses {@link MessageFormat} placeholders ({@code {0}}).
 * {@link StructuredArgument} instances in the parameter list are not used for
 * formatting; they are forwarded to SLF4J so JSON layouts can serialize them.
 */
public final class PowertoolsSystemLogger implements System.Logger {

    private static final ConcurrentMap<String, PowertoolsSystemLogger> LOGGERS = new ConcurrentHashMap<>();

    private final String name;
    private final org.slf4j.Logger delegate;

    private PowertoolsSystemLogger(String name) {
        this.name = name;
        this.delegate = LoggerFactory.getLogger(name);
    }

    static PowertoolsSystemLogger getLogger(String name) {
        Objects.requireNonNull(name, "name");
        return LOGGERS.computeIfAbsent(name, PowertoolsSystemLogger::new);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isLoggable(Level level) {
        Objects.requireNonNull(level, "level");
        if (level == Level.OFF) {
            return false;
        }
        return delegate.isEnabledForLevel(toSlf4jLevel(level));
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        Objects.requireNonNull(level, "level");
        if (!isLoggable(level)) {
            return;
        }
        logToSlf4j(level, localize(bundle, msg), thrown, List.of());
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        Objects.requireNonNull(level, "level");
        if (!isLoggable(level)) {
            return;
        }

        String pattern = localize(bundle, format);
        List<Object> formatArgs = new ArrayList<>();
        List<StructuredArgument> structuredArguments = new ArrayList<>();
        splitParameters(params, formatArgs, structuredArguments);

        String message = formatMessage(pattern, formatArgs);
        logToSlf4j(level, message, null, structuredArguments);
    }

    private void logToSlf4j(Level level, String message, Throwable thrown,
            List<StructuredArgument> structuredArguments) {
        LoggingEventBuilder builder = delegate.atLevel(toSlf4jLevel(level));
        if (thrown != null) {
            builder = builder.setCause(thrown);
        }
        for (StructuredArgument argument : structuredArguments) {
            builder = builder.addArgument(argument);
        }
        builder.log(message);
    }

    private static void splitParameters(Object[] params, List<Object> formatArgs,
            List<StructuredArgument> structuredArguments) {
        if (params == null) {
            return;
        }
        for (Object param : params) {
            if (param instanceof StructuredArgument) {
                structuredArguments.add((StructuredArgument) param);
            } else {
                formatArgs.add(param);
            }
        }
    }

    private static String localize(ResourceBundle bundle, String key) {
        if (bundle == null || key == null) {
            return key;
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    private static String formatMessage(String pattern, List<Object> formatArgs) {
        if (pattern == null || formatArgs.isEmpty()) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, formatArgs.toArray());
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    private static org.slf4j.event.Level toSlf4jLevel(Level level) {
        switch (level) {
            case ALL:
            case TRACE:
                return org.slf4j.event.Level.TRACE;
            case DEBUG:
                return org.slf4j.event.Level.DEBUG;
            case INFO:
                return org.slf4j.event.Level.INFO;
            case WARNING:
                return org.slf4j.event.Level.WARN;
            case ERROR:
            case OFF:
                return org.slf4j.event.Level.ERROR;
            default:
                return org.slf4j.event.Level.INFO;
        }
    }
}
