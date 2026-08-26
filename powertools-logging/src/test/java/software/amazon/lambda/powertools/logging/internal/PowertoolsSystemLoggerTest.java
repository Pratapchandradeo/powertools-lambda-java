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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.contentOf;
import static software.amazon.lambda.powertools.logging.argument.StructuredArguments.entry;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.test.TestLogger;

import software.amazon.lambda.powertools.logging.argument.StructuredArgument;

class PowertoolsSystemLoggerTest {

    private static final String LOGGER_NAME = PowertoolsSystemLoggerTest.class.getName();

    private TestLogger testLogger;
    private System.Logger systemLogger;

    @BeforeEach
    void setUp() throws IOException {
        testLogger = (TestLogger) LoggerFactory.getLogger(LOGGER_NAME);
        testLogger.setLogLevel("trace");
        testLogger.clearArguments();
        systemLogger = PowertoolsSystemLogger.getLogger(LOGGER_NAME);
        truncateLogFile();
    }

    @Test
    void getName_returnsLoggerName() {
        assertThat(systemLogger.getName()).isEqualTo(LOGGER_NAME);
    }

    @Test
    void getLogger_cachesByName() {
        assertThat(PowertoolsSystemLogger.getLogger(LOGGER_NAME)).isSameAs(systemLogger);
    }

    @Test
    void getLogger_rejectsNullName() {
        assertThatThrownBy(() -> PowertoolsSystemLogger.getLogger(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isLoggable_rejectsNullLevel() {
        assertThatThrownBy(() -> systemLogger.isLoggable(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isLoggable_offIsNeverLoggable() {
        assertThat(systemLogger.isLoggable(Level.OFF)).isFalse();
    }

    @Test
    void isLoggable_respectsDelegateLevel() {
        testLogger.setLogLevel("info");

        assertThat(systemLogger.isLoggable(Level.INFO)).isTrue();
        assertThat(systemLogger.isLoggable(Level.WARNING)).isTrue();
        assertThat(systemLogger.isLoggable(Level.ERROR)).isTrue();
        assertThat(systemLogger.isLoggable(Level.DEBUG)).isFalse();
        assertThat(systemLogger.isLoggable(Level.TRACE)).isFalse();
        assertThat(systemLogger.isLoggable(Level.ALL)).isFalse();
    }

    @Test
    void isLoggable_allMapsToTrace() {
        testLogger.setLogLevel("trace");

        assertThat(systemLogger.isLoggable(Level.ALL)).isTrue();
        assertThat(systemLogger.isLoggable(Level.TRACE)).isTrue();
    }

    @Test
    void log_plainMessage() {
        systemLogger.log(Level.INFO, "hello powertools");

        assertThat(contentOf(logFile())).contains("hello powertools");
    }

    @Test
    void log_formatsMessageFormatPlaceholders() {
        systemLogger.log(Level.INFO, "Processing order {0} for {1}", "order-1", "alice");

        assertThat(contentOf(logFile())).contains("Processing order order-1 for alice");
    }

    @Test
    void log_forwardsStructuredArgumentsSeparately() {
        StructuredArgument argument = entry("orderId", "order-1");

        systemLogger.log(Level.INFO, "Processing order {0}", "order-1", argument);

        assertThat(contentOf(logFile())).contains("Processing order order-1");
        assertThat(testLogger.getArguments()).containsExactly(argument);
    }

    @Test
    void log_structuredArgumentOnlyDoesNotFormatMessage() {
        StructuredArgument argument = entry("orderId", "order-1");

        systemLogger.log(Level.INFO, "Collecting payment", argument);

        assertThat(contentOf(logFile())).contains("Collecting payment");
        assertThat(testLogger.getArguments()).containsExactly(argument);
    }

    @Test
    void log_multipleStructuredArguments() {
        StructuredArgument order = entry("orderId", "order-1");
        StructuredArgument amount = entry("amount", 12.5);

        systemLogger.log(Level.INFO, "done", order, amount);

        assertThat(testLogger.getArguments()).containsExactly(order, amount);
    }

    @Test
    void log_nullParamsLogsPatternAsIs() {
        systemLogger.log(Level.INFO, "no params", (Object[]) null);

        assertThat(contentOf(logFile())).contains("no params");
        assertThat(testLogger.getArguments()).isNullOrEmpty();
    }

    @Test
    void log_nullMessageIsAccepted() {
        systemLogger.log(Level.INFO, (String) null);

        assertThat(contentOf(logFile())).contains("INFO");
    }

    @Test
    void log_invalidMessageFormatKeepsPattern() {
        systemLogger.log(Level.INFO, "broken { pattern", "ignored");

        assertThat(contentOf(logFile())).contains("broken { pattern");
    }

    @Test
    void log_disabledLevelDoesNotEmit() {
        testLogger.setLogLevel("error");

        systemLogger.log(Level.INFO, "should not appear");

        assertThat(contentOf(logFile())).doesNotContain("should not appear");
        assertThat(testLogger.getArguments()).isNull();
    }

    @Test
    void log_supplierNotInvokedWhenDisabled() {
        testLogger.setLogLevel("error");
        AtomicBoolean called = new AtomicBoolean(false);

        systemLogger.log(Level.DEBUG, () -> {
            called.set(true);
            return "expensive";
        });

        assertThat(called).isFalse();
        assertThat(contentOf(logFile())).doesNotContain("expensive");
    }

    @Test
    void log_supplierInvokedWhenEnabled() {
        AtomicBoolean called = new AtomicBoolean(false);

        systemLogger.log(Level.INFO, () -> {
            called.set(true);
            return "lazy message";
        });

        assertThat(called).isTrue();
        assertThat(contentOf(logFile())).contains("lazy message");
    }

    @Test
    void log_objectUsesToString() {
        systemLogger.log(Level.INFO, ListResourceBundle.class);

        assertThat(contentOf(logFile())).contains(ListResourceBundle.class.toString());
    }

    @Test
    void log_throwableIsForwarded() {
        RuntimeException error = new RuntimeException("boom");

        systemLogger.log(Level.ERROR, "failed", error);

        assertThat(contentOf(logFile())).contains("failed").contains("boom");
    }

    @Test
    void log_warningMapsToSlf4jWarn() {
        systemLogger.log(Level.WARNING, "be careful");

        assertThat(contentOf(logFile())).contains("WARN").contains("be careful");
    }

    @Test
    void log_resourceBundleLocalizesAndFormats() {
        ResourceBundle bundle = new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][] {
                        { "order.processed", "Processed {0}" }
                };
            }
        };

        systemLogger.log(Level.INFO, bundle, "order.processed", "order-1");

        assertThat(contentOf(logFile())).contains("Processed order-1");
    }

    @Test
    void log_missingResourceBundleKeyUsesRawKey() {
        ResourceBundle bundle = new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][] {
                        { "other.key", "unused" }
                };
            }
        };

        systemLogger.log(Level.INFO, bundle, "missing.key");

        assertThat(contentOf(logFile())).contains("missing.key");
    }

    @Test
    void log_offLevelDoesNotEmit() {
        systemLogger.log(Level.OFF, "never");

        assertThat(contentOf(logFile())).doesNotContain("never");
    }

    @Test
    void log_nullLevelRejected() {
        assertThatThrownBy(() -> systemLogger.log((Level) null, "msg"))
                .isInstanceOf(NullPointerException.class);
    }

    private static File logFile() {
        return new File("target/logfile.json");
    }

    private static void truncateLogFile() throws IOException {
        try {
            FileChannel.open(Paths.get("target/logfile.json"), StandardOpenOption.WRITE).truncate(0).close();
        } catch (NoSuchFileException e) {
            // first run
        }
    }
}
