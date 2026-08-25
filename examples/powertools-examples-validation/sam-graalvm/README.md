# Powertools for AWS Lambda (Java) - Validation Example with SAM on GraalVM

This project demonstrates the Validation utility deployed using [Serverless Application Model](https://aws.amazon.com/serverless/sam/) running as a GraalVM native image.

The handler [InboundValidation](src/main/java/org/demo/validation/InboundValidation.java) validates incoming HTTP requests
received from API Gateway against [schema.json](src/main/resources/schema.json). The schema file is registered in
`src/main/resources/META-INF/native-image/helloworld/resource-config.json` so native-image includes it.

## Configuration

- Set the environment to use GraalVM

```shell
export JAVA_HOME=<path to GraalVM>
```

## Build the sample application

- Build the Docker image that will be used as the environment for SAM build:

```shell
docker build --platform linux/amd64 . -t powertools-examples-validation-sam-graalvm
```

- Build the SAM project using the docker image

```shell
sam build --use-container --build-image powertools-examples-validation-sam-graalvm
```

#### [Optional] Building with -SNAPSHOT versions of PowerTools

- If you are testing the example with a -SNAPSHOT version of PowerTools, the maven build inside the docker image will fail. This is because the -SNAPSHOT version of the PowerTools library that you are working on is still not available in maven central/snapshot repository.
  To get around this, follow these steps:
    - Create the native image using the `docker` command below on your development machine. The native image is created in the `target` directory.
        - `` docker run --platform linux/amd64  -it -v `pwd`:`pwd` -w `pwd` -v ~/.m2:/root/.m2 powertools-examples-validation-sam-graalvm mvn clean -Pnative-image package -DskipTests ``
    - Edit the [`Makefile`](Makefile) remove this line
        - `mvn clean package -P native-image`
    - Build the SAM project using the docker image
        - `sam build --use-container --build-image powertools-examples-validation-sam-graalvm`

## Test the application

To test the validation, POST a JSON object shaped like our schema:

```bash
curl -X POST https://[REST-API-ID].execute-api.[REGION].amazonaws.com/Prod/hello/ -H "Content-Type: application/json" -d '{"id": 123,"name":"The Hitchhikers Guide to the Galaxy","price":10.99}'
```

If we break the schema - for instance, by removing one of the compulsory fields,
we will get an error back from our API and will see a `ValidationException` in the logs:

```bash
sam logs --tail --stack-name $MY_STACK
```
