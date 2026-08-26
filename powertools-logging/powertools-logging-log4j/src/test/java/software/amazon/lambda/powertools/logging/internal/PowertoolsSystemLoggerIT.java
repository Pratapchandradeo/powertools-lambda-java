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

import static org.apache.commons.lang3.reflect.FieldUtils.writeStaticField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.amazonaws.services.lambda.runtime.Context;

import software.amazon.lambda.powertools.common.internal.LambdaHandlerProcessor;
import software.amazon.lambda.powertools.common.stubs.TestLambdaContext;
import software.amazon.lambda.powertools.logging.PowertoolsLogging;
import software.amazon.lambda.powertools.logging.internal.handler.PowertoolsSystemLoggerHandler;

class PowertoolsSystemLoggerIT {

    private Context context;

    @BeforeEach
    void setUp() throws IllegalAccessException, IOException {
        MDC.clear();
        writeStaticField(LambdaHandlerProcessor.class, "isColdStart", null, true);
        writeStaticField(PowertoolsLogging.class, "hasBeenInitialized", new AtomicBoolean(false), true);
        context = new TestLambdaContext();
        truncateLogFile();
    }

    @AfterEach
    void cleanUp() throws IOException {
        truncateLogFile();
        MDC.clear();
    }

    @Test
    void shouldEmitPowertoolsJsonFromSystemLogger() {
        PowertoolsSystemLoggerHandler handler = new PowertoolsSystemLoggerHandler();

        handler.handleRequest("Input", context);

        File logFile = new File("target/logfile.json");
        assertThat(contentOf(logFile))
                .contains("\"message\":\"Processing order order-1\"")
                .contains("\"orderId\":\"order-1\"")
                .contains("\"cardNumber\":\"4111xxxx\"")
                .contains("\"function_name\":\"test-function\"")
                .contains("\"cold_start\":true")
                .contains("\"service\":\"testLog4j\"");
    }

    private static void truncateLogFile() throws IOException {
        try {
            FileChannel.open(Paths.get("target/logfile.json"), StandardOpenOption.WRITE).truncate(0).close();
        } catch (NoSuchFileException e) {
            // first run
        }
    }
}
