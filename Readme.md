# BCP Spring JDBC Oracle

Template (starter) form spring-jdbc with Oracle DB

### Config Env:

Run `create-table.sql` script:

```sql
CREATE TABLE CUSTOMER_TST 
(
  ID NUMBER NOT NULL 
, FIRST_NAME VARCHAR2(50) 
, LAST_NAME VARCHAR2(50) 
, CONSTRAINT CUSTOMER_TST_PK PRIMARY KEY 
  (ID) ENABLE 
);
```

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