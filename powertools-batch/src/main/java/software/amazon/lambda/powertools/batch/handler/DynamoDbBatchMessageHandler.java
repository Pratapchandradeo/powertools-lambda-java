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
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;

import software.amazon.lambda.powertools.batch.exception.DeserializationNotSupportedException;

/**
 * A batch message processor for DynamoDB Streams batches.
 *
 * @see <a href="https://docs.aws.amazon.com/lambda/latest/dg/with-ddb.html#services-ddb-batchfailurereporting">DynamoDB Streams batch failure reporting</a>
 */
public class DynamoDbBatchMessageHandler extends
        AbstractBatchMessageHandler<DynamodbEvent, DynamodbEvent.DynamodbStreamRecord, Void, StreamsEventResponse.BatchItemFailure, StreamsEventResponse> {

    public DynamoDbBatchMessageHandler(Consumer<DynamodbEvent.DynamodbStreamRecord> successHandler,
            BiConsumer<DynamodbEvent.DynamodbStreamRecord, Throwable> failureHandler,
            BiConsumer<DynamodbEvent.DynamodbStreamRecord, Context> rawMessageHandler) {
        super(rawMessageHandler, null, null, successHandler, failureHandler);
    }

    @Override
    protected List<DynamodbEvent.DynamodbStreamRecord> getRecords(DynamodbEvent event) {
        return event.getRecords();
    }

    @Override
    protected StreamsEventResponse.BatchItemFailure createFailure(DynamodbEvent.DynamodbStreamRecord streamRecord) {
        return StreamsEventResponse.BatchItemFailure.builder()
                .withItemIdentifier(streamRecord.getDynamodb().getSequenceNumber()).build();
    }

    @Override
    protected StreamsEventResponse createResponse(List<StreamsEventResponse.BatchItemFailure> failures) {
        return StreamsEventResponse.builder().withBatchItemFailures(failures).build();
    }

    @Override
    protected String getRecordIdentifier(DynamodbEvent.DynamodbStreamRecord streamRecord) {
        return streamRecord.getEventID();
    }

    @Override
    protected Void deserialize(DynamodbEvent.DynamodbStreamRecord streamRecord) {
        throw new DeserializationNotSupportedException();
    }
}
