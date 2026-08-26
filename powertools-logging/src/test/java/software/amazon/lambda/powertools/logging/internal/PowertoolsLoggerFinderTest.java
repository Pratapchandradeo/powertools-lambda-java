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

import org.junit.jupiter.api.Test;

class PowertoolsLoggerFinderTest {

    @Test
    void getLogger_returnsPowertoolsSystemLoggerWithName() {
        PowertoolsLoggerFinder finder = new PowertoolsLoggerFinder();

        System.Logger logger = finder.getLogger("finder.test", getClass().getModule());

        assertThat(logger).isInstanceOf(PowertoolsSystemLogger.class);
        assertThat(logger.getName()).isEqualTo("finder.test");
    }

    @Test
    void getLogger_returnsCachedInstance() {
        PowertoolsLoggerFinder finder = new PowertoolsLoggerFinder();

        System.Logger first = finder.getLogger("finder.cache", getClass().getModule());
        System.Logger second = finder.getLogger("finder.cache", getClass().getModule());

        assertThat(first).isSameAs(second);
    }

    @Test
    void getLogger_ignoresModuleArgument() {
        PowertoolsLoggerFinder finder = new PowertoolsLoggerFinder();

        System.Logger withModule = finder.getLogger("finder.module", getClass().getModule());
        System.Logger withoutModule = finder.getLogger("finder.module", null);

        assertThat(withModule).isSameAs(withoutModule);
    }

    @Test
    void systemGetLogger_usesPowertoolsFinderWhenRegistered() {
        System.Logger logger = System.getLogger("finder.serviceLoader");

        assertThat(logger).isInstanceOf(PowertoolsSystemLogger.class);
        assertThat(logger.getName()).isEqualTo("finder.serviceLoader");
    }
}
