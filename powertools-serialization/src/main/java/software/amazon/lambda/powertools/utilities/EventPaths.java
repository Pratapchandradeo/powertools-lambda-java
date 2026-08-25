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

package software.amazon.lambda.powertools.utilities;

/**
 * Built-in JMESPath envelopes for nested Lambda events.
 * <p>
 * Pass these to {@link EventDeserializer#extractDataFrom(Object, String)} or to
 * {@code withEnvelope} / {@code buildWithMessageHandler} on the Batch builder.
 * Paths are applied to the object as JSON; they do not use the built-in one-level unwrap.
 */
public final class EventPaths {

    /**
     * SNS notification wrapped in a single {@link com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage}.
     * Use this from Batch, which deserializes one SQS record at a time.
     */
    public static final String SQS_SNS = "powertools_json(body).Message";

    private EventPaths() {
    }
}
