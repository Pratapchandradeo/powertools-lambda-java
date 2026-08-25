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
import static software.amazon.lambda.powertools.streaming.S3TestSupport.CSV_BODY;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class CsvTransformTest {

    @Test
    void parsesHeaderAndRows() throws IOException {
        Iterator<Map<String, String>> rows = new CsvTransform().apply(new ByteArrayInputStream(CSV_BODY));

        assertThat(rows.hasNext()).isTrue();
        assertThat(rows.next()).containsEntry("name", "hello").containsEntry("value", "world");
        assertThat(rows.hasNext()).isFalse();
    }

    @Test
    void parsesTabSeparatedValues() throws IOException {
        byte[] tsv = "name\tvalue\nhello\tworld\n".getBytes(StandardCharsets.UTF_8);
        Iterator<Map<String, String>> rows = new CsvTransform('\t').apply(new ByteArrayInputStream(tsv));

        assertThat(rows.next()).containsEntry("name", "hello").containsEntry("value", "world");
    }

    @Test
    void parsesQuotedFieldsWithDelimiterInside() throws IOException {
        byte[] csv = "name,note\n\"Doe, Jane\",\"said \"\"hi\"\"\"\n".getBytes(StandardCharsets.UTF_8);
        Map<String, String> row = new CsvTransform().apply(new ByteArrayInputStream(csv)).next();

        assertThat(row).containsEntry("name", "Doe, Jane").containsEntry("note", "said \"hi\"");
    }

    @Test
    void missingColumnsBecomeEmptyStrings() throws IOException {
        byte[] csv = "a,b,c\n1,2\n".getBytes(StandardCharsets.UTF_8);
        Map<String, String> row = new CsvTransform().apply(new ByteArrayInputStream(csv)).next();

        assertThat(row).containsEntry("a", "1").containsEntry("b", "2").containsEntry("c", "");
    }

    @Test
    void extraColumnsAreIgnored() throws IOException {
        byte[] csv = "a,b\n1,2,3\n".getBytes(StandardCharsets.UTF_8);
        Map<String, String> row = new CsvTransform().apply(new ByteArrayInputStream(csv)).next();

        assertThat(row).containsOnlyKeys("a", "b").containsEntry("a", "1").containsEntry("b", "2");
    }

    @Test
    void emptyStreamYieldsNoRows() throws IOException {
        Iterator<Map<String, String>> rows = new CsvTransform().apply(new ByteArrayInputStream(new byte[0]));

        assertThat(rows.hasNext()).isFalse();
        assertThatThrownBy(rows::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void headerOnlyYieldsNoRows() throws IOException {
        Iterator<Map<String, String>> rows =
                new CsvTransform().apply(new ByteArrayInputStream("name,value\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(rows.hasNext()).isFalse();
    }

    @Test
    void multipleRowsPreserveOrder() throws IOException {
        byte[] csv = "id,name\n1,a\n2,b\n".getBytes(StandardCharsets.UTF_8);
        Iterator<Map<String, String>> rows = new CsvTransform().apply(new ByteArrayInputStream(csv));
        List<Map<String, String>> all = new ArrayList<>();
        rows.forEachRemaining(all::add);

        assertThat(all).hasSize(2);
        assertThat(all.get(0)).containsEntry("id", "1");
        assertThat(all.get(1)).containsEntry("id", "2");
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> new CsvTransform().apply(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullCharset() {
        assertThatThrownBy(() -> new CsvTransform(',', null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closeStopsIteration() throws IOException {
        CsvTransform.CsvRowIterator rows = (CsvTransform.CsvRowIterator) new CsvTransform()
                .apply(new ByteArrayInputStream("a,b\n1,2\n3,4\n".getBytes(StandardCharsets.UTF_8)));
        rows.close();

        assertThat(rows.hasNext()).isFalse();
    }
}
