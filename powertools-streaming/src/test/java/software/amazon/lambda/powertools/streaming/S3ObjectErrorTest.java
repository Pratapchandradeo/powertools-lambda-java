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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.HELLO_WORLD;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.noSuchKeyException;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.notFoundException;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.responseStream;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.sdkFailure;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3ObjectErrorTest {

    @Mock
    private S3Client s3Client;

    @Test
    void missingKeyOnReadThrowsStreamingException() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(notFoundException());

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("missing.txt").s3Client(s3Client).build()) {
            assertThatThrownBy(s3::read)
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("s3://bucket/missing.txt")
                    .hasMessageContaining("not found");
        }
    }

    @Test
    void noSuchKeyErrorCodeIsTreatedAsNotFound() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(noSuchKeyException());

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build()) {
            assertThatThrownBy(() -> s3.read(new byte[8]))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Test
    void missingKeyOnSizeThrowsStreamingException() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(notFoundException());

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("missing.txt").s3Client(s3Client).build()) {
            assertThatThrownBy(s3::size)
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Test
    void sdkFailureOnReadIsWrapped() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(sdkFailure());

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build()) {
            assertThatThrownBy(s3::read)
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("failed to read")
                    .hasCauseInstanceOf(software.amazon.awssdk.core.exception.SdkClientException.class);
        }
    }

    @Test
    void sdkFailureOnSizeIsWrapped() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(sdkFailure());

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build()) {
            assertThatThrownBy(s3::size)
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("failed to get object size");
        }
    }

    @Test
    void otherS3ErrorsAreNotReportedAsNotFound() {
        S3Exception forbidden = (S3Exception) S3Exception.builder()
                .statusCode(403)
                .message("Access Denied")
                .build();
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(forbidden);

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build()) {
            assertThatThrownBy(s3::read)
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("Failed to read")
                    .hasMessageNotContaining("not found");
        }
    }

    @Test
    void transientReadFailureIsRetriedOnceWithSameRange() throws IOException {
        AtomicInteger remainingFailures = new AtomicInteger(1);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            if (remainingFailures.getAndDecrement() > 0) {
                return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new java.io.InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("connection reset");
                            }

                            @Override
                            public int read(byte[] b, int off, int len) throws IOException {
                                throw new IOException("connection reset");
                            }
                        }));
            }
            return responseStream(HELLO_WORLD);
        });

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build()) {
            assertThat(s3.readAllBytes()).isEqualTo(HELLO_WORLD);
        }

        verify(s3Client, times(2)).getObject(any(GetObjectRequest.class));
    }

    @Test
    void persistentReadFailureIsNotRetriedForever() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation ->
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new java.io.InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("connection reset");
                            }

                            @Override
                            public int read(byte[] b, int off, int len) throws IOException {
                                throw new IOException("connection reset");
                            }
                        })));

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build()) {
            assertThatThrownBy(s3::read)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("connection reset");
        }
    }

    @Test
    void versionedNotFoundIncludesVersionId() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(notFoundException());

        try (S3Object s3 = S3Object.builder()
                .bucket("bucket")
                .key("key")
                .versionId("v-missing")
                .s3Client(s3Client)
                .build()) {
            assertThatThrownBy(s3::read)
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("versionId=v-missing");
        }
    }
}
