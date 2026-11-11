# NEXORA

NEXORA is a Java-based student platform that brings academic development, career guidance, placement preparation and student progress tracking into one application.

## Technologies

- Java 21
- Spring Boot
- Thymeleaf
- Spring Security
- MySQL
- Maven

## Modules

- Student registration, login and profile
- Student dashboard
- Career guidance and career selection
- Development GPS and milestone tracking
- Academic development and study resources
- Recommended courses
- Opportunities hub
- Achievements and connections
- SGPA / CGPA calculator
- Settings and subscription plans

## Requirements

- JDK 21
- MySQL Server
- Git

## Setup and Run

1. Clone or download this repository.
2. Create the MySQL database:

   ```sql
   CREATE DATABASE nexora;
   ```

3. Configure the database connection for your local MySQL installation. The application uses the database `nexora`; set the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` environment variables if your local settings differ.
4. From the project root, run the application on Windows:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   On Linux or macOS:

   ```bash
   chmod +x ./mvnw
   ./mvnw spring-boot:run
   ```

5. Open [http://localhost:8080/login](http://localhost:8080/login).

The application schema is available at `src/main/resources/schema.sql`. A copy for manual database setup is provided in `database/schema.sql`.

## Build and Test

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package
```

Linux / macOS:

```bash
./mvnw test
./mvnw clean package
```

## Screenshots

Application screenshots are available in the [`screenshots`](screenshots/) directory.

| Module | Screenshot |
|---|---|
| Login | [View](screenshots/01-login.png) |
| Registration | [View](screenshots/02-registration.png) |
| Dashboard | [View](screenshots/03-dashboard.png) |
| Career Guidance | [View](screenshots/04-career-guidance.png) |
| Academic Development | [View](screenshots/05-academic-development.png) |
| Career Selection | [View](screenshots/06-career-selection.png) |
| Development GPS | [View](screenshots/07-development-gps.png) |
| Opportunities Hub | [View](screenshots/08-opportunities-hub.png) |
| Recommended Courses | [View](screenshots/09-recommended-courses.png) |
| Achievements | [View](screenshots/10-achievements.png) |
| Connections | [View](screenshots/11-connections.png) |
| SGPA / CGPA Calculator | [View](screenshots/12-sgpa-cgpa-calculator.png) |
| Settings | [View](screenshots/13-settings-subscription.png) |
| Logout | [View](screenshots/14-logout-confirmation.png) |

## Configuration

Email OTP and external integrations may require service credentials and provider permissions. Configure credentials locally through environment variables; do not commit passwords, API keys or OAuth secrets to the repository.
