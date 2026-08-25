package software.amazon.lambda.powertools.batch.model;

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

import java.util.Objects;

/**
 * Payload used by issue #2163 SNS-in-SQS samples: {@code {"field":"dummy","name":"dummy-name"}}.
 */
public class DummyInput {
    private String field;
    private String name;

    public DummyInput() {
    }

    public DummyInput(String field, String name) {
        this.field = field;
        this.name = name;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DummyInput that = (DummyInput) o;
        return Objects.equals(field, that.field) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, name);
    }
}
