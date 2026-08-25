# Powertools for AWS Lambda (Java) - Streaming Example

This sample streams a CSV (optionally gzip-compressed) from S3 using
`powertools-streaming`. For the full API, see the
[streaming documentation](https://docs.powertools.aws.dev/lambda-java/utilities/streaming/).

The handler [S3CsvHandler](src/main/java/org/demo/streaming/S3CsvHandler.java)
reads each row without loading the object into memory.

## Deploy

This sample uses the Serverless Application Model (SAM). See
[the examples directory](../README.md) for SAM setup.

```bash
sam build
sam deploy --guided
```

## Try it

Upload a CSV (or `.csv.gz`) to the stack's `InputBucket`:

```text
name,value
hello,world
```

```bash
aws s3 cp sample.csv.gz s3://$(aws cloudformation describe-stacks \
  --stack-name $MY_STACK \
  --query "Stacks[0].Outputs[?OutputKey=='Bucket'].OutputValue" \
  --output text)

sam logs --tail --stack-name $MY_STACK
```

You should see one log line per CSV row. The function only needs `s3:GetObject`
on that bucket.
