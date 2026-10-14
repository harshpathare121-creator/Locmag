#!/usr/bin/env sh
set -eu
export ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
export CAMPUSLOCK_DB_PATH="${CAMPUSLOCK_DB_PATH:-./campuslock.db}"
exec mvn spring-boot:run
