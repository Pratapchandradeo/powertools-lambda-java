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

package software.amazon.lambda.powertools.e2e;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.lambda.powertools.streaming.S3Object;
import software.amazon.lambda.powertools.streaming.S3ZipArchive;
import software.amazon.lambda.powertools.streaming.StreamingException;
import software.amazon.lambda.powertools.streaming.transformations.CsvTransform;
import software.amazon.lambda.powertools.streaming.transformations.GzipTransform;
import software.amazon.lambda.powertools.streaming.transformations.ZipTransform;

/**
 * Mirrors the Python streaming e2e handler payload:
 * {@code bucket}, {@code key}, optional {@code version_id},
 * {@code is_gzip}, {@code is_csv}, {@code transform_gzip}, {@code transform_csv},
 * {@code transform_zip}, {@code in_place}.
 */
public class Function implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String bucket = string(event, "bucket");
        String key = string(event, "key");
        String versionId = string(event, "version_id");
        boolean isGzip = flag(event, "is_gzip");
        boolean isCsv = flag(event, "is_csv");
        boolean transformGzip = flag(event, "transform_gzip");
        boolean transformCsv = flag(event, "transform_csv");
        boolean transformZip = flag(event, "transform_zip");
        boolean inPlace = flag(event, "in_place");

        try (S3Object s3 = S3Object.builder()
                .bucket(bucket)
                .key(key)
                .versionId(versionId)
                .gzip(isGzip)
                .build()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("size", s3.size());

            if (transformZip) {
                S3ZipArchive zip = s3.transform(new ZipTransform());
                result.put("manifest", zip.names());
                try (InputStream inner = zip.open("2.txt")) {
                    result.put("body", new String(inner.readAllBytes(), StandardCharsets.UTF_8));
                }
                return result;
            }

            if (transformGzip && !isGzip) {
                if (inPlace) {
                    s3.transformInPlace(new GzipTransform());
                } else {
                    try (InputStream gunzipped = s3.transform(new GzipTransform())) {
                        result.put("body", new String(gunzipped.readAllBytes(), StandardCharsets.UTF_8));
                    }
                    return result;
                }
            }

            if (isCsv || transformCsv) {
                Iterator<Map<String, String>> rows = inPlace && transformCsv
                        ? new CsvTransform().apply(s3)
                        : s3.csvRows();
                result.put("body", rows.hasNext() ? rows.next() : null);
                return result;
            }

            result.put("body", new String(s3.readAllBytes(), StandardCharsets.UTF_8));
            return result;
        } catch (StreamingException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "Not found");
                return error;
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String string(Map<String, Object> event, String name) {
        Object value = event.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean flag(Map<String, Object> event, String name) {
        Object value = event.get(name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
