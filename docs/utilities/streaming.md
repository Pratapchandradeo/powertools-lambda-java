---
title: Streaming
description: Utility
---

The streaming utility handles Amazon S3 objects larger than the available Lambda memory as a seekable byte stream.

**Key features**

* Stream S3 objects with an `InputStream` interface and minimal memory use
* Built-in gzip, CSV/TSV, and ZIP transformations
* Custom `StreamTransform` implementations
* HTTP Range reads so you can seek, skip, or read the last bytes without downloading the prefix

## Background

Loading a large S3 object into a `byte[]` or `String` can cause out-of-memory errors or timeouts. Objects are often gzip-compressed, stored as CSV, or packed in a ZIP, which adds more non-business logic.

This utility fetches data as you consume it (`GetObject` with `Range: bytes={position}-`) and can apply transformations on the stream. You can process one row, a few rows, or the whole object while using only a few megabytes.

!!! warning "This is not Large Messages"
    [`powertools-large-messages`](./large_messages.md) loads an entire SQS/SNS offload payload from S3 into a `String`. Use **Streaming** for large datasets on S3. Use **Large Messages** for extended SQS/SNS payloads.

## Install

No AspectJ configuration is required. This module is functional-only.

=== "Maven"

    ```xml hl_lines="3-7"
    <dependencies>
        ...
        <dependency>
            <groupId>software.amazon.lambda</groupId>
            <artifactId>powertools-streaming</artifactId>
            <version>{{ powertools.version }}</version>
        </dependency>
        ...
    </dependencies>
    ```

=== "Gradle"

    ```groovy hl_lines="6"
        repositories {
            mavenCentral()
        }

        dependencies {
            implementation 'software.amazon.lambda:powertools-streaming:{{ powertools.version }}'
        }

        sourceCompatibility = 11
        targetCompatibility = 11
    ```

## IAM Permissions

The function needs these actions on the objects it reads:

* `s3:GetObject`
* `s3:GetObjectVersion` — only if you pass `versionId`
* `kms:Decrypt` — only if the object is encrypted with a customer-managed KMS key

`HeadObject` (used by `size()` and `seek(..., END)`) is covered by `s3:GetObject` on a known key.

## Getting started

### Streaming from an S3 object

Create an `S3Object` with bucket and key. Bytes are fetched only as you read.

=== "Non-versioned object"

    ```java hl_lines="12-15"
    import software.amazon.lambda.powertools.streaming.S3Object;

    public class Handler implements RequestHandler<S3Event, String> {
        @Override
        public String handleRequest(S3Event event, Context context) {
            var record = event.getRecords().get(0).getS3();
            String bucket = record.getBucket().getName();
            String key = URLDecoder.decode(record.getObject().getKey().replace("+", "%2B"), StandardCharsets.UTF_8);

            try (S3Object s3 = S3Object.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()) {
                s3.lines().forEach(line -> process(line));
            }
            return "ok";
        }
    }
    ```

=== "Versioned object"

    ```java hl_lines="8"
    try (S3Object s3 = S3Object.builder()
            .bucket(bucket)
            .key(key)
            .versionId(versionId)
            .s3Client(customClient) // optional
            .build()) {
        byte[] head = s3.readNBytes(64);
    }
    ```

Closing `S3Object` does not close the `S3Client`. If you omit `s3Client()`, a default client is created with `UrlConnectionHttpClient` and `AWS_REGION`.

## Data transformations

Transformations are a pipeline. Gzip can be enabled on the builder (still bytes). CSV and ZIP change the result type, so they are applied with `transform(...)`.

### Gzip

=== "Builder flag"

    ```java
    try (S3Object s3 = S3Object.builder()
            .bucket(bucket)
            .key(key)
            .gzip(true)
            .build()) {
        s3.lines().forEach(this::process);
    }
    ```

=== "transform()"

    ```java
    try (S3Object s3 = S3Object.builder().bucket(bucket).key(key).build();
            InputStream gunzipped = s3.transform(new GzipTransform())) {
        // read decompressed bytes
    }
    ```

Seek is **not** supported after an in-place gzip. Seek on the raw object first, then wrap.

### CSV / TSV

Each row is a `Map<String, String>` keyed by the header line. Quoted fields (`"Doe, Jane"`) and escaped quotes (`""`) are supported.

=== "CSV"

    ```java
    try (S3Object s3 = S3Object.builder().bucket(bucket).key(key).gzip(true).build()) {
        Iterator<Map<String, String>> rows = s3.csvRows();
        while (rows.hasNext()) {
            Map<String, String> row = rows.next();
            process(row.get("name"), row.get("value"));
        }
    }
    ```

=== "TSV"

    ```java
    Iterator<Map<String, String>> rows = s3.csvRows('\t');
    // or: s3.transform(new CsvTransform('\t'));
    ```

### ZIP

A ZIP archive is many files, so ZIP is not composed with gzip/CSV on the same stream. The source must be rewindable (`S3Object` or mark/reset). Entries are scanned from the start (`ZipInputStream`).

```java
try (S3Object s3 = S3Object.builder().bucket(bucket).key(key).build();
        S3ZipArchive zip = s3.transform(new ZipTransform());
        InputStream inner = zip.open("filename.txt")) {
    List<String> names = zip.names();
    // read inner
}
```

Closing the archive or an entry does not close the `S3Object`.

### Built-in transformations

| Name | Description | Class |
| --- | --- | --- |
| **Gzip** | Gunzips the stream (`GZIPInputStream`) | `GzipTransform` |
| **CSV** | Header row → `Iterator<Map<String, String>>` | `CsvTransform` |
| **ZIP** | Sequential archive; `names()` / `open(name)` | `ZipTransform` |

### Custom transformations

Implement `StreamTransform<T>`:

```java
public final class UppercaseTransform implements StreamTransform<InputStream> {
    @Override
    public InputStream apply(InputStream input) {
        return new FilterInputStream(input) {
            @Override
            public int read() throws IOException {
                int b = in.read();
                return b == -1 ? -1 : Character.toUpperCase(b);
            }
        };
    }
}

InputStream upper = s3.transform(new UppercaseTransform());
```

## Advanced

### Skipping or reading backwards

`S3Object` supports `seek(offset, SeekWhence)`. Seeking aborts the current HTTP body and the next read uses `Range: bytes={position}-`.

=== "Skip a fixed header"

    ```java
    s3.seek(headerBytes, SeekWhence.SET);
    s3.seek(100L * rowBytes, SeekWhence.CURRENT);
    ```

=== "Last N bytes"

    ```java
    s3.seek(-30, SeekWhence.END);
    byte[] tail = s3.readAllBytes();
    ```

`size()` issues one cached `HeadObject`. A range start past the object end (S3 416) is treated as EOF.

### Injecting an S3 client

```java
S3Client client = S3Client.builder()
        .httpClient(UrlConnectionHttpClient.create())
        .build();

try (S3Object s3 = S3Object.builder()
        .bucket(bucket)
        .key(key)
        .s3Client(client)
        .build()) {
    // ...
}
```

## Testing your code

Transforms accept any `InputStream`. Unit-test them without S3:

```java
@Test
void csvParsesHeader() throws IOException {
    byte[] payload = "name,value\nhello,world\n".getBytes(StandardCharsets.UTF_8);
    Iterator<Map<String, String>> rows =
            new CsvTransform().apply(new ByteArrayInputStream(payload));
    assertThat(rows.next()).containsEntry("name", "hello");
}
```

Mock `S3Client.getObject(...)` and return a `ResponseInputStream` (see the module unit tests) to assert `Range` headers.

## Known limitations

### AWS X-Ray segment size

Each Range GET is an S3 API call. If the S3 client is instrumented with Tracer, a large file can hit the [64 KB X-Ray segment size](https://docs.aws.amazon.com/general/latest/gr/xray.html#limits_xray){target="_blank"} limit.

Use tracer annotations on code that does **not** loop over `S3Object`.

### Other limits

* Not thread-safe. Do not share one `S3Object` across threads.
* Seek after in-place gzip is unsupported.
* ZIP `open(name)` / `names()` scan from the start (no random-access `ZipFile`).
* HTTP `Content-Encoding: gzip` on the object metadata can break Range requests. Application `.gz` keys (raw gzip bytes) are fine.
* This utility does not delete S3 objects and does not replace Large Messages.
