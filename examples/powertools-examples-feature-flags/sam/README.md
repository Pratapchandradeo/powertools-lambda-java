# Powertools for AWS Lambda (Java) - Feature Flags Example

This project contains an example of a Lambda function using the feature flags module of Powertools for AWS Lambda
(Java). For more information on this module, please refer to the
[documentation](https://docs.powertools.aws.dev/lambda/java/utilities/feature_flags/).

The example deploys an AppConfig application with a freeform JSON feature-flag document and a function that evaluates
flags from API Gateway query parameters. See
[FeatureFlagsFunction.java](src/main/java/org/demo/featureflags/FeatureFlagsFunction.java) for the handler.

## Deploy the sample application

This sample is based on Serverless Application Model (SAM). To deploy it, check out the instructions for getting
started with SAM in [the examples directory](../../README.md)

```bash
cd examples/powertools-examples-feature-flags/sam
sam build
sam deploy --guided
```

## Test the application

Call the API with a customer context as query parameters:

```bash
# premium customer in the NL geo — premium_features and geo_customer_campaign are on
curl "https://[REST-API-ID].execute-api.[REGION].amazonaws.com/Prod/flags/?tier=premium&country=NL"

# standard customer — only the static campaign is on
curl "https://[REST-API-ID].execute-api.[REGION].amazonaws.com/Prod/flags/?tier=standard&country=US"
```

A premium request returns JSON similar to:

```json
{
  "premium_features": true,
  "ten_percent_off_campaign": true,
  "geo_customer_campaign": true,
  "enabled_features": [
    "premium_features",
    "ten_percent_off_campaign",
    "geo_customer_campaign"
  ]
}
```
