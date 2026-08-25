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
import static software.amazon.lambda.powertools.streaming.S3TestSupport.CSV_BODY;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.HELLO_WORLD;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.gzip;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.open;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.zip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.lambda.powertools.streaming.transformations.CsvTransform;
import software.amazon.lambda.powertools.streaming.transformations.GzipTransform;
import software.amazon.lambda.powertools.streaming.transformations.ZipTransform;

@ExtendWith(MockitoExtension.class)
class S3ObjectTransformTest {

    @Mock
    private S3Client s3Client;

    @Test
    void gzipBuilderFlagDecompressesOnRead() throws IOException {
        try (S3Object s3 = S3Object.builder()
                .bucket("bucket")
                .key("key")
                .s3Client(s3Client)
                .gzip(true)
                .build()) {
            S3TestSupport.stubObject(s3Client, gzip(HELLO_WORLD));
            assertThat(s3.readAllBytes()).isEqualTo(HELLO_WORLD);
        }
    }

    @Test
    void transformReturnsGunzippedStream() throws IOException {
        try (S3Object s3 = open(s3Client, gzip(HELLO_WORLD));
                InputStream gunzipped = s3.transform(new GzipTransform())) {
            assertThat(gunzipped.readAllBytes()).isEqualTo(HELLO_WORLD);
        }
    }

    @Test
    void transformInPlaceAppliesGzip() throws IOException {
        try (S3Object s3 = open(s3Client, gzip(HELLO_WORLD))) {
            s3.transformInPlace(new GzipTransform());
            assertThat(s3.readAllBytes()).isEqualTo(HELLO_WORLD);
        }
    }

    @Test
    void gzipThenCsvViaTransform() throws IOException {
        try (S3Object s3 = S3Object.builder()
                .bucket("bucket")
                .key("key")
                .s3Client(s3Client)
                .gzip(true)
                .build()) {
            S3TestSupport.stubObject(s3Client, gzip(CSV_BODY));
            Iterator<Map<String, String>> rows = s3.transform(new CsvTransform());
            assertThat(rows.next()).containsEntry("name", "hello").containsEntry("value", "world");
        }
    }

    @Test
    void csvRowsConvenience() throws IOException {
        try (S3Object s3 = open(s3Client, CSV_BODY)) {
            Iterator<Map<String, String>> rows = s3.csvRows();
            assertThat(rows.next()).containsEntry("name", "hello");
        }
    }

    @Test
    void csvRowsWithDelimiter() throws IOException {
        byte[] tsv = "name\tvalue\nhello\tworld\n".getBytes(StandardCharsets.UTF_8);
        try (S3Object s3 = open(s3Client, tsv)) {
            Iterator<Map<String, String>> rows = s3.csvRows('\t');
            assertThat(rows.next()).containsEntry("value", "world");
        }
    }

    @Test
    void zipListsAndOpensEntryFromS3Object() throws IOException {
        byte[] archive = zip("1.txt", "This is file 1", "2.txt", "This is file 2");
        try (S3Object s3 = open(s3Client, archive);
                S3ZipArchive zipArchive = s3.transform(new ZipTransform())) {
            assertThat(zipArchive.names()).containsExactly("1.txt", "2.txt");
            try (InputStream inner = zipArchive.open("2.txt")) {
                assertThat(new String(inner.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("This is file 2");
            }
        }
    }

    @Test
    void seekAfterInPlaceGzipThrows() {
        try (S3Object s3 = S3Object.builder()
                .bucket("bucket")
                .key("key")
                .s3Client(s3Client)
                .gzip(true)
                .build()) {
            assertThatThrownBy(() -> s3.seek(0, SeekWhence.SET))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("seek is not supported");
        }
    }

    @Test
    void seekThenGzipTransform() throws IOException {
        byte[] payload = gzip(HELLO_WORLD);
        try (S3Object s3 = open(s3Client, payload)) {
            s3.seek(0, SeekWhence.SET);
            try (InputStream gunzipped = s3.transform(new GzipTransform())) {
                assertThat(gunzipped.readAllBytes()).isEqualTo(HELLO_WORLD);
            }
        }
    }

    @Test
    void cannotAddInPlaceTransformAfterRead() throws IOException {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            s3.read();
            assertThatThrownBy(() -> s3.transformInPlace(new GzipTransform()))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("after reading");
        }
    }

    @Test
    void transformRequiresArgument() {
        try (S3Object s3 = open(s3Client, HELLO_WORLD)) {
            assertThatThrownBy(() -> s3.transform(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> s3.transformInPlace(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
