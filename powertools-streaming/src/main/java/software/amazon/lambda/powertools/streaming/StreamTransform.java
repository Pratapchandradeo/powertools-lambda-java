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

import java.io.IOException;
import java.io.InputStream;

/**
 * A data transformation applied to an S3 object stream.
 *
 * @param <T> the type produced by the transformation (another {@link InputStream},
 *            a row iterator, a ZIP archive, etc.)
 */
@FunctionalInterface
public interface StreamTransform<T> {

    /**
     * Applies this transformation to {@code input}.
     *
     * @param input the source stream (typically an {@link S3Object} or a previous transform)
     * @return the transformed view
     * @throws IOException if the transformation cannot be applied
     */
    T apply(InputStream input) throws IOException;
}
