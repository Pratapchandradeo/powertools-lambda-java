build-ValidationFunction:
	mvn clean package -P native-image
	chmod +x target/hello-world
	cp target/hello-world $(ARTIFACTS_DIR)
	chmod +x src/main/config/bootstrap
	cp src/main/config/bootstrap $(ARTIFACTS_DIR)
