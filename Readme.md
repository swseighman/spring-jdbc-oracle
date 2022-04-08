Spring JDBC Oracle

Configure database properties in `application.properties` file.

### Run Project

```console
mvn spring-boot:run
```
(or) passing parameters:
```console
mvn spring-boot:run \
 -Dspring-boot.run.arguments=" \
 --SPRING_DATASOURCE_USERNAME=BCP \
 --SPRING_DATASOURCE_PASSWORD=BCP \
 --SPRING_DATASOURCE_URL=jdbc:oracle:thin:@localhost:1522:xe"
 ```
