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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.HELLO_WORLD;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.open;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.rangeNotSatisfiable;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.stubObject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ObjectSeekTest {

    @Mock
    private S3Client s3Client;

    @Test
    void firstReadUsesRangeFromZero() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            s3.read();
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().range()).isEqualTo("bytes=0-");
    }

    @Test
    void seekSetThenReadUsesRangeFromOffset() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.seek(6, SeekWhence.SET)).isEqualTo(6);
            assertThat(s3.readAllBytes()).isEqualTo("world".getBytes());
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().range()).isEqualTo("bytes=6-");
    }

    @Test
    void seekCurrentAccumulatesOffset() {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.seek(2, SeekWhence.CURRENT)).isEqualTo(2);
            assertThat(s3.seek(4, SeekWhence.CURRENT)).isEqualTo(6);
            assertThat(s3.position()).isEqualTo(6);
        }
    }

    @Test
    void seekEndUsesHeadObjectThenRangesFromSize() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.seek(0, SeekWhence.END)).isEqualTo(HELLO_WORLD.length);
            assertThat(s3.seek(-5, SeekWhence.END)).isEqualTo(HELLO_WORLD.length - 5);
            assertThat(s3.readAllBytes()).isEqualTo("world".getBytes());
        }

        verify(s3Client).headObject(any(HeadObjectRequest.class));
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().range()).isEqualTo("bytes=6-");
    }

    @Test
    void samePositionSeekDoesNotReopenStream() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            s3.read();
            long position = s3.position();
            s3.seek(position, SeekWhence.SET);
            s3.seek(0, SeekWhence.CURRENT);
            s3.read();
        }

        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
    }

    @Test
    void seekAfterReadAbortsCurrentBodyAndReopens() throws IOException {
        AtomicBoolean aborted = new AtomicBoolean();
        stubObject(s3Client, HELLO_WORLD, aborted);

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build()) {
            s3.read();
            assertThat(aborted.get()).isFalse();
            s3.seek(6, SeekWhence.SET);
            assertThat(aborted.get()).isTrue();
            assertThat(s3.readAllBytes()).isEqualTo("world".getBytes());
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client, times(2)).getObject(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(GetObjectRequest::range)
                .containsExactly("bytes=0-", "bytes=6-");
    }

    @Test
    void seekPastEndAllowsReadToReturnEof() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            s3.seek(1000, SeekWhence.SET);
            assertThat(s3.read()).isEqualTo(-1);
        }
    }

    @Test
    void seekToNegativePositionThrows() {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThatThrownBy(() -> s3.seek(-1, SeekWhence.SET))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("negative");
            assertThatThrownBy(() -> s3.seek(-1, SeekWhence.CURRENT))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("negative");
            assertThatThrownBy(() -> s3.seek(-HELLO_WORLD.length - 1, SeekWhence.END))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("negative");
        }
    }

    @Test
    void seekWithNullWhenceThrows() {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThatThrownBy(() -> s3.seek(0, null))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("whence");
        }
    }

    @Test
    void rangeNotSatisfiableIsTreatedAsEof() throws IOException {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(rangeNotSatisfiable());

        try (S3Object s3 = S3Object.builder().bucket("bucket").key("key").s3Client(s3Client).build()) {
            s3.seek(1_000, SeekWhence.SET);
            assertThat(s3.read()).isEqualTo(-1);
        }
    }

    @Test
    void knownSizePastEndDoesNotCallGetObject() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThat(s3.size()).isEqualTo(HELLO_WORLD.length);
            s3.seek(1_000, SeekWhence.SET);
            assertThat(s3.read()).isEqualTo(-1);
        }

        verify(s3Client, never()).getObject(any(GetObjectRequest.class));
    }

    @Test
    void skipThenReadStartsAtSkippedOffset() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            s3.skip(6);
            assertThat(new String(s3.readAllBytes())).isEqualTo("world");
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().range()).isEqualTo("bytes=6-");
    }
}
