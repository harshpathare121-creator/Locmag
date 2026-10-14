# CampusLock — flat-file Java Spring Boot + SQLite project

All project files are placed directly in this one folder; there are no nested project folders. The Maven build is configured for this flat layout.

## Files
- `index.html` — existing HTML/CSS/JavaScript frontend
- `CampusLockApplication.java` — Spring Boot entry point
- `CampusLockController.java` — API and student/admin actions
- `DatabaseInitializer.java` — SQLite schema and sample locker initialization
- `ApiExceptionHandler.java` — API error responses
- `application.properties` — server/database configuration
- `pom.xml` — Maven dependencies/build
- `Dockerfile`, `render.yaml` — Render deployment configuration
- `run-local.bat`, `run-local.sh` — local start helpers
- `.env.example`, `.gitignore` — environment example and Git exclusions

## Run locally
Requirements: JDK 17+ and Maven 3.9+.

1. Extract this ZIP.
2. Open a terminal in the extracted folder.
3. Run `mvn spring-boot:run` (Windows can also run `run-local.bat`).
4. Visit http://localhost:8080.
5. Default local admin credentials: `admin` / `admin123`. Change them before public deployment.

The SQLite database is created as `campuslock.db` in the working directory by default. The first run seeds sample lockers.

## Features
- Student registration/login with hashed passwords
- Distance priority: >=30 km VERY HIGH; >=15 km HIGH; >=5 km MEDIUM; otherwise LOW
- Student locker requests with 1–365 day duration
- Admin approve/reject, booking expiry, cancellation and in-app notifications
- Database-backed shared data across devices using the same deployed server
- Admin locker management and student priority ranking

## GitHub + Render
Upload the contents of this folder to the root of a GitHub repository. In Render, deploy it as a Docker web service or Blueprint using `render.yaml`. Set unique `ADMIN_USERNAME` and `ADMIN_PASSWORD` environment variables. Ensure a persistent disk is mounted at `/var/data` because the configured deployed SQLite path is `/var/data/campuslock.db`. Render persistent disks may require a paid plan; without persistent storage SQLite data can be lost on restart/redeploy.

## Important
The source package has been rearranged into a flat layout. Build it locally with `mvn spring-boot:run` to verify the environment and dependency downloads before deploying. Do not commit the database file or real secrets.
