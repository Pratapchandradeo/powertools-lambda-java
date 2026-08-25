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
import static software.amazon.lambda.powertools.streaming.S3TestSupport.HELLO_WORLD;
import static software.amazon.lambda.powertools.streaming.S3TestSupport.gzip;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class GzipTransformTest {

    @Test
    void decompressesGzipPayload() throws IOException {
        try (InputStream in = new GzipTransform().apply(new ByteArrayInputStream(gzip(HELLO_WORLD)))) {
            assertThat(in.readAllBytes()).isEqualTo(HELLO_WORLD);
        }
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> new GzipTransform().apply(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPlainBytes() {
        assertThatThrownBy(() -> new GzipTransform().apply(new ByteArrayInputStream("not gzip".getBytes(
                StandardCharsets.UTF_8))))
                .isInstanceOf(IOException.class);
    }
}
