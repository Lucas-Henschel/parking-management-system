# Serviço de Estacionamento

Java 21, Spring Boot, PostgreSQL e RabbitMQ. Porta padrão: **8081**.

Na raiz, execute `docker compose up -d estacionamento-db rabbitmq`. Neste diretório,
execute `.\mvnw.cmd spring-boot:run` (Windows) ou `./mvnw spring-boot:run` (Linux).
O Flyway aplica as migrations automaticamente.

Swagger: http://localhost:8081/swagger-ui.html. Health: http://localhost:8081/actuator/health.