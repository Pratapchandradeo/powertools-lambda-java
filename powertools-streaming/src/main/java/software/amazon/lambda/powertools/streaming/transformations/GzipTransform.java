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
import java.util.zip.GZIPInputStream;

import software.amazon.lambda.powertools.streaming.StreamTransform;

/**
 * Gunzips a stream using {@link GZIPInputStream}.
 * <p>
 * Seek is not supported on the resulting stream. Apply gzip after any
 * {@link software.amazon.lambda.powertools.streaming.S3Object#seek(long,
 * software.amazon.lambda.powertools.streaming.SeekWhence)} on the raw object.
 * </p>
 */
public final class GzipTransform implements StreamTransform<InputStream> {

    @Override
    public InputStream apply(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        return new GZIPInputStream(input);
    }
}
