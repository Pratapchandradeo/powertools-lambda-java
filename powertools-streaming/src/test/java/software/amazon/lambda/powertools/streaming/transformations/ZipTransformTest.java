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

package software.amazon.lambda.powertools.streaming.transformations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.zip;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import software.amazon.lambda.powertools.streaming.S3ZipArchive;
import software.amazon.lambda.powertools.streaming.StreamingException;

class ZipTransformTest {

    private static final byte[] ARCHIVE = zip("1.txt", "This is file 1", "2.txt", "This is file 2");

    @Test
    void listsEntryNames() throws IOException {
        try (S3ZipArchive zip = new ZipTransform().apply(new ByteArrayInputStream(ARCHIVE))) {
            assertThat(zip.names()).containsExactly("1.txt", "2.txt");
        }
    }

    @Test
    void opensNamedEntry() throws IOException {
        try (S3ZipArchive zip = new ZipTransform().apply(new ByteArrayInputStream(ARCHIVE));
                InputStream inner = zip.open("2.txt")) {
            assertThat(new String(inner.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("This is file 2");
        }
    }

    @Test
    void canListThenOpenBecauseSourceIsRewound() throws IOException {
        try (S3ZipArchive zip = new ZipTransform().apply(new ByteArrayInputStream(ARCHIVE))) {
            assertThat(zip.names()).contains("1.txt");
            try (InputStream inner = zip.open("1.txt")) {
                assertThat(new String(inner.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("This is file 1");
            }
        }
    }

    @Test
    void missingEntryThrows() throws IOException {
        try (S3ZipArchive zip = new ZipTransform().apply(new ByteArrayInputStream(ARCHIVE))) {
            assertThatThrownBy(() -> zip.open("missing.txt"))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Test
    void emptyNameThrows() throws IOException {
        try (S3ZipArchive zip = new ZipTransform().apply(new ByteArrayInputStream(ARCHIVE))) {
            assertThatThrownBy(() -> zip.open(""))
                    .isInstanceOf(StreamingException.class)
                    .hasMessageContaining("name is required");
        }
    }

    @Test
    void nonRewindableStreamCannotListTwice() {
        InputStream once = new FilterInputStream(new ByteArrayInputStream(ARCHIVE)) {
            @Override
            public boolean markSupported() {
                return false;
            }
        };

        S3ZipArchive zip = new ZipTransform().apply(once);
        assertThatThrownBy(zip::names)
                .isInstanceOf(StreamingException.class)
                .hasMessageContaining("rewindable");
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> new ZipTransform().apply(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
