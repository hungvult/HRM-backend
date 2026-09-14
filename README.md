# HRM Backend

Backend service for the HRM system, built with Spring Boot, Maven, Java 21, Spring Security, Spring Data JPA, and PostgreSQL.

## Requirements

- Java 21
- PostgreSQL 14 or newer
- Git
- Maven is optional because the project includes Maven Wrapper scripts

## Setup

Clone the repository and move into the backend folder:

```bash
git clone https://github.com/hungvult/HRM-backend.git
cd HRM-backend
```

Create a local environment file:

```bash
cp .env.example .env
```

Update `.env` for your local PostgreSQL database:

```env
DB_URL=jdbc:postgresql://localhost:5432/hrm
DB_USERNAME=postgres
DB_PASSWORD=1234
```

Make sure the database exists before starting the app:

```sql
CREATE DATABASE hrm;
```

## Run Locally

On Windows:

```bash
mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

The API runs on:

```text
http://localhost:8080
```

## Build

On Windows:

```bash
mvnw.cmd clean package
```

On macOS or Linux:

```bash
./mvnw clean package
```

The generated JAR is created in the `target` folder.

## Test

On Windows:

```bash
mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```

## Environment Variables

| Name | Description | Default |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/hrm` |
| `DB_USERNAME` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | Required |

## Troubleshooting

- If the app cannot connect to PostgreSQL, confirm that PostgreSQL is running and the `hrm` database exists.
- If Java compilation fails, confirm that `java -version` reports Java 21.
- If dependencies fail to download, check your network connection and retry the Maven command.
