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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.APIErrorType;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretValueEntry;
import software.amazon.lambda.powertools.parameters.BaseProvider;
import software.amazon.lambda.powertools.parameters.cache.CacheManager;
import software.amazon.lambda.powertools.parameters.transform.TransformationManager;
import software.amazon.lambda.powertools.parameters.transform.Transformer;

/**
 * AWS Secrets Manager Parameter Provider<br/><br/>
 *
 * <u>Samples:</u>
 * <pre>
 *     SecretsProvider provider = SecretsProvider.builder().build();
 *
 *     String value = provider.get("key");
 *     System.out.println(value);
 *     >>> "value"
 *
 *     // Get a value and cache it for 30 seconds (all others values will now be cached for 30 seconds)
 *     String value = provider.defaultMaxAge(30, ChronoUnit.SECONDS).get("key");
 *
 *     // Get a value and cache it for 1 minute (all others values are cached for 5 seconds by default)
 *     String value = provider.withMaxAge(1, ChronoUnit.MINUTES).get("key");
 *
 *     // Get a base64 encoded value, decoded into a String, and store it in the cache
 *     String value = provider.withTransformation(Transformer.base64).get("key");
 *
 *     // Get a json value, transform it into an Object, and store it in the cache
 *     TargetObject = provider.withTransformation(Transformer.json).get("key", TargetObject.class);
 *
 *     // Get multiple secrets in one BatchGetSecretValue call
 *     Map&lt;String, String&gt; secrets = provider.getMultiple(List.of("db-password", "api-key"));
 * </pre>
 */
public class SecretsProvider extends BaseProvider {

    static final int BATCH_GET_SECRET_VALUE_LIMIT = 20;

    private final SecretsManagerClient client;

    /**
     * Use the {@link SecretsProviderBuilder} to create an instance!
     *
     * @param client custom client you would like to use.
     */
    SecretsProvider(CacheManager cacheManager, TransformationManager transformationManager,
                    SecretsManagerClient client) {
        super(cacheManager, transformationManager);
        this.client = client;
    }

    /**
     * Create a builder that can be used to configure and create a {@link SecretsProvider}.
     *
     * @return a new instance of {@link SecretsProviderBuilder}
     */
    public static SecretsProviderBuilder builder() {
        return new SecretsProviderBuilder();
    }

    /**
     * Create a SecretsProvider with all default settings.
     */
    public static SecretsProvider create() {
        return new SecretsProviderBuilder().build();
    }

    /**
     * Retrieve the parameter value from the AWS Secrets Manager.
     *
     * @param key key of the parameter
     * @return the value of the parameter identified by the key
     */
    @Override
    protected String getValue(String key) {
        GetSecretValueRequest request = GetSecretValueRequest.builder().secretId(key).build();

        String secretValue = client.getSecretValue(request).secretString();
        if (secretValue == null) {
            secretValue =
                    new String(Base64.getDecoder().decode(client.getSecretValue(request).secretBinary().asByteArray()),
                            UTF_8);
        }
        return secretValue;
    }

    /**
     * Path-based retrieval is not supported by AWS Secrets Manager.
     *
     * @throws UnsupportedOperationException always; use {@link #getMultiple(List)} instead
     */
    @Override
    protected Map<String, String> getMultipleValues(String path) {
        throw new UnsupportedOperationException(
                "Impossible to get multiple values from AWS Secrets Manager via path. "
                        + "Use getMultiple(List<String> names) instead.");
    }

    /**
     * Retrieve multiple secrets from AWS Secrets Manager using
     * {@code BatchGetSecretValue}.<br/><br/>
     *
     * Values are fetched in chunks of {@value #BATCH_GET_SECRET_VALUE_LIMIT} names
     * (the API limit for {@code SecretIdList}). Cached values are reused; only
     * uncached names are requested. Each retrieved value is stored in the cache
     * so a later {@link #get(String)} call can hit the cache.<br/><br/>
     *
     * <i>Does not support transformation.</i>
     *
     * @param names secret names or ARNs to retrieve; must not be null or empty
     * @return map of the requested identifiers to their secret values, in request order
     * @throws IllegalArgumentException if {@code names} is null, empty, or contains a blank name
     * @throws IllegalStateException    if a requested secret is missing or the API reports an error
     */
    public Map<String, String> getMultiple(List<String> names) {
        validateSecretNames(names);
        try {
            Map<String, String> result = new LinkedHashMap<>();
            List<String> toFetch = new ArrayList<>();
            for (String name : names) {
                Optional<Object> cached = cacheManager.getIfNotExpired(name, now());
                if (cached.isPresent() && cached.get() instanceof String) {
                    result.put(name, (String) cached.get());
                } else {
                    toFetch.add(name);
                }
            }

            if (!toFetch.isEmpty()) {
                Map<String, String> fetched = batchGetSecretValues(toFetch);
                for (String name : toFetch) {
                    String value = fetched.get(name);
                    cacheManager.putInCache(name, value);
                    result.put(name, value);
                }
            }
            return result;
        } finally {
            resetToDefaults();
        }
    }

    /**
     * Builder method to call before {@link #get(String)} or {@link #getMultiple(List)}
     * to set cache max age for the parameter to get.
     *
     * @param maxAge Maximum time to cache the parameter, before calling the underlying store
     * @param unit   Unit of time
     * @return this provider, so the {@link #getMultiple(List)} overload stays visible
     */
    @Override
    public SecretsProvider withMaxAge(int maxAge, ChronoUnit unit) {
        super.withMaxAge(maxAge, unit);
        return this;
    }

    /**
     * Builder method to call before {@link #get(String)} or {@link #get(String, Class)}
     * to provide a {@link Transformer}. Not used by {@link #getMultiple(List)}.
     *
     * @param transformerClass Class of the transformer to apply
     * @return this provider, so fluent calls stay on {@link SecretsProvider}
     */
    @Override
    @SuppressWarnings("rawtypes")
    public SecretsProvider withTransformation(Class<? extends Transformer> transformerClass) {
        super.withTransformation(transformerClass);
        return this;
    }

    private static void validateSecretNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("You must provide at least one secret name");
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Secret names must not be null or blank");
            }
        }
    }

    private Map<String, String> batchGetSecretValues(List<String> names) {
        List<String> distinctNames = new ArrayList<>();
        for (String name : names) {
            if (!distinctNames.contains(name)) {
                distinctNames.add(name);
            }
        }

        Map<String, String> fetched = new LinkedHashMap<>();
        for (int from = 0; from < distinctNames.size(); from += BATCH_GET_SECRET_VALUE_LIMIT) {
            int to = Math.min(from + BATCH_GET_SECRET_VALUE_LIMIT, distinctNames.size());
            fetched.putAll(fetchSecretChunk(distinctNames.subList(from, to)));
        }
        return fetched;
    }

    private Map<String, String> fetchSecretChunk(List<String> chunk) {
        BatchGetSecretValueResponse response = client.batchGetSecretValue(
                BatchGetSecretValueRequest.builder()
                        .secretIdList(chunk)
                        .build());

        if (response.hasErrors() && !response.errors().isEmpty()) {
            throw new IllegalStateException(
                    "Failed to retrieve one or more secrets from AWS Secrets Manager: "
                            + formatSecretErrors(response.errors()));
        }

        Map<String, SecretValueEntry> byId = indexSecretEntries(response);
        Map<String, String> result = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String requested : chunk) {
            SecretValueEntry entry = byId.get(requested);
            if (entry == null) {
                missing.add(requested);
                continue;
            }
            String value = extractSecretValue(entry);
            if (value == null) {
                throw new IllegalStateException(
                        "Secret '" + requested + "' did not contain a SecretString or SecretBinary");
            }
            result.put(requested, value);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "No secrets found matching the provided names: " + missing);
        }
        return result;
    }

    private static Map<String, SecretValueEntry> indexSecretEntries(BatchGetSecretValueResponse response) {
        Map<String, SecretValueEntry> byId = new HashMap<>();
        if (!response.hasSecretValues()) {
            return byId;
        }
        for (SecretValueEntry entry : response.secretValues()) {
            if (entry.name() != null) {
                byId.put(entry.name(), entry);
            }
            if (entry.arn() != null) {
                byId.put(entry.arn(), entry);
            }
        }
        return byId;
    }

    private static String formatSecretErrors(List<APIErrorType> errors) {
        return errors.stream()
                .map(error -> {
                    String detail = error.errorCode() != null ? error.errorCode() : "UnknownError";
                    if (error.message() != null) {
                        detail = detail + " (" + error.message() + ")";
                    }
                    return error.secretId() + ": " + detail;
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Prefer SecretString. Fall back to SecretBinary as UTF-8 text.
     * Unlike {@link #getValue(String)}, this does not issue a second API call
     * and does not Base64-decode bytes the SDK already decoded.
     */
    private static String extractSecretValue(SecretValueEntry entry) {
        if (entry.secretString() != null) {
            return entry.secretString();
        }
        SdkBytes secretBinary = entry.secretBinary();
        if (secretBinary != null) {
            return secretBinary.asUtf8String();
        }
        return null;
    }

    // For test purpose only
    SecretsManagerClient getClient() {
        return client;
    }

}
