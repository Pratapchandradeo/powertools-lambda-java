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

/**
 * {@link System.LoggerFinder} that routes {@link System#getLogger(String)} to
 * {@link PowertoolsSystemLogger}, so JDK platform logging uses the same SLF4J
 * backend as {@code LoggerFactory.getLogger}.
 *
 * <p>Registered via {@code META-INF/services/java.lang.System$LoggerFinder}.
 * Do not also add {@code slf4j-jdk-platform-logging} or {@code log4j-jpl};
 * the JVM loads a single {@code LoggerFinder}.
 */
public final class PowertoolsLoggerFinder extends System.LoggerFinder {

    /**
     * Public no-arg constructor required by {@link java.util.ServiceLoader}.
     */
    public PowertoolsLoggerFinder() {
        // ServiceLoader
    }

    @Override
    public System.Logger getLogger(String name, Module module) {
        return PowertoolsSystemLogger.getLogger(name);
    }
}
