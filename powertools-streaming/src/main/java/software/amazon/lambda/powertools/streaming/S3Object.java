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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.lambda.powertools.streaming.internal.DefaultS3ClientFactory;
import software.amazon.lambda.powertools.streaming.internal.S3SeekableInputStream;
import software.amazon.lambda.powertools.streaming.transformations.CsvTransform;
import software.amazon.lambda.powertools.streaming.transformations.GzipTransform;

/**
 * Seekable, streamable reader for an Amazon S3 object.
 * <p>
 * Bytes are fetched incrementally with ranged {@code GetObject} calls as you read,
 * so the object does not need to fit in memory. Seeking issues a new
 * {@code Range: bytes={position}-} request after aborting the current HTTP body.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * try (S3Object s3 = S3Object.builder()
 *         .bucket(bucket)
 *         .key(key)
 *         .build()) {
 *     s3.lines().forEach(line -> process(line));
 * }
 * }</pre>
 *
 * <p>
 * This class is not thread-safe. Closing the stream does not close the
 * {@link S3Client} (injected or default).
 * </p>
 */
public final class S3Object extends InputStream {

    private final S3SeekableInputStream rawStream;
    private final List<StreamTransform<? extends InputStream>> inPlaceTransforms = new ArrayList<>();
    private InputStream view;

    S3Object(S3SeekableInputStream rawStream) {
        this.rawStream = rawStream;
    }

    /**
     * Creates a new builder.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the S3 bucket.
     *
     * @return the bucket name
     */
    public String getBucket() {
        return rawStream.getBucket();
    }

    /**
     * Returns the S3 object key.
     *
     * @return the object key
     */
    public String getKey() {
        return rawStream.getKey();
    }

    /**
     * Returns the object version id, or {@code null} if the request is unversioned.
     *
     * @return the version id or {@code null}
     */
    public String getVersionId() {
        return rawStream.getVersionId();
    }

    /**
     * Returns whether this stream has been closed.
     *
     * @return {@code true} if {@link #close()} was called
     */
    public boolean isClosed() {
        return rawStream.isClosed();
    }

    /**
     * Returns the current read position in the object, in bytes.
     *
     * @return the current position
     */
    public long position() {
        return rawStream.position();
    }

    /**
     * Returns the size of the S3 object in bytes (one cached {@code HeadObject} call).
     *
     * @return the object size
     */
    public long size() {
        return rawStream.size();
    }

    /**
     * Moves the read position. The current HTTP body is aborted if the position changes.
     *
     * @param offset the byte offset, interpreted according to {@code whence}
     * @param whence the origin of {@code offset}
     * @return the resulting position
     */
    public long seek(long offset, SeekWhence whence) {
        if (!inPlaceTransforms.isEmpty()) {
            throw new StreamingException("seek is not supported after in-place data transforms");
        }
        return rawStream.seek(offset, whence);
    }

    /**
     * Applies a transformation and returns the result without changing this object.
     *
     * @param transform the transformation
     * @param <T>       the result type
     * @return the transformed view
     * @throws IOException if the transformation fails
     */
    public <T> T transform(StreamTransform<T> transform) throws IOException {
        if (transform == null) {
            throw new IllegalArgumentException("transform is required");
        }
        return transform.apply(activeStream());
    }

    /**
     * Applies an {@link InputStream} transformation in place. Later reads on this
     * {@code S3Object} go through the transformed stream. Must be called before reading.
     *
     * @param transform the transformation
     * @return this object
     */
    public S3Object transformInPlace(StreamTransform<? extends InputStream> transform) {
        if (transform == null) {
            throw new IllegalArgumentException("transform is required");
        }
        if (view != null) {
            throw new StreamingException("Cannot add in-place transforms after reading has started");
        }
        inPlaceTransforms.add(transform);
        return this;
    }

    /**
     * Parses this stream as CSV (header row + comma delimiter).
     *
     * @return row iterator
     * @throws IOException if the stream cannot be read
     */
    public Iterator<Map<String, String>> csvRows() throws IOException {
        return transform(new CsvTransform());
    }

    /**
     * Parses this stream as delimited text with a header row.
     *
     * @param delimiter field delimiter ({@code '\t'} for TSV)
     * @return row iterator
     * @throws IOException if the stream cannot be read
     */
    public Iterator<Map<String, String>> csvRows(char delimiter) throws IOException {
        return transform(new CsvTransform(delimiter));
    }

    /**
     * Returns a UTF-8 line stream over the object from the current position.
     * Closing the returned stream closes this {@code S3Object}.
     *
     * @return a stream of lines
     */
    public Stream<String> lines() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(this, StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public int read() throws IOException {
        return activeStream().read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return activeStream().read(buffer, offset, length);
    }

    @Override
    public long skip(long n) {
        if (inPlaceTransforms.isEmpty()) {
            return rawStream.skip(n);
        }
        try {
            return activeStream().skip(n);
        } catch (IOException e) {
            throw new StreamingException("Failed to skip transformed stream", e);
        }
    }

    @Override
    public boolean markSupported() {
        return rawStream.markSupported();
    }

    @Override
    public synchronized void mark(int readlimit) {
        rawStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        rawStream.reset();
    }

    @Override
    public void close() {
        if (view != null && view != rawStream) {
            try {
                view.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                view = null;
                rawStream.close();
            }
            return;
        }
        rawStream.close();
    }

    private InputStream activeStream() throws IOException {
        if (view == null) {
            InputStream current = rawStream;
            for (StreamTransform<? extends InputStream> transform : inPlaceTransforms) {
                current = transform.apply(current);
            }
            view = current;
        }
        return view;
    }

    /**
     * Builder for {@link S3Object}.
     */
    public static final class Builder {
        private String bucket;
        private String key;
        private String versionId;
        private S3Client s3Client;
        private boolean gzip;

        private Builder() {
        }

        /**
         * Sets the S3 bucket (required).
         *
         * @param bucket the bucket name
         * @return this builder
         */
        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /**
         * Sets the S3 object key (required).
         *
         * @param key the object key
         * @return this builder
         */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * Sets the object version id for versioned buckets.
         *
         * @param versionId the version id
         * @return this builder
         */
        public Builder versionId(String versionId) {
            this.versionId = versionId;
            return this;
        }

        /**
         * Sets the S3 client. If omitted, a default client is created
         * with {@code UrlConnectionHttpClient} and {@code AWS_REGION}.
         *
         * @param s3Client the client
         * @return this builder
         */
        public Builder s3Client(S3Client s3Client) {
            this.s3Client = s3Client;
            return this;
        }

        /**
         * When {@code true}, gunzips the object as it is read (in place).
         * Seek is then unsupported. Apply seek on the raw object first, or omit this
         * flag and call {@link S3Object#transform(StreamTransform)} instead.
         *
         * @param gzip whether to apply {@link GzipTransform}
         * @return this builder
         */
        public Builder gzip(boolean gzip) {
            this.gzip = gzip;
            return this;
        }

        /**
         * Builds a seekable stream for the configured object.
         * No S3 API call is made until the first read or {@link S3Object#size()}.
         *
         * @return a new {@link S3Object}
         */
        public S3Object build() {
            if (bucket == null || bucket.isEmpty()) {
                throw new StreamingException("bucket is required");
            }
            if (key == null || key.isEmpty()) {
                throw new StreamingException("key is required");
            }
            S3Client client = s3Client != null ? s3Client : DefaultS3ClientFactory.get();
            S3Object object = new S3Object(new S3SeekableInputStream(bucket, key, versionId, client));
            if (gzip) {
                object.transformInPlace(new GzipTransform());
            }
            return object;
        }
    }
}
