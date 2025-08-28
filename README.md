# 💳 Bank Cards API

## 📖 Описание
Bank Cards API — это учебный/демонстрационный backend на **Spring Boot 3 + Java 17**,  
предназначенный для управления пользователями, банковскими картами и переводами.

В проекте реализованы:
- JWT аутентификация и авторизация
- Роли `USER` и `ADMIN`
- Управление картами (создание, блокировка, активация, удаление)
- Переводы между картами (с идемпотентностью)
- Liquibase для миграций базы
- PostgreSQL как основная БД
- OpenAPI/Swagger для документации

---

## 🚀 Технологии
- Java 17
- Spring Boot 3 (Web, Security, Data JPA, Validation)
- PostgreSQL + Liquibase
- JWT (jjwt)
- Lombok, MapStruct
- Testcontainers, JUnit 5, Mockito
- Springdoc OpenAPI 3

---

## ⚙️ Запуск

### Требования
- JDK 17+
- Maven 3.9+
- Docker (если используешь контейнеризацию)

### Шаги
1. Клонировать репозиторий:
   ```bash
   git clone https://github.com/your-org/bank-cards-api.git
   cd bank-cards-api
   ```
2. Запустить PostgreSQL (локально или через Docker Compose):
    ```
    docker compose up -d
   ```
3. Собрать и запустить приложение:
    ```
   mvn clean install
   mvn spring-boot:run
   ```
4. Приложение будет доступно по адресу: http://localhost:8080
5. Добавить переменные окружение в .env:
    ```
   ПРИМЕР
   
    SERVER_PORT=8080
    DB_URL=jdbc:postgresql://localhost:5432/bankdb
    DB_USERNAME=bankuser
    DB_PASSWORD=bankpass
    
    JWT_SECRET=your-very-secret-key
    JWT_EXPIRATION_MS=86400000
    
    USERS_DEFAULT_PAGE_SIZE=10
    USERS_MAX_PAGE_SIZE=100
    
    CRYPTO_AES_KEY=your-base64-aes-key
    CRYPTO_HMAC_KEY=your-base64-hmac-key
    
    TRANSFERS_DEFAULT_PAGE_SIZE=10
    TRANSFERS_MAX_PAGE_SIZE=100
    TRANSFERS_MAX_TTL_SECONDS=300
    
    APP_CLOCK_ZONE=UTC
    JPA_SHOW_SQL=false
    ```
## 📚 Документация API

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- OpenAPI YAML: [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml)

Подробное описание API см. в [docs/README_DOCS.md](./docs/README_DOCS.md).

---

## Автор
- @primebeaf
- oleshasha9@gmail.com

