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

package software.amazon.lambda.powertools;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.lambda.powertools.testutils.Infrastructure.FUNCTION_NAME_OUTPUT;
import static software.amazon.lambda.powertools.testutils.lambda.LambdaInvoker.invokeFunction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.databind.JsonNode;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;
import software.amazon.lambda.powertools.testutils.Infrastructure;
import software.amazon.lambda.powertools.testutils.lambda.InvocationResult;
import software.amazon.lambda.powertools.utilities.JsonConfig;

class StreamingE2ET {

    private static final SdkHttpClient HTTP = UrlConnectionHttpClient.builder().build();
    private static final Region REGION = Region.of(System.getProperty("AWS_DEFAULT_REGION", "eu-west-1"));
    private static final S3Client S3 = S3Client.builder().httpClient(HTTP).region(REGION).build();

    private static Infrastructure infrastructure;
    private static String functionName;
    private static String bucketName;

    @BeforeAll
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    static void setup() throws IOException {
        bucketName = "streaming-e2e-" + UUID.randomUUID().toString().substring(0, 8);
        infrastructure = Infrastructure.builder()
                .testName(StreamingE2ET.class.getSimpleName())
                .pathToFunction("streaming")
                .largeMessagesBucket(bucketName)
                .timeoutInSeconds(60)
                .build();
        Map<String, String> outputs = infrastructure.deploy();
        functionName = outputs.get(FUNCTION_NAME_OUTPUT);

        S3.putBucketVersioning(PutBucketVersioningRequest.builder()
                .bucket(bucketName)
                .versioningConfiguration(VersioningConfiguration.builder()
                        .status(BucketVersioningStatus.ENABLED)
                        .build())
                .build());

        put("plain.txt", "hello world".getBytes(StandardCharsets.UTF_8));
        put("csv.txt", "name,value\nhello,world\n".getBytes(StandardCharsets.UTF_8));
        put("plain.txt.gz", gzip("hello world".getBytes(StandardCharsets.UTF_8)));
        put("csv.txt.gz", gzip("name,value\nhello,world\n".getBytes(StandardCharsets.UTF_8)));
        put("fileset.zip", zip());
    }

    @AfterAll
    static void tearDown() {
        if (infrastructure != null) {
            infrastructure.destroy();
        }
    }

    @Test
    void plainObject() throws Exception {
        JsonNode result = invoke(payload("plain.txt"));
        assertThat(result.get("size").asLong()).isEqualTo(11);
        assertThat(result.get("body").asText()).isEqualTo("hello world");
    }

    @Test
    void versionedObject() throws Exception {
        PutObjectResponse put = put("versioned.txt", "hello world".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> payload = payload("versioned.txt");
        payload.put("version_id", put.versionId());

        JsonNode result = invoke(payload);
        assertThat(result.get("body").asText()).isEqualTo("hello world");
    }

    @Test
    void missingKey() throws Exception {
        JsonNode result = invoke(payload("NOTEXISTENT.txt"));
        assertThat(result.get("error").asText()).isEqualTo("Not found");
    }

    @Test
    void csvConstructor() throws Exception {
        Map<String, Object> payload = payload("csv.txt");
        payload.put("is_csv", true);
        JsonNode result = invoke(payload);
        assertThat(result.get("body").get("name").asText()).isEqualTo("hello");
        assertThat(result.get("body").get("value").asText()).isEqualTo("world");
    }

    @Test
    void csvTransform() throws Exception {
        Map<String, Object> payload = payload("csv.txt");
        payload.put("transform_csv", true);
        JsonNode result = invoke(payload);
        assertThat(result.get("body").get("name").asText()).isEqualTo("hello");
    }

    @Test
    void csvTransformInPlace() throws Exception {
        Map<String, Object> payload = payload("csv.txt");
        payload.put("transform_csv", true);
        payload.put("in_place", true);
        JsonNode result = invoke(payload);
        assertThat(result.get("body").get("value").asText()).isEqualTo("world");
    }

    @Test
    void gzipConstructor() throws Exception {
        Map<String, Object> payload = payload("plain.txt.gz");
        payload.put("is_gzip", true);
        JsonNode result = invoke(payload);
        assertThat(result.get("body").asText()).isEqualTo("hello world");
    }

    @Test
    void gzipTransform() throws Exception {
        Map<String, Object> payload = payload("plain.txt.gz");
        payload.put("transform_gzip", true);
        JsonNode result = invoke(payload);
        assertThat(result.get("body").asText()).isEqualTo("hello world");
    }

    @Test
    void gzipCsvConstructor() throws Exception {
        Map<String, Object> payload = payload("csv.txt.gz");
        payload.put("is_gzip", true);
        payload.put("is_csv", true);
        JsonNode result = invoke(payload);
        assertThat(result.get("body").get("name").asText()).isEqualTo("hello");
    }

    @Test
    void zipTransform() throws Exception {
        Map<String, Object> payload = payload("fileset.zip");
        payload.put("transform_zip", true);
        JsonNode result = invoke(payload);
        assertThat(result.get("manifest").get(0).asText()).isEqualTo("1.txt");
        assertThat(result.get("manifest").get(1).asText()).isEqualTo("2.txt");
        assertThat(result.get("body").asText()).isEqualTo("This is file 2");
    }

    private static JsonNode invoke(Map<String, Object> payload) throws Exception {
        String json = JsonConfig.get().getObjectMapper().writeValueAsString(payload);
        InvocationResult invocation = invokeFunction(functionName, json);
        assertThat(invocation.getFunctionError())
                .describedAs("Lambda failed: %s %s", invocation.getFunctionError(), invocation.getResult())
                .isNull();
        return JsonConfig.get().getObjectMapper().readTree(invocation.getResult());
    }

    private static Map<String, Object> payload(String key) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bucket", bucketName);
        payload.put("key", key);
        return payload;
    }

    private static PutObjectResponse put(String key, byte[] body) {
        return S3.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(),
                RequestBody.fromBytes(body));
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private static byte[] zip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("1.txt"));
            zip.write("This is file 1".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("2.txt"));
            zip.write("This is file 2".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
