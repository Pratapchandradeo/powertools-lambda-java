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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import software.amazon.lambda.powertools.streaming.S3Object;
import software.amazon.lambda.powertools.streaming.S3ZipArchive;
import software.amazon.lambda.powertools.streaming.SeekWhence;
import software.amazon.lambda.powertools.streaming.StreamTransform;
import software.amazon.lambda.powertools.streaming.StreamingException;
import software.amazon.lambda.powertools.streaming.internal.NonClosingInputStream;

/**
 * Exposes a ZIP stream as an {@link S3ZipArchive}.
 * <p>
 * ZIP is sequential: {@code names()} and {@code open(name)} scan from the
 * archive start. The source must be rewindable ({@link S3Object} or
 * mark/reset). ZIP cannot be composed with gzip on the same stream.
 * </p>
 */
public final class ZipTransform implements StreamTransform<S3ZipArchive> {

    @Override
    public S3ZipArchive apply(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        return new SequentialZipArchive(input);
    }

    static final class SequentialZipArchive implements S3ZipArchive {
        private final InputStream source;
        private final long startPosition;
        private boolean marked;

        SequentialZipArchive(InputStream source) {
            this.source = source;
            if (source instanceof S3Object) {
                this.startPosition = ((S3Object) source).position();
            } else {
                this.startPosition = 0;
                if (source.markSupported()) {
                    source.mark(Integer.MAX_VALUE);
                    this.marked = true;
                }
            }
        }

        @Override
        public List<String> names() throws IOException {
            rewind();
            List<String> names = new ArrayList<>();
            ZipInputStream zip = new ZipInputStream(new NonClosingInputStream(source));
            try {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    names.add(entry.getName());
                }
            } finally {
                zip.close();
            }
            return names;
        }

        @Override
        public InputStream open(String name) throws IOException {
            if (name == null || name.isEmpty()) {
                throw new StreamingException("ZIP entry name is required");
            }
            rewind();
            ZipInputStream zip = new ZipInputStream(new NonClosingInputStream(source));
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    return zip;
                }
            }
            zip.close();
            throw new StreamingException("ZIP entry not found: " + name);
        }

        @Override
        public void close() {
            // source is owned by the caller (typically S3Object)
        }

        private void rewind() throws IOException {
            if (source instanceof S3Object) {
                ((S3Object) source).seek(startPosition, SeekWhence.SET);
                return;
            }
            if (marked) {
                source.reset();
                return;
            }
            throw new StreamingException(
                    "ZIP requires a rewindable stream (S3Object or mark/reset) to list or reopen entries");
        }
    }
}
