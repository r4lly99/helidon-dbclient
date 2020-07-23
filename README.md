# Helidon SE DbClient & Messaging with Kafka Examples

## Prerequisites
* Java 11+ 
* Docker
* Mongodb
* [Kafka bootstrap server](../README.md) running on `host:9092`

## Build & Run
1. `mvn clean install`
2. `java -jar target/helidon-dbclient.jar`
3. Visit http://localhost:7001 or 
4. Create db pokemon and collection pokemons in MongoDB
5. Send POST `http://localhost:7001/db/konuchi/type/water` 
6. GET `http://localhost:7001/db`
