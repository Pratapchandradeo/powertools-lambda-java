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

package software.amazon.lambda.powertools.streaming.internal;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.lambda.powertools.streaming.SeekWhence;
import software.amazon.lambda.powertools.streaming.StreamingException;

/**
 * Seekable {@link InputStream} over an S3 object.
 * <p>
 * Data is fetched lazily with {@code GetObject} and an HTTP Range header
 * ({@code bytes={position}-}). Seeking aborts the current HTTP body (without
 * draining it) and the next read opens a new ranged request.
 * Object size is loaded once via {@code HeadObject}.
 */
public final class S3SeekableInputStream extends InputStream {
    private static final Logger LOG = LoggerFactory.getLogger(S3SeekableInputStream.class);

    private final String bucket;
    private final String key;
    private final String versionId;
    private final S3Client s3Client;

    private long position;
    private Long cachedSize;
    private ResponseInputStream<GetObjectResponse> body;
    private boolean closed;
    private boolean retried;
    private boolean exhausted;
    private long markPosition = -1;

    public S3SeekableInputStream(String bucket, String key, String versionId, S3Client s3Client) {
        this.bucket = bucket;
        this.key = key;
        this.versionId = versionId;
        this.s3Client = s3Client;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    public String getVersionId() {
        return versionId;
    }

    public long position() {
        return position;
    }

    public boolean isClosed() {
        return closed;
    }

    public long size() {
        if (cachedSize == null) {
            try {
                HeadObjectRequest.Builder request = HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(key);
                if (versionId != null) {
                    request.versionId(versionId);
                }
                Long contentLength = s3Client.headObject(request.build()).contentLength();
                cachedSize = contentLength == null ? 0L : contentLength;
            } catch (S3Exception e) {
                throw wrapS3Exception("Failed to get size of S3 object", e);
            } catch (SdkException e) {
                throw new StreamingException(formatObject() + ": failed to get object size", e);
            }
        }
        return cachedSize;
    }

    public long seek(long offset, SeekWhence whence) {
        if (closed) {
            throw new StreamingException("Stream is closed");
        }
        if (whence == null) {
            throw new StreamingException("whence must not be null");
        }

        long target;
        switch (whence) {
            case SET:
                target = offset;
                break;
            case CURRENT:
                target = position + offset;
                break;
            case END:
                target = size() + offset;
                break;
            default:
                throw new StreamingException("Unsupported whence: " + whence);
        }
        if (target < 0) {
            throw new StreamingException("Cannot seek to negative position: " + target);
        }
        if (target != position) {
            LOG.debug("Seeking from {} to {} ({})", position, target, whence);
            abortBody();
            position = target;
            exhausted = false;
        }
        return position;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int count = read(one, 0, 1);
        if (count == -1) {
            return -1;
        }
        return one[0] & 0xFF;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        checkClosed();
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (offset < 0 || length < 0 || length > buffer.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) {
            return 0;
        }
        if (cachedSize != null && position >= cachedSize) {
            return -1;
        }
        try {
            ensureOpen();
            if (exhausted || body == null) {
                return -1;
            }
            int count = body.read(buffer, offset, length);
            if (count > 0) {
                position += count;
            }
            retried = false;
            return count;
        } catch (IOException e) {
            return retryRead(buffer, offset, length, e);
        }
    }

    @Override
    public long skip(long n) {
        if (n <= 0 || closed) {
            return 0;
        }
        long current = position;
        seek(n, SeekWhence.CURRENT);
        return position - current;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(int readlimit) {
        markPosition = position;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (markPosition < 0) {
            throw new IOException("Stream has not been marked");
        }
        seek(markPosition, SeekWhence.SET);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        abortBody();
    }

    private int retryRead(byte[] buffer, int offset, int length, IOException cause) throws IOException {
        if (retried) {
            throw cause;
        }
        LOG.debug("Read failed at position {}, reopening ranged GetObject", position, cause);
        retried = true;
        abortBody();
        try {
            ensureOpen();
            if (exhausted || body == null) {
                return -1;
            }
            int count = body.read(buffer, offset, length);
            if (count > 0) {
                position += count;
            }
            return count;
        } catch (IOException e) {
            e.addSuppressed(cause);
            throw e;
        }
    }

    private void ensureOpen() throws IOException {
        checkClosed();
        if (exhausted) {
            return;
        }
        if (body == null) {
            openAtCurrentPosition();
        }
    }

    private void openAtCurrentPosition() {
        if (cachedSize != null && position >= cachedSize) {
            exhausted = true;
            return;
        }
        try {
            GetObjectRequest.Builder request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range("bytes=" + position + "-");
            if (versionId != null) {
                request.versionId(versionId);
            }
            LOG.debug("Opening S3 stream at {}", request.build().range());
            body = s3Client.getObject(request.build());
            exhausted = false;
        } catch (S3Exception e) {
            if (isRangeNotSatisfiable(e)) {
                exhausted = true;
                body = null;
                return;
            }
            throw wrapS3Exception("Failed to read S3 object", e);
        } catch (SdkException e) {
            throw new StreamingException(formatObject() + ": failed to read object", e);
        }
    }

    private void abortBody() {
        if (body == null) {
            return;
        }
        try {
            body.abort();
        } catch (RuntimeException e) {
            LOG.debug("Aborting S3 response stream failed", e);
        } finally {
            body = null;
        }
    }

    private void checkClosed() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
    }

    private StreamingException wrapS3Exception(String action, S3Exception e) {
        if (isNotFound(e)) {
            return new StreamingException(formatObject() + ": not found", e);
        }
        return new StreamingException(formatObject() + ": " + action, e);
    }

    private static boolean isNotFound(S3Exception e) {
        if (e.statusCode() == 404) {
            return true;
        }
        AwsErrorDetails details = e.awsErrorDetails();
        if (details == null || details.errorCode() == null) {
            return false;
        }
        String code = details.errorCode();
        return "NoSuchKey".equals(code) || "NotFound".equals(code) || "NoSuchVersion".equals(code);
    }

    private static boolean isRangeNotSatisfiable(S3Exception e) {
        if (e.statusCode() == 416) {
            return true;
        }
        AwsErrorDetails details = e.awsErrorDetails();
        if (details == null || details.errorCode() == null) {
            return false;
        }
        String code = details.errorCode();
        return "InvalidRange".equals(code) || "RequestedRangeNotSatisfiable".equals(code);
    }

    private String formatObject() {
        if (versionId != null) {
            return "s3://" + bucket + "/" + key + "?versionId=" + versionId;
        }
        return "s3://" + bucket + "/" + key;
    }
}
