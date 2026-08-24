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

package software.amazon.lambda.powertools.batch.handler;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;

import software.amazon.lambda.powertools.utilities.EventDeserializer;

/**
 * A batch message processor for Kinesis Streams batch processing.
 * <p>
 * Refer to <a href="https://docs.aws.amazon.com/lambda/latest/dg/with-kinesis.html#services-kinesis-batchfailurereporting">Kinesis Batch failure reporting</a>
 *
 * @param <M> The user-defined type of the Kinesis record payload
 */
public class KinesisStreamsBatchMessageHandler<M> extends
        AbstractBatchMessageHandler<KinesisEvent, KinesisEvent.KinesisEventRecord, M, StreamsEventResponse.BatchItemFailure, StreamsEventResponse> {

    public KinesisStreamsBatchMessageHandler(BiConsumer<KinesisEvent.KinesisEventRecord, Context> rawMessageHandler,
            BiConsumer<M, Context> messageHandler,
            Class<M> messageClass,
            Consumer<KinesisEvent.KinesisEventRecord> successHandler,
            BiConsumer<KinesisEvent.KinesisEventRecord, Throwable> failureHandler) {
        super(rawMessageHandler, messageHandler, messageClass, successHandler, failureHandler);
    }

    @Override
    protected List<KinesisEvent.KinesisEventRecord> getRecords(KinesisEvent event) {
        return event.getRecords();
    }

    @Override
    protected StreamsEventResponse.BatchItemFailure createFailure(KinesisEvent.KinesisEventRecord eventRecord) {
        return StreamsEventResponse.BatchItemFailure.builder()
                .withItemIdentifier(eventRecord.getKinesis().getSequenceNumber()).build();
    }

    @Override
    protected StreamsEventResponse createResponse(List<StreamsEventResponse.BatchItemFailure> failures) {
        return StreamsEventResponse.builder().withBatchItemFailures(failures).build();
    }

    @Override
    protected String getRecordIdentifier(KinesisEvent.KinesisEventRecord eventRecord) {
        return eventRecord.getEventID();
    }

    @Override
    protected M deserialize(KinesisEvent.KinesisEventRecord eventRecord) {
        return EventDeserializer.extractDataFrom(eventRecord).as(getMessageClass());
    }
}
