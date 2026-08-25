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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import software.amazon.lambda.powertools.streaming.StreamTransform;

/**
 * Parses a CSV (or TSV) stream into row maps keyed by the header line.
 * <p>
 * Supports a configurable delimiter and basic RFC 4180 quoting
 * ({@code "field"} and escaped quotes as {@code ""}).
 * </p>
 */
public final class CsvTransform implements StreamTransform<Iterator<Map<String, String>>> {

    private final char delimiter;
    private final Charset charset;

    /**
     * UTF-8 CSV with comma delimiter.
     */
    public CsvTransform() {
        this(',', StandardCharsets.UTF_8);
    }

    /**
     * UTF-8 CSV with a custom delimiter (use {@code '\t'} for TSV).
     *
     * @param delimiter the field delimiter
     */
    public CsvTransform(char delimiter) {
        this(delimiter, StandardCharsets.UTF_8);
    }

    /**
     * CSV with a custom delimiter and character set.
     *
     * @param delimiter the field delimiter
     * @param charset   the text encoding
     */
    public CsvTransform(char delimiter, Charset charset) {
        if (charset == null) {
            throw new IllegalArgumentException("charset is required");
        }
        this.delimiter = delimiter;
        this.charset = charset;
    }

    @Override
    public Iterator<Map<String, String>> apply(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset));
        String headerLine = reader.readLine();
        if (headerLine == null || headerLine.isEmpty()) {
            reader.close();
            return new CsvRowIterator(reader, Collections.emptyList(), delimiter, true);
        }
        return new CsvRowIterator(reader, parseLine(headerLine, delimiter), delimiter, false);
    }

    static List<String> parseLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    /**
     * Iterator over CSV rows. Closing it closes the underlying reader/stream.
     */
    public static final class CsvRowIterator implements Iterator<Map<String, String>>, Closeable {
        private final BufferedReader reader;
        private final List<String> headers;
        private final char delimiter;
        private String nextLine;
        private boolean closed;

        CsvRowIterator(BufferedReader reader, List<String> headers, char delimiter, boolean closed) {
            this.reader = reader;
            this.headers = headers;
            this.delimiter = delimiter;
            this.closed = closed;
            this.nextLine = closed ? null : advance();
        }

        @Override
        public boolean hasNext() {
            return nextLine != null;
        }

        @Override
        public Map<String, String> next() {
            if (nextLine == null) {
                throw new NoSuchElementException();
            }
            List<String> values = parseLine(nextLine, delimiter);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String value = i < values.size() ? values.get(i) : "";
                row.put(headers.get(i), value);
            }
            nextLine = advance();
            return row;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                nextLine = null;
                reader.close();
            }
        }

        private String advance() {
            try {
                return reader.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
