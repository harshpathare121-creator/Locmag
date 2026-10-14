# CampusLock — Java Spring Boot + SQLite

CampusLock is a Full Stack Java college mini-project. The frontend uses HTML, CSS and JavaScript; the backend uses Java 17 with Spring Boot REST APIs; SQLite stores student accounts, lockers, requests and notifications. The website is served by the Java application.

## Features

- Student registration and login; passwords are stored as BCrypt hashes.
- Distance-based priority: `>= 30 km VERY HIGH`, `>= 15 km HIGH`, `>= 5 km MEDIUM`, otherwise `LOW`.
- Shared locker inventory stored in SQLite.
- Students request a locker for 1–365 days.
- Admin approves or rejects requests. Approval allocates the locker and starts the booking duration on the approval date.
- In-app notifications for students and admin, stored in the database and visible across devices using the same deployed service.
- Booking cancellation and automatic expiry cleanup when dashboards are requested.
- Admin can add lockers, see student priority rankings, view request history, and delete lockers that have no bookings or request history.
- Server-side session login. The backend checks student/admin permissions for protected APIs.

## Project layout

```text
CampusLock-Java/
├── src/main/java/com/campuslock/
│   ├── CampusLockApplication.java
│   ├── CampusLockController.java
│   ├── DatabaseInitializer.java
│   └── ApiExceptionHandler.java
├── src/main/resources/
│   ├── application.properties
│   └── static/index.html
├── pom.xml
├── Dockerfile
├── render.yaml
└── .gitignore
```

## Run locally

Requirements: JDK 17 or newer and Maven 3.9+.

1. Extract the ZIP and open a terminal in the `CampusLock-Java` folder.
2. Start the app:

   ```bash
   mvn spring-boot:run
   ```

3. Open <http://localhost:8080>.
4. The SQLite database file `campuslock.db` is created in the project folder automatically. On the first run, eight sample lockers are added.
5. Open **Admin** and use the local defaults `admin` / `admin123`.

You can override the local admin credentials before starting the app:

**Windows PowerShell**

```powershell
$env:ADMIN_USERNAME="campus-admin"
$env:ADMIN_PASSWORD="use-a-long-unique-password"
mvn spring-boot:run
```

**macOS/Linux**

```bash
export ADMIN_USERNAME="campus-admin"
export ADMIN_PASSWORD="use-a-long-unique-password"
mvn spring-boot:run
```

The `PORT` environment variable is supported by the app. `CAMPUSLOCK_DB_PATH` changes the database file path. The application now creates the database parent directory before Spring Boot initializes JDBC, which helps avoid SQLite startup failures when the configured folder is missing. The process still needs write permission to that directory.

## Test the workflow

1. Register a student with a unique Student ID and email. Passwords must be at least 8 characters.
2. Log in as that student and request an available locker for a duration between 1 and 365 days.
3. Log out, select Admin, and log in.
4. Approve or reject the pending request. On approval, the locker becomes booked and the student's dashboard receives an in-app notification.
5. Log back in as the student to see the result and expiry date.
6. Register a second student or open the site in another browser/device to verify that the backend shares the same database and inventory.

## Deploy to GitHub + Render

### 1. Push to GitHub

Use your existing GitHub repository `locmag`. Upload the **contents of this project folder** to the repository root, preserving the `src/main/java/...` and `src/main/resources/...` folder structure. Do not upload your local `campuslock.db` file.

### 2. Create the Render service

1. In Render, create a **New Blueprint Instance** from your GitHub repository, or create a Docker web service and select this repository.
2. The included `render.yaml` uses the Dockerfile, checks `/api/health`, and requests a persistent disk at `/var/data`. If you created the Render service manually instead of using a Blueprint, configure a persistent disk mounted at `/var/data` in the service settings; adding `render.yaml` to GitHub alone does not automatically attach a disk to an existing manual service.
3. Set `ADMIN_USERNAME` and `ADMIN_PASSWORD` to unique values in Render's environment settings. Do not keep `admin123` for a public deployment.
4. Deploy and open the Render URL. The frontend is served by Spring Boot at the root URL, so you do not need a separate static-site service.

**SQLite + Render note:** SQLite is a file database. The app is configured to write to `/var/data/campuslock.db`, so the Render service must have a persistent disk mounted at `/var/data`. Persistent disks require a paid Render plan. Without a persistent disk, database data can be lost when the service restarts or redeploys. The directory-creation fix can help the app start, but it does not make an ephemeral database persistent. Back up the database file periodically. For a fully free always-persistent hosted database, PostgreSQL is usually a better fit, but this project is configured for SQLite as requested.

## API overview

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| GET | `/api/health` | Public | Health check |
| POST | `/api/auth/register` | Public | Register student |
| POST | `/api/auth/login` | Public | Student login |
| POST | `/api/auth/admin-login` | Public | Admin login |
| GET | `/api/auth/me` | Session | Check current session |
| POST | `/api/auth/logout` | Session | Log out |
| GET | `/api/student/dashboard` | Student | Student data, lockers, requests, notifications |
| POST | `/api/requests` | Student | Submit a locker request |
| POST | `/api/requests/{id}/withdraw` | Student owner | Withdraw a pending request |
| POST | `/api/bookings/cancel` | Student | Cancel active booking |
| GET | `/api/admin/dashboard` | Admin | Admin dashboard data |
| POST | `/api/admin/requests/{id}/approve` | Admin | Approve request |
| POST | `/api/admin/requests/{id}/reject` | Admin | Reject request |
| POST | `/api/admin/lockers` | Admin | Add locker |
| DELETE | `/api/admin/lockers/{id}` | Admin | Delete unused locker |
| POST | `/api/notifications/read` | Logged-in user | Mark notifications read |

## Notes / limitations

- Notifications are **in-app notifications**, not email, SMS, or push notifications.
- Session-based authentication is used; keep the frontend and backend on the same origin as configured here.
- For a public production system, add HTTPS (Render provides it), rate limiting, password reset, stronger audit logging, backups, and a more complete security review.
- SQLite supports this college demo well, but a single SQLite database is not ideal for high concurrent write traffic.
