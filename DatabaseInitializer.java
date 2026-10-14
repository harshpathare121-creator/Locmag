import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public DatabaseInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void run(ApplicationArguments args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS students (student_id TEXT PRIMARY KEY COLLATE NOCASE, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE COLLATE NOCASE, password_hash TEXT NOT NULL, address TEXT NOT NULL, distance REAL NOT NULL, priority TEXT NOT NULL, booking_locker_id TEXT, booking_start TEXT, booking_days INTEGER NOT NULL DEFAULT 0, booking_expiry TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS lockers (locker_id TEXT PRIMARY KEY COLLATE NOCASE, block_name TEXT NOT NULL, size TEXT NOT NULL, booked_by TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS locker_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id TEXT NOT NULL, locker_id TEXT NOT NULL, days INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', created_at TEXT NOT NULL, decided_at TEXT, decision_message TEXT, FOREIGN KEY(student_id) REFERENCES students(student_id), FOREIGN KEY(locker_id) REFERENCES lockers(locker_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS notifications (id INTEGER PRIMARY KEY AUTOINCREMENT, recipient_type TEXT NOT NULL, recipient_id TEXT NOT NULL, title TEXT NOT NULL, message TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'info', created_at TEXT NOT NULL, is_read INTEGER NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_requests_student_status ON locker_requests(student_id, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_requests_locker_status ON locker_requests(locker_id, status)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_type, recipient_id, id)");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM lockers", Integer.class);
        if (count == null || count == 0) {
            List<Object[]> lockers = List.of(
                new Object[]{"L-101", "Block A", "Small"}, new Object[]{"L-102", "Block A", "Medium"},
                new Object[]{"L-201", "Block B", "Large"}, new Object[]{"L-202", "Block B", "Medium"},
                new Object[]{"L-301", "Block C", "Small"}, new Object[]{"L-302", "Block C", "Large"},
                new Object[]{"L-401", "Block D", "Medium"}, new Object[]{"L-402", "Block D", "Small"}
            );
            for (Object[] l : lockers) jdbc.update("INSERT OR IGNORE INTO lockers(locker_id, block_name, size, booked_by) VALUES(?,?,?,NULL)", l);
        }
    }
}
