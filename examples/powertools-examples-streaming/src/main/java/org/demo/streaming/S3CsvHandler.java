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

package org.demo.streaming;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;

import software.amazon.lambda.powertools.streaming.S3Object;

/**
 * Streams a CSV (optionally gzip-compressed) from the S3 object that triggered this function.
 */
public class S3CsvHandler implements RequestHandler<S3Event, String> {
    private static final Logger LOG = LoggerFactory.getLogger(S3CsvHandler.class);

    @Override
    public String handleRequest(S3Event event, Context context) {
        S3EventNotificationRecord record = event.getRecords().get(0);
        String bucket = record.getS3().getBucket().getName();
        String key = URLDecoder.decode(record.getS3().getObject().getKey().replace("+", "%2B"),
                StandardCharsets.UTF_8);
        boolean gzip = key.endsWith(".gz");

        int count = 0;
        try (S3Object s3 = S3Object.builder()
                .bucket(bucket)
                .key(key)
                .gzip(gzip)
                .build()) {
            Iterator<Map<String, String>> rows = s3.csvRows();
            while (rows.hasNext()) {
                Map<String, String> row = rows.next();
                LOG.info("row={}", row);
                count++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream s3://" + bucket + "/" + key, e);
        }
        return "processed " + count + " rows";
    }
}
