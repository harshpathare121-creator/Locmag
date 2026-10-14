package com.campuslock;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CampusLockApplication {
    public static void main(String[] args) {
        // SQLite can create the database file, but not missing parent directories.
        // Render's persistent disk is normally mounted at /var/data. Creating the
        // parent folder here prevents a JDBC startup failure when the folder is absent.
        String configuredPath = System.getenv().getOrDefault("CAMPUSLOCK_DB_PATH", "./campuslock.db");
        try {
            Path databasePath = Path.of(configuredPath).toAbsolutePath().normalize();
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Cannot prepare SQLite database path. Check CAMPUSLOCK_DB_PATH and directory permissions.", ex);
        }
        SpringApplication.run(CampusLockApplication.class, args);
    }
}
