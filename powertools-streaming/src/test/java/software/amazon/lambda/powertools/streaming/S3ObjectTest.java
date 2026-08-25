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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.HELLO_LINES;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.HELLO_WORLD;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.open;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.stubObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ObjectTest {

    @Mock
    private S3Client s3Client;

    @Test
    void builderStoresBucketKeyAndVersion() {
        S3Object s3 = S3Object.builder()
                .bucket("my-bucket")
                .key("path/file.txt")
                .versionId("v1")
                .s3Client(s3Client)
                .build();

        assertThat(s3.getBucket()).isEqualTo("my-bucket");
        assertThat(s3.getKey()).isEqualTo("path/file.txt");
        assertThat(s3.getVersionId()).isEqualTo("v1");
        assertThat(s3.position()).isZero();
        assertThat(s3.isClosed()).isFalse();
    }

    @Test
    void builderDoesNotCallS3UntilRead() {
        S3Object.builder()
                .bucket("bucket")
                .key("key")
                .s3Client(s3Client)
                .build();

        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void builderRequiresBucket(String bucket) {
        assertThatThrownBy(() -> S3Object.builder()
                .bucket(bucket)
                .key("key")
                .s3Client(s3Client)
                .build())
                .isInstanceOf(StreamingException.class)
                .hasMessageContaining("bucket");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void builderRequiresKey(String key) {
        assertThatThrownBy(() -> S3Object.builder()
                .bucket("bucket")
                .key(key)
                .s3Client(s3Client)
                .build())
                .isInstanceOf(StreamingException.class)
                .hasMessageContaining("key");
    }

    @Test
    void readAllBytesReturnsObjectContent() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.readAllBytes()).isEqualTo(HELLO_WORLD);
            assertThat(s3.position()).isEqualTo(HELLO_WORLD.length);
        }
    }

    @Test
    void singleByteReadAdvancesPosition() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.read()).isEqualTo('h');
            assertThat(s3.read()).isEqualTo('e');
            assertThat(s3.position()).isEqualTo(2);
        }
    }

    @Test
    void readAfterEndReturnsMinusOne() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            s3.readAllBytes();
            assertThat(s3.read()).isEqualTo(-1);
            assertThat(s3.read(new byte[4])).isEqualTo(-1);
        }
    }

    @Test
    void emptyObjectReadReturnsMinusOne() throws IOException {
        try (S3Object s3 = open(s3Client, new byte[0])) {
            assertThat(s3.read()).isEqualTo(-1);
            assertThat(s3.size()).isZero();
        }
    }

    @Test
    void zeroLengthReadDoesNotOpenStream() throws IOException {
        S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build();

        assertThat(s3.read(new byte[8], 0, 0)).isZero();
        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void linesSplitsOnNewlines() {
        try (S3Object s3 = open(s3Client, HELLO_LINES)) {
            List<String> lines = s3.lines().collect(Collectors.toList());
            assertThat(lines).containsExactly("hello", "world");
        }
    }

    @Test
    void tryWithResourcesClosesStream() throws IOException {
        S3Object s3 = open(s3Client, HELLO_WORLD);
        try (S3Object ignored = s3) {
            assertThat(s3.read()).isEqualTo('h');
        }

        assertThat(s3.isClosed()).isTrue();
        assertThatThrownBy(s3::read)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void closeIsIdempotent() {
        S3Object s3 = open(s3Client, HELLO_WORLD);
        s3.close();
        s3.close();
        assertThat(s3.isClosed()).isTrue();
    }

    @Test
    void seekAfterCloseThrows() {
        S3Object s3 = open(s3Client, HELLO_WORLD);
        s3.close();

        assertThatThrownBy(() -> s3.seek(0, SeekWhence.SET))
                .isInstanceOf(StreamingException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void markAndResetReturnToMarkedPosition() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.markSupported()).isTrue();
            s3.read();
            s3.mark(32);
            long marked = s3.position();
            s3.readAllBytes();
            s3.reset();
            assertThat(s3.position()).isEqualTo(marked);
            assertThat(s3.read()).isEqualTo('e');
        }
    }

    @Test
    void resetWithoutMarkThrows() {
        S3Object s3 = open(s3Client, HELLO_WORLD);

        assertThatThrownBy(s3::reset)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("marked");
    }

    @Test
    void skipUsesSeekAndDoesNotRequirePriorRead() {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.skip(6)).isEqualTo(6);
            assertThat(s3.position()).isEqualTo(6);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -10})
    void skipNonPositiveReturnsZero(long n) {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.skip(n)).isZero();
            assertThat(s3.position()).isZero();
        }
    }

    @Test
    void sizeUsesHeadObjectOnce() {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.size()).isEqualTo(HELLO_WORLD.length);
            assertThat(s3.size()).isEqualTo(HELLO_WORLD.length);
        }

        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void versionIdIsForwardedOnGetAndHead() throws IOException {
        stubObject(s3Client, HELLO_WORLD);
        try (S3Object s3 = S3Object.builder()
                .bucket("bucket")
                .key("key")
                .versionId("abc")
                .s3Client(s3Client)
                .build()) {
            s3.size();
            s3.read();
        }

        ArgumentCaptor<HeadObjectRequest> head = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(head.capture());
        assertThat(head.getValue().versionId()).isEqualTo("abc");

        ArgumentCaptor<GetObjectRequest> get = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(get.capture());
        assertThat(get.getValue().versionId()).isEqualTo("abc");
        assertThat(get.getValue().bucket()).isEqualTo("bucket");
        assertThat(get.getValue().key()).isEqualTo("key");
    }

    @Test
    void utf8ContentIsPreserved() throws IOException {
        byte[] payload = "café".getBytes(StandardCharsets.UTF_8);
        try (S3Object s3 = open(s3Client, payload)) {
            assertThat(new String(s3.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("café");
        }
    }
}
