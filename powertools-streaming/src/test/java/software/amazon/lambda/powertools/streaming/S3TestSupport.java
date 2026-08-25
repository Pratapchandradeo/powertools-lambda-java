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

package software.amazon.lambda.powertools.streaming;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

public final class S3TestSupport {
    public static final byte[] HELLO_WORLD = "hello world".getBytes(StandardCharsets.UTF_8);
    public static final byte[] HELLO_LINES = "hello\nworld".getBytes(StandardCharsets.UTF_8);
    public static final byte[] CSV_BODY = "name,value\nhello,world\n".getBytes(StandardCharsets.UTF_8);

    private S3TestSupport() {
    }

    static void stubObject(S3Client s3Client, byte[] payload) {
        stubObject(s3Client, payload, new AtomicBoolean());
    }

    static void stubObject(S3Client s3Client, byte[] payload, AtomicBoolean aborted) {
        lenient().when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentLength((long) payload.length).build());
        lenient().when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            int start = parseRangeStart(request.range());
            byte[] slice = start >= payload.length ? new byte[0] : Arrays.copyOfRange(payload, start, payload.length);
            return responseStream(slice, aborted);
        });
    }

    static ResponseInputStream<GetObjectResponse> responseStream(byte[] data) {
        return responseStream(data, new AtomicBoolean());
    }

    static ResponseInputStream<GetObjectResponse> responseStream(byte[] data, AtomicBoolean aborted) {
        return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(data), () -> aborted.set(true)));
    }

    static S3Exception notFoundException() {
        return (S3Exception) S3Exception.builder()
                .statusCode(404)
                .message("Not Found")
                .build();
    }

    static S3Exception noSuchKeyException() {
        return (S3Exception) S3Exception.builder()
                .statusCode(400)
                .message("The specified key does not exist")
                .awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("NoSuchKey")
                        .errorMessage("The specified key does not exist")
                        .build())
                .build();
    }

    static SdkClientException sdkFailure() {
        return SdkClientException.builder().message("network error").build();
    }

    static S3Exception rangeNotSatisfiable() {
        return (S3Exception) S3Exception.builder()
                .statusCode(416)
                .message("Requested Range Not Satisfiable")
                .awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("InvalidRange")
                        .errorMessage("The requested range is not satisfiable")
                        .build())
                .build();
    }

    public static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(data);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] zip(String name1, String content1, String name2, String content2) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry(name1));
                zip.write(content1.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry(name2));
                zip.write(content2.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static int parseRangeStart(String range) {
        if (range == null || !range.startsWith("bytes=")) {
            return 0;
        }
        String start = range.substring("bytes=".length());
        int dash = start.indexOf('-');
        if (dash <= 0) {
            return 0;
        }
        return Integer.parseInt(start.substring(0, dash));
    }

    static S3Object open(S3Client s3Client, byte[] payload) {
        stubObject(s3Client, payload);
        return S3Object.builder()
                .bucket("bucket")
                .key("key")
                .s3Client(s3Client)
                .build();
    }
}
