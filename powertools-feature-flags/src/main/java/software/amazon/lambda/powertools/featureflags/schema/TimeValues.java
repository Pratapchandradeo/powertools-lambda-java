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

package software.amazon.lambda.powertools.featureflags.schema;

/**
 * Keys used inside a time-based condition {@code value} object.
 */
public final class TimeValues {

    public static final String START = "START";
    public static final String END = "END";
    public static final String TIMEZONE = "TIMEZONE";
    public static final String DAYS = "DAYS";
    public static final String HOUR_MIN_SEPARATOR = ":";
    public static final String DEFAULT_TIMEZONE = "UTC";

    private TimeValues() {
    }
}
