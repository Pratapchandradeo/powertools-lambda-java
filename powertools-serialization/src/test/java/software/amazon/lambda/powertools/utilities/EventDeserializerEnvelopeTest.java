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

package software.amazon.lambda.powertools.utilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static software.amazon.lambda.powertools.utilities.EventDeserializer.extractDataFrom;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.tests.annotations.Event;

import software.amazon.lambda.powertools.utilities.model.DummyInput;
import software.amazon.lambda.powertools.utilities.model.Product;

class EventDeserializerEnvelopeTest {

    @ParameterizedTest
    @Event(value = "sqs_sns_event.json", type = SQSEvent.class)
    void extractSqsMessageWithSqsSnsPath_shouldReturnInnerPayload(SQSEvent event) {
        SQSEvent.SQSMessage message = event.getRecords().get(0);

        DummyInput input = extractDataFrom(message, EventPaths.SQS_SNS).as(DummyInput.class);

        assertThat(input).isEqualTo(new DummyInput("dummy", "dummy-name"));
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_event.json", type = SQSEvent.class)
    void extractSqsSnsMessageWithoutEnvelope_shouldReturnRawSnsNotification(SQSEvent event) {
        String body = extractDataFrom(event.getRecords().get(0)).as(String.class);

        assertThat(body)
                .contains("\"Type\": \"Notification\"")
                .contains("\"Message\":");
    }

    @ParameterizedTest
    @Event(value = "sqs_event.json", type = SQSEvent.class)
    void extractWithBlankEnvelope_shouldUseBuiltInUnwrap(SQSEvent event) {
        List<Product> withNull = extractDataFrom(event, null).asListOf(Product.class);
        List<Product> withBlank = extractDataFrom(event, "  ").asListOf(Product.class);
        List<Product> builtIn = extractDataFrom(event).asListOf(Product.class);

        assertThat(withNull).isEqualTo(builtIn);
        assertThat(withBlank).isEqualTo(builtIn);
        assertThat(builtIn.get(0)).isEqualTo(new Product(1234, "product", 42));
    }

    @ParameterizedTest
    @Event(value = "sqs_sns_event.json", type = SQSEvent.class)
    void extractWithMissingEnvelope_shouldThrow(SQSEvent event) {
        assertThatThrownBy(() -> extractDataFrom(event.getRecords().get(0), "does.not.exist").as(DummyInput.class))
                .isInstanceOf(EventDeserializationException.class)
                .hasMessage("Envelope not found in the object");
    }
}
