@echo off
if "%ADMIN_USERNAME%"=="" set ADMIN_USERNAME=admin
if "%ADMIN_PASSWORD%"=="" set ADMIN_PASSWORD=admin123
if "%CAMPUSLOCK_DB_PATH%"=="" set CAMPUSLOCK_DB_PATH=./campuslock.db
call mvn spring-boot:run
