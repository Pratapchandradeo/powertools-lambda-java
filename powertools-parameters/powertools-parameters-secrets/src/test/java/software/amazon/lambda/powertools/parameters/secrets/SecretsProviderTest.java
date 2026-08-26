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

package software.amazon.lambda.powertools.parameters.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static software.amazon.lambda.powertools.parameters.transform.Transformer.json;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.APIErrorType;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretValueEntry;
import software.amazon.lambda.powertools.parameters.cache.CacheManager;
import software.amazon.lambda.powertools.parameters.transform.TransformationManager;

@ExtendWith(MockitoExtension.class)
class SecretsProviderTest {

    @Mock
    SecretsManagerClient client;

    @Mock
    TransformationManager transformationManager;

    @Captor
    ArgumentCaptor<GetSecretValueRequest> paramCaptor;

    @Captor
    ArgumentCaptor<BatchGetSecretValueRequest> batchCaptor;

    CacheManager cacheManager;

    SecretsProvider provider;

    @BeforeEach
    void init() {
        cacheManager = new CacheManager();
        provider = new SecretsProvider(cacheManager, transformationManager, client);
    }

    @Test
    void getValue() {
        String key = "Key1";
        String expectedValue = "Value1";
        GetSecretValueResponse response = GetSecretValueResponse.builder().secretString(expectedValue).build();
        Mockito.when(client.getSecretValue(paramCaptor.capture())).thenReturn(response);
        provider.withMaxAge(2, ChronoUnit.DAYS);

        String value = provider.getValue(key);

        assertThat(value).isEqualTo(expectedValue);
        assertThat(paramCaptor.getValue().secretId()).isEqualTo(key);
    }

    @Test
    void getValueBase64() {
        String key = "Key2";
        String expectedValue = "Value2";
        byte[] valueb64 = Base64.getEncoder().encode(expectedValue.getBytes());
        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretBinary(SdkBytes.fromByteArray(valueb64)).build();
        Mockito.when(client.getSecretValue(paramCaptor.capture())).thenReturn(response);

        String value = provider.getValue(key);

        assertThat(value).isEqualTo(expectedValue);
        assertThat(paramCaptor.getValue().secretId()).isEqualTo(key);
    }

    @Test
    void getMultipleValuesThrowsException() {
        // Act & Assert
        assertThatRuntimeException().isThrownBy(() -> provider.getMultipleValues("path"))
                .withMessage("Impossible to get multiple values from AWS Secrets Manager via path. "
                        + "Use getMultiple(List<String> names) instead.");
    }

    @Test
    void getMultiple_retrievesSecretsBySecretIdList() {
        List<String> names = List.of("db-password", "api-key");
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(
                        stringEntry("db-password", "secret-db"),
                        stringEntry("api-key", "secret-api"))
                .build();
        Mockito.when(client.batchGetSecretValue(batchCaptor.capture())).thenReturn(response);

        Map<String, String> result = provider.getMultiple(names);

        assertThat(result).containsExactly(
                Map.entry("db-password", "secret-db"),
                Map.entry("api-key", "secret-api"));
        BatchGetSecretValueRequest captured = batchCaptor.getValue();
        assertThat(captured.secretIdList()).containsExactly("db-password", "api-key");
        assertThat(captured.filters()).isNullOrEmpty();
    }

    @Test
    void getMultiple_supportsBinarySecretWithoutBase64Decoding() {
        String binaryValue = "binary-secret";
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(
                        stringEntry("name1", "Value1"),
                        SecretValueEntry.builder()
                                .name("name2")
                                .secretBinary(SdkBytes.fromUtf8String(binaryValue))
                                .build())
                .build();
        Mockito.when(client.batchGetSecretValue(batchCaptor.capture())).thenReturn(response);

        Map<String, String> result = provider.getMultiple(List.of("name1", "name2"));

        assertThat(result).containsEntry("name1", "Value1").containsEntry("name2", binaryValue);
        assertThat(batchCaptor.getValue().secretIdList()).containsExactly("name1", "name2");
    }

    @Test
    void getMultiple_rejectsNullOrEmptyOrBlankNames() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.getMultiple((List<String>) null))
                .withMessage("You must provide at least one secret name");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.getMultiple(List.of()))
                .withMessage("You must provide at least one secret name");
        List<String> withBlank = new ArrayList<>();
        withBlank.add("valid");
        withBlank.add("  ");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.getMultiple(withBlank))
                .withMessage("Secret names must not be null or blank");
        List<String> withNull = new ArrayList<>();
        withNull.add("valid");
        withNull.add(null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> provider.getMultiple(withNull))
                .withMessage("Secret names must not be null or blank");

        Mockito.verify(client, never()).batchGetSecretValue(any(BatchGetSecretValueRequest.class));
    }

    @Test
    void getMultiple_usesCacheOnSecondCallAndSeedsSingleGet() {
        List<String> names = List.of("name1", "name2");
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(
                        stringEntry("name1", "Value1"),
                        stringEntry("name2", "Value2"))
                .build();
        Mockito.when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenReturn(response);

        Map<String, String> first = provider.getMultiple(names);
        Map<String, String> second = provider.getMultiple(names);
        String cachedSingle = provider.get("name1");

        assertThat(first).containsEntry("name1", "Value1").containsEntry("name2", "Value2");
        assertThat(second).isEqualTo(first);
        assertThat(cachedSingle).isEqualTo("Value1");
        Mockito.verify(client, times(1)).batchGetSecretValue(any(BatchGetSecretValueRequest.class));
        Mockito.verify(client, never()).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    void getMultiple_fetchesOnlyUncachedNames() {
        BatchGetSecretValueResponse firstResponse = BatchGetSecretValueResponse.builder()
                .secretValues(
                        stringEntry("name1", "Value1"),
                        stringEntry("name2", "Value2"))
                .build();
        BatchGetSecretValueResponse secondResponse = BatchGetSecretValueResponse.builder()
                .secretValues(stringEntry("name3", "Value3"))
                .build();
        Mockito.when(client.batchGetSecretValue(batchCaptor.capture()))
                .thenReturn(firstResponse, secondResponse);

        provider.getMultiple(List.of("name1", "name2"));
        Map<String, String> result = provider.getMultiple(List.of("name1", "name2", "name3"));

        assertThat(result).containsExactly(
                Map.entry("name1", "Value1"),
                Map.entry("name2", "Value2"),
                Map.entry("name3", "Value3"));
        List<BatchGetSecretValueRequest> requests = batchCaptor.getAllValues();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).secretIdList()).containsExactly("name1", "name2");
        assertThat(requests.get(1).secretIdList()).containsExactly("name3");
    }

    @Test
    void getMultiple_chunksRequestsOverApiLimit() {
        List<String> names = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "name" + i)
                .collect(Collectors.toList());
        Mockito.when(client.batchGetSecretValue(batchCaptor.capture()))
                .thenAnswer(invocation -> {
                    BatchGetSecretValueRequest request = invocation.getArgument(0);
                    List<SecretValueEntry> entries = request.secretIdList().stream()
                            .map(name -> stringEntry(name, "value-" + name))
                            .collect(Collectors.toList());
                    return BatchGetSecretValueResponse.builder().secretValues(entries).build();
                });

        Map<String, String> result = provider.getMultiple(names);

        assertThat(result).hasSize(21);
        assertThat(result.get("name1")).isEqualTo("value-name1");
        assertThat(result.get("name21")).isEqualTo("value-name21");
        List<BatchGetSecretValueRequest> requests = batchCaptor.getAllValues();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).secretIdList()).hasSize(SecretsProvider.BATCH_GET_SECRET_VALUE_LIMIT);
        assertThat(requests.get(1).secretIdList()).containsExactly("name21");
        assertThat(requests.get(0).filters()).isNullOrEmpty();
        assertThat(requests.get(1).filters()).isNullOrEmpty();
    }

    @Test
    void getMultiple_throwsWhenApiReturnsErrors() {
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(stringEntry("name1", "Value1"))
                .errors(APIErrorType.builder()
                        .secretId("name2")
                        .errorCode("AccessDeniedException")
                        .message("not allowed")
                        .build())
                .build();
        Mockito.when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenReturn(response);

        assertThatIllegalStateException()
                .isThrownBy(() -> provider.getMultiple(List.of("name1", "name2")))
                .withMessageContaining("name2")
                .withMessageContaining("AccessDeniedException")
                .withMessageContaining("not allowed");
    }

    @Test
    void getMultiple_throwsWhenRequestedSecretIsMissingFromResponse() {
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(stringEntry("name1", "Value1"))
                .build();
        Mockito.when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenReturn(response);

        assertThatIllegalStateException()
                .isThrownBy(() -> provider.getMultiple(List.of("name1", "missing")))
                .withMessageContaining("missing");
    }

    @Test
    void getMultiple_keysResultByRequestedArn() {
        String arn = "arn:aws:secretsmanager:us-east-1:123456789012:secret:db-password-AbCdEf";
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(SecretValueEntry.builder()
                        .name("db-password")
                        .arn(arn)
                        .secretString("secret-db")
                        .build())
                .build();
        Mockito.when(client.batchGetSecretValue(batchCaptor.capture())).thenReturn(response);

        Map<String, String> result = provider.getMultiple(List.of(arn));

        assertThat(result).containsExactly(Map.entry(arn, "secret-db"));
        assertThat(result).doesNotContainKey("db-password");
        assertThat(batchCaptor.getValue().secretIdList()).containsExactly(arn);
    }

    @Test
    void getMultiple_fluentWithMaxAgeStillCallsBatchApiOnce() {
        List<String> names = List.of("name1", "name2");
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(
                        stringEntry("name1", "Value1"),
                        stringEntry("name2", "Value2"))
                .build();
        Mockito.when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenReturn(response);

        Map<String, String> first = provider.withMaxAge(2, ChronoUnit.DAYS).getMultiple(names);
        Map<String, String> second = provider.getMultiple(names);

        assertThat(first).containsEntry("name1", "Value1");
        assertThat(second).isEqualTo(first);
        Mockito.verify(client, times(1)).batchGetSecretValue(any(BatchGetSecretValueRequest.class));
    }

    @Test
    void getMultiple_deduplicatesNamesInAwsRequest() {
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(stringEntry("name1", "Value1"))
                .build();
        Mockito.when(client.batchGetSecretValue(batchCaptor.capture())).thenReturn(response);

        Map<String, String> result = provider.getMultiple(List.of("name1", "name1"));

        assertThat(result).containsExactly(Map.entry("name1", "Value1"));
        assertThat(batchCaptor.getValue().secretIdList()).containsExactly("name1");
    }

    @Test
    void getMultiple_throwsWhenSecretHasNeitherStringNorBinary() {
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                .secretValues(SecretValueEntry.builder().name("empty-secret").build())
                .build();
        Mockito.when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenReturn(response);

        assertThatIllegalStateException()
                .isThrownBy(() -> provider.getMultiple(List.of("empty-secret")))
                .withMessageContaining("empty-secret")
                .withMessageContaining("SecretString or SecretBinary");
    }

    private static SecretValueEntry stringEntry(String name, String value) {
        return SecretValueEntry.builder().name(name).secretString(value).build();
    }

    @Test
    void testGetSecretsProvider_withoutParameter_shouldCreateDefaultClient() {
        // Act
        SecretsProvider secretsProvider = SecretsProvider.builder()
                .build();

        // Assert
        assertNotNull(secretsProvider);
        assertNotNull(secretsProvider.getClient());
    }

    @Test
    void testGetSecretsProvider_withoutParameter_shouldHaveDefaultTransformationManager() {
        // Act
        SecretsProvider secretsProvider = SecretsProvider.builder()
                .build();
        // Assert
        assertDoesNotThrow(() -> secretsProvider.withTransformation(json));
    }
}
