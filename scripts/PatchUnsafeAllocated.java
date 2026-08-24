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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds {@code "unsafeAllocated": true} to generated GraalVM reflect-config.json
 * entries for Mockito subclass mocks. The tracing agent does not emit this flag
 * for Objenesis {@code Unsafe.allocateInstance()} on GraalVM 21.0.10+.
 */
public class PatchUnsafeAllocated {

    private static final String MOCKITO_MOCK_MARKER = "$MockitoMock$";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: PatchUnsafeAllocated <agent-output-dir>");
            System.exit(1);
        }
        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            return;
        }
        Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals("reflect-config.json"))
                .forEach(path -> {
                    try {
                        patchFile(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    static void patchFile(Path path) throws IOException {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        Object parsed = new Parser(original).parseValue();
        if (!(parsed instanceof List)) {
            return;
        }
        boolean changed = false;
        for (Object entry : (List<?>) parsed) {
            if (!(entry instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) entry;
            Object name = object.get("name");
            if (name instanceof String && ((String) name).contains(MOCKITO_MOCK_MARKER)
                    && !Boolean.TRUE.equals(object.get("unsafeAllocated"))) {
                object.put("unsafeAllocated", Boolean.TRUE);
                changed = true;
            }
        }
        if (changed) {
            Files.writeString(path, JsonWriter.write(parsed), StandardCharsets.UTF_8);
        }
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = text.charAt(index);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                return parseNull();
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            throw new IllegalArgumentException("Unexpected character '" + c + "' at " + index);
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<Object>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c == '\\') {
                    if (index >= text.length()) {
                        throw new IllegalArgumentException("Unterminated escape at " + index);
                    }
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            builder.append(escaped);
                            break;
                        case 'b':
                            builder.append('\b');
                            break;
                        case 'f':
                            builder.append('\f');
                            break;
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'u':
                            if (index + 4 > text.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape at " + index);
                            }
                            builder.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                            index += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid escape '\\" + escaped + "'");
                    }
                } else {
                    builder.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean at " + index);
        }

        private Object parseNull() {
            if (text.startsWith("null", index)) {
                index += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null at " + index);
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < text.length() && isNumberChar(text.charAt(index))) {
                index++;
            }
            return Double.valueOf(text.substring(start, index));
        }

        private static boolean isNumberChar(char c) {
            return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!peek(expected)) {
                throw new IllegalArgumentException("Expected '" + expected + "' at " + index);
            }
            index++;
        }
    }

    private static final class JsonWriter {
        static String write(Object value) {
            StringBuilder builder = new StringBuilder();
            write(builder, value);
            builder.append('\n');
            return builder.toString();
        }

        private static void write(StringBuilder builder, Object value) {
            if (value == null) {
                builder.append("null");
            } else if (value instanceof String) {
                writeString(builder, (String) value);
            } else if (value instanceof Boolean) {
                builder.append(value);
            } else if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (number == Math.rint(number) && !Double.isInfinite(number)) {
                    builder.append((long) number);
                } else {
                    builder.append(number);
                }
            } else if (value instanceof List) {
                builder.append('[');
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    write(builder, list.get(i));
                }
                builder.append(']');
            } else if (value instanceof Map) {
                builder.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    writeString(builder, String.valueOf(entry.getKey()));
                    builder.append(':');
                    write(builder, entry.getValue());
                }
                builder.append('}');
            } else {
                throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
            }
        }

        private static void writeString(StringBuilder builder, String value) {
            builder.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"':
                        builder.append("\\\"");
                        break;
                    case '\\':
                        builder.append("\\\\");
                        break;
                    case '\b':
                        builder.append("\\b");
                        break;
                    case '\f':
                        builder.append("\\f");
                        break;
                    case '\n':
                        builder.append("\\n");
                        break;
                    case '\r':
                        builder.append("\\r");
                        break;
                    case '\t':
                        builder.append("\\t");
                        break;
                    default:
                        if (c < 0x20) {
                            builder.append(String.format("\\u%04x", (int) c));
                        } else {
                            builder.append(c);
                        }
                }
            }
            builder.append('"');
        }
    }
}
