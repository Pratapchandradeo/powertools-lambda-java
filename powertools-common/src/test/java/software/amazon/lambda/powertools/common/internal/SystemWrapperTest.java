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

package software.amazon.lambda.powertools.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

class SystemWrapperTest {

    @Test
    @SetEnvironmentVariable(key = "POWERTOOLS_TRACER_CAPTURE_RESPONSE", value = "true")
    void containsKeyIsTrueWhenVariableIsPresent() {
        assertThat(SystemWrapper.containsKey("POWERTOOLS_TRACER_CAPTURE_RESPONSE")).isTrue();
        assertThat(SystemWrapper.getenv("POWERTOOLS_TRACER_CAPTURE_RESPONSE")).isEqualTo("true");
    }

    @Test
    void containsKeyIsFalseWhenVariableIsAbsent() {
        assertThat(SystemWrapper.containsKey("POWERTOOLS_DOES_NOT_EXIST_" + System.nanoTime())).isFalse();
    }
}
