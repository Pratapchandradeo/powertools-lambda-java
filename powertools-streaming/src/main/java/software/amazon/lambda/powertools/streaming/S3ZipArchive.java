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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * A ZIP archive opened from a stream (typically an {@link S3Object}).
 * <p>
 * Entries are discovered sequentially with {@link java.util.zip.ZipInputStream}.
 * {@link #names()} and {@link #open(String)} rewind the source when it is
 * seekable ({@link S3Object}) or mark/reset capable. Closing this archive
 * does not close the source stream.
 * </p>
 */
public interface S3ZipArchive extends Closeable {

    /**
     * Returns the names of all entries in the archive (files and directories).
     *
     * @return entry names in archive order
     * @throws IOException if the archive cannot be read
     */
    List<String> names() throws IOException;

    /**
     * Opens the named entry as a stream. The caller should close the returned stream.
     * Closing it does not close the source {@link S3Object}.
     *
     * @param name the entry name as returned by {@link #names()}
     * @return the entry content
     * @throws IOException if the entry cannot be opened
     */
    InputStream open(String name) throws IOException;
}
