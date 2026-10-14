import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
public class CampusLockController {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public CampusLockController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private String now() { return LocalDateTime.now().format(STAMP); }
    private String today() { return LocalDate.now().toString(); }
    private String priority(double d) { return d >= 30 ? "VERY HIGH" : d >= 15 ? "HIGH" : d >= 5 ? "MEDIUM" : "LOW"; }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(BAD_REQUEST, message); }
    private ResponseStatusException unauthorized(String message) { return new ResponseStatusException(UNAUTHORIZED, message); }
    private ResponseStatusException forbidden(String message) { return new ResponseStatusException(FORBIDDEN, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(NOT_FOUND, message); }
    private String text(Map<String, Object> body, String key) { Object v = body == null ? null : body.get(key); return v == null ? "" : String.valueOf(v).trim(); }
    private Map<String,Object> map(Object... pairs) { Map<String,Object> m = new LinkedHashMap<>(); for(int i=0;i<pairs.length;i+=2)m.put(String.valueOf(pairs[i]),pairs[i+1]); return m; }
    private String role(HttpSession session) { return (String) session.getAttribute("role"); }
    private String studentId(HttpSession session) { return (String) session.getAttribute("studentId"); }
    private void requireStudent(HttpSession s) { if (!"STUDENT".equals(role(s)) || studentId(s) == null) throw unauthorized("Please log in as a student."); }
    private void requireAdmin(HttpSession s) { if (!"ADMIN".equals(role(s))) throw forbidden("Admin login is required for this action."); }
    private void addNotification(String target, String recipientId, String title, String message, String type) {
        jdbc.update("INSERT INTO notifications(recipient_type,recipient_id,title,message,type,created_at,is_read) VALUES(?,?,?,?,?,?,0)", target, recipientId, title, message, type, now());
    }
    private Map<String,Object> studentRow(String id) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT student_id,name,email,address,distance,priority,booking_locker_id,booking_start,booking_days,booking_expiry FROM students WHERE student_id=?", id);
        if(rows.isEmpty()) return null;
        Map<String,Object> r=rows.get(0);
        return map("id",r.get("student_id"),"name",r.get("name"),"email",r.get("email"),"address",r.get("address"),"distance",r.get("distance"),"priority",r.get("priority"),"booking",r.get("booking_locker_id"),"bookingStart",r.get("booking_start"),"bookingDays",r.get("booking_days"),"bookingExpiry",r.get("booking_expiry"));
    }
    private List<Map<String,Object>> lockers() {
        return jdbc.query("SELECT l.locker_id,l.block_name,l.size,l.booked_by,CASE WHEN l.booked_by IS NULL OR l.booked_by='' THEN 1 ELSE 0 END AS available,CASE WHEN EXISTS(SELECT 1 FROM locker_requests r WHERE r.locker_id=l.locker_id AND r.status='PENDING') THEN 1 ELSE 0 END AS pending FROM lockers l ORDER BY l.locker_id", (rs,n)->map("id",rs.getString("locker_id"),"block",rs.getString("block_name"),"size",rs.getString("size"),"bookedBy",rs.getString("booked_by"),"available",rs.getInt("available")==1,"pending",rs.getInt("pending")==1));
    }
    private List<Map<String,Object>> requests(String where, Object... args) {
        String sql="SELECT r.id,r.student_id,s.name AS student_name,r.locker_id,l.block_name,l.size,r.days,r.status,r.created_at,r.decided_at,r.decision_message,r.days AS duration FROM locker_requests r JOIN students s ON s.student_id=r.student_id JOIN lockers l ON l.locker_id=r.locker_id "+where+" ORDER BY r.id DESC";
        return jdbc.query(sql,(rs,n)->map("id",rs.getLong("id"),"studentId",rs.getString("student_id"),"studentName",rs.getString("student_name"),"lockerId",rs.getString("locker_id"),"block",rs.getString("block_name"),"size",rs.getString("size"),"days",rs.getInt("days"),"status",rs.getString("status"),"createdAt",rs.getString("created_at"),"decidedAt",rs.getString("decided_at"),"decisionMessage",rs.getString("decision_message")),args);
    }
    private List<Map<String,Object>> notifications(String target,String recipient) {
        return jdbc.query("SELECT id,title,message,type,created_at,is_read FROM notifications WHERE recipient_type=? AND recipient_id=? ORDER BY id DESC LIMIT 100",(rs,n)->map("id",rs.getLong("id"),"title",rs.getString("title"),"message",rs.getString("message"),"type",rs.getString("type"),"date",rs.getString("created_at"),"read",rs.getInt("is_read")==1),target,recipient);
    }

    private void releaseExpired() {
        List<Map<String,Object>> expired=jdbc.queryForList("SELECT student_id,booking_locker_id FROM students WHERE booking_locker_id IS NOT NULL AND booking_locker_id<>'' AND booking_expiry <= ?",today());
        for(Map<String,Object> row:expired){
            String sid=String.valueOf(row.get("student_id")), lid=String.valueOf(row.get("booking_locker_id"));
            jdbc.update("UPDATE lockers SET booked_by=NULL WHERE locker_id=? AND booked_by=?",lid,sid);
            jdbc.update("UPDATE students SET booking_locker_id=NULL,booking_start=NULL,booking_days=0,booking_expiry=NULL WHERE student_id=?",sid);
            addNotification("STUDENT",sid,"Locker booking expired","Your booking for "+lid+" has expired and the locker is now available.","info");
        }
    }

    @GetMapping("/health") public Map<String,Object> health(){return map("status","ok","application","CampusLock");}

    @PostMapping("/auth/register") public Map<String,Object> register(@RequestBody Map<String,Object> body) {
        String name=text(body,"name"), id=text(body,"studentId"), email=text(body,"email").toLowerCase(Locale.ROOT), password=text(body,"password"), address=text(body,"address");
        double distance;
        try { distance=Double.parseDouble(text(body,"distance")); } catch(Exception ex) { throw bad("Enter a valid distance in kilometres."); }
        if(name.isBlank()||id.isBlank()||email.isBlank()||password.length()<8||address.isBlank()) throw bad("Fill in every field. Password must contain at least 8 characters.");
        if(distance<0||distance>10000||!Double.isFinite(distance)) throw bad("Distance must be between 0 and 10,000 km.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE student_id=?",Integer.class,id)>0) throw bad("That Student ID is already registered.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE email=?",Integer.class,email)>0) throw bad("That email address is already registered.");
        jdbc.update("INSERT INTO students(student_id,name,email,password_hash,address,distance,priority) VALUES(?,?,?,?,?,?,?)",id,name,email,passwordEncoder.encode(password),address,distance,priority(distance));
        addNotification("ADMIN","admin","New student registered",name+" ("+id+") registered on CampusLock.","info");
        return map("message","Account created successfully. Please log in.");
    }

    @PostMapping("/auth/login") public Map<String,Object> login(@RequestBody Map<String,Object> body,HttpSession session) {
        String id=text(body,"studentId"), password=text(body,"password");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT student_id,password_hash FROM students WHERE student_id=?",id);
        if(rows.isEmpty()||!passwordEncoder.matches(password,String.valueOf(rows.get(0).get("password_hash")))) throw unauthorized("Invalid Student ID or password.");
        session.setAttribute("role","STUDENT"); session.setAttribute("studentId",String.valueOf(rows.get(0).get("student_id")));
        return map("role","STUDENT","student",studentRow(studentId(session)));
    }

    @PostMapping("/auth/admin-login") public Map<String,Object> adminLogin(@RequestBody Map<String,Object> body,HttpSession session) {
        String expectedUser=System.getenv().getOrDefault("ADMIN_USERNAME","admin");
        String expectedPassword=System.getenv().getOrDefault("ADMIN_PASSWORD","admin123");
        if(!expectedUser.equals(text(body,"username"))||!expectedPassword.equals(text(body,"password"))) throw unauthorized("Incorrect admin username or password.");
        session.setAttribute("role","ADMIN");session.removeAttribute("studentId");
        return map("role","ADMIN");
    }

    @GetMapping("/auth/me") public Map<String,Object> me(HttpSession session) {
        if("ADMIN".equals(role(session))) return map("role","ADMIN");
        if("STUDENT".equals(role(session))) { Map<String,Object> s=studentRow(studentId(session)); if(s!=null)return map("role","STUDENT","student",s); }
        return map("role","GUEST");
    }
    @PostMapping("/auth/logout") public Map<String,Object> logout(HttpSession session){session.invalidate();return map("message","Logged out.");}

    @GetMapping("/student/dashboard") public Map<String,Object> studentDashboard(HttpSession session) {
        requireStudent(session);releaseExpired();String id=studentId(session);
        return map("student",studentRow(id),"lockers",lockers(),"requests",requests("WHERE r.student_id=?",id),"notifications",notifications("STUDENT",id));
    }

    @PostMapping("/requests") public Map<String,Object> createRequest(@RequestBody Map<String,Object> body,HttpSession session) {
        requireStudent(session);releaseExpired();String sid=studentId(session), lockerId=text(body,"lockerId");int days;
        try{days=Integer.parseInt(text(body,"days"));}catch(Exception e){throw bad("Duration must be a whole number between 1 and 365 days.");}
        if(days<1||days>365)throw bad("Duration must be a whole number between 1 and 365 days.");
        Map<String,Object> student=studentRow(sid);if(student==null)throw unauthorized("Student account not found. Please log in again.");
        if(student.get("booking")!=null && !String.valueOf(student.get("booking")).isBlank())throw bad("You already have an active locker booking.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM locker_requests WHERE student_id=? AND status='PENDING'",Integer.class,sid)>0)throw bad("You already have a pending locker request.");
        List<Map<String,Object>> found=jdbc.queryForList("SELECT locker_id,block_name,size,booked_by FROM lockers WHERE locker_id=?",lockerId);
        if(found.isEmpty())throw notFound("Locker not found.");
        if(found.get(0).get("booked_by")!=null&&!String.valueOf(found.get(0).get("booked_by")).isBlank())throw bad("This locker is already booked.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM locker_requests WHERE locker_id=? AND status='PENDING'",Integer.class,lockerId)>0)throw bad("This locker already has a pending request.");
        jdbc.update("INSERT INTO locker_requests(student_id,locker_id,days,status,created_at) VALUES(?,?,?,'PENDING',?)",sid,lockerId,days,now());
        addNotification("ADMIN","admin","New locker booking request",student.get("name")+" ("+sid+") requested "+lockerId+" for "+days+" day(s). Review it in the Admin Dashboard.","info");
        addNotification("STUDENT",sid,"Locker request submitted","Your request for "+lockerId+" for "+days+" day(s) is pending admin approval.","info");
        return map("message","Request sent to admin for approval.");
    }

    @PostMapping("/requests/{requestId}/withdraw") public Map<String,Object> withdraw(@PathVariable long requestId,HttpSession session) {
        requireStudent(session);String sid=studentId(session);List<Map<String,Object>> r=jdbc.queryForList("SELECT locker_id,status FROM locker_requests WHERE id=? AND student_id=?",requestId,sid);
        if(r.isEmpty())throw notFound("Request not found.");if(!"PENDING".equals(r.get(0).get("status")))throw bad("Only pending requests can be withdrawn.");
        jdbc.update("UPDATE locker_requests SET status='WITHDRAWN',decided_at=? WHERE id=?",now(),requestId);
        addNotification("ADMIN","admin","Locker request withdrawn",sid+" withdrew the request for "+r.get(0).get("locker_id")+".","info");
        addNotification("STUDENT",sid,"Request withdrawn","Your request for "+r.get(0).get("locker_id")+" has been withdrawn.","info");return map("message","Request withdrawn.");
    }

    @PostMapping("/bookings/cancel") public Map<String,Object> cancelBooking(HttpSession session) {
        requireStudent(session);String sid=studentId(session);Map<String,Object> s=studentRow(sid);Object b=s.get("booking");if(b==null||String.valueOf(b).isBlank())throw bad("You do not have an active locker booking.");String lid=String.valueOf(b);
        jdbc.update("UPDATE lockers SET booked_by=NULL WHERE locker_id=? AND booked_by=?",lid,sid);
        jdbc.update("UPDATE students SET booking_locker_id=NULL,booking_start=NULL,booking_days=0,booking_expiry=NULL WHERE student_id=?",sid);
        addNotification("STUDENT",sid,"Locker booking cancelled","Your booking for "+lid+" has been cancelled.","info");addNotification("ADMIN","admin","Locker booking cancelled",sid+" cancelled booking "+lid+".","info");return map("message","Locker booking cancelled.");
    }

    @GetMapping("/admin/dashboard") public Map<String,Object> adminDashboard(HttpSession session) {
        requireAdmin(session);releaseExpired();List<Map<String,Object>> ls=lockers();List<Map<String,Object>> ss=jdbc.query("SELECT student_id,name,email,address,distance,priority,booking_locker_id,booking_days,booking_expiry FROM students ORDER BY distance DESC, name ASC",(rs,n)->map("id",rs.getString("student_id"),"name",rs.getString("name"),"email",rs.getString("email"),"address",rs.getString("address"),"distance",rs.getDouble("distance"),"priority",rs.getString("priority"),"booking",rs.getString("booking_locker_id"),"bookingDays",rs.getInt("booking_days"),"bookingExpiry",rs.getString("booking_expiry")));
        long pending=jdbc.queryForObject("SELECT COUNT(*) FROM locker_requests WHERE status='PENDING'",Long.class);long available=ls.stream().filter(x->Boolean.TRUE.equals(x.get("available"))).count();
        return map("lockers",ls,"students",ss,"requests",requests("WHERE r.status='PENDING'"),"history",requests("WHERE r.status<>'PENDING'"),"notifications",notifications("ADMIN","admin"),"stats",map("pending",pending,"total",ls.size(),"available",available,"booked",ls.size()-available,"students",ss.size()));
    }

    @PostMapping("/admin/requests/{requestId}/approve") public Map<String,Object> approve(@PathVariable long requestId,HttpSession session) {
        requireAdmin(session);releaseExpired();List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.id,r.student_id,r.locker_id,r.days,r.status,s.name FROM locker_requests r JOIN students s ON s.student_id=r.student_id WHERE r.id=?",requestId);
        if(rows.isEmpty())throw notFound("Request not found.");Map<String,Object> r=rows.get(0);if(!"PENDING".equals(r.get("status")))throw bad("This request is no longer pending.");String sid=String.valueOf(r.get("student_id")),lid=String.valueOf(r.get("locker_id"));int days=((Number)r.get("days")).intValue();
        Map<String,Object> s=studentRow(sid);List<Map<String,Object>> l=jdbc.queryForList("SELECT booked_by FROM lockers WHERE locker_id=?",lid);
        if(s==null||l.isEmpty()||(l.get(0).get("booked_by")!=null&&!String.valueOf(l.get(0).get("booked_by")).isBlank())||(s.get("booking")!=null&&!String.valueOf(s.get("booking")).isBlank())) {
            jdbc.update("UPDATE locker_requests SET status='REJECTED',decided_at=?,decision_message=? WHERE id=?",now(),"The locker or student is no longer available.",requestId);
            addNotification("STUDENT",sid,"Locker request not approved","Your request for "+lid+" could not be approved because the locker or your account is no longer available.","rejected");throw bad("The locker or student is no longer available. The request was rejected.");
        }
        String start=today(),expiry=LocalDate.now().plusDays(days).toString();
        int updated=jdbc.update("UPDATE lockers SET booked_by=? WHERE locker_id=? AND (booked_by IS NULL OR booked_by='')",sid,lid);
        if(updated!=1)throw bad("Another admin action has already allocated this locker. Refresh and retry.");
        jdbc.update("UPDATE students SET booking_locker_id=?,booking_start=?,booking_days=?,booking_expiry=? WHERE student_id=?",lid,start,days,expiry,sid);
        jdbc.update("UPDATE locker_requests SET status='APPROVED',decided_at=?,decision_message=NULL WHERE id=?",now(),requestId);
        addNotification("STUDENT",sid,"Locker request approved","Your request for "+lid+" has been approved. Duration: "+days+" day(s). Expiry date: "+expiry+".","success");
        addNotification("ADMIN","admin","Locker request approved","You approved "+r.get("name")+"'s request for "+lid+".","success");return map("message","Request approved and locker allocated.");
    }

    @PostMapping("/admin/requests/{requestId}/reject") public Map<String,Object> reject(@PathVariable long requestId,@RequestBody(required=false) Map<String,Object> body,HttpSession session) {
        requireAdmin(session);List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.student_id,r.locker_id,r.status,s.name FROM locker_requests r JOIN students s ON s.student_id=r.student_id WHERE r.id=?",requestId);
        if(rows.isEmpty())throw notFound("Request not found.");Map<String,Object> r=rows.get(0);if(!"PENDING".equals(r.get("status")))throw bad("This request is no longer pending.");String reason=text(body==null?Map.of():body,"reason");String sid=String.valueOf(r.get("student_id")),lid=String.valueOf(r.get("locker_id"));
        jdbc.update("UPDATE locker_requests SET status='REJECTED',decided_at=?,decision_message=? WHERE id=?",now(),reason,requestId);
        addNotification("STUDENT",sid,"Locker request not approved","Your request for "+lid+" was not approved."+(reason.isBlank()?"":" Reason: "+reason),"rejected");addNotification("ADMIN","admin","Locker request rejected","You rejected "+r.get("name")+"'s request for "+lid+".","info");return map("message","Request rejected. Student notified.");
    }

    @PostMapping("/admin/lockers") public Map<String,Object> addLocker(@RequestBody Map<String,Object> body,HttpSession session) {
        requireAdmin(session);String id=text(body,"id"),block=text(body,"block"),size=text(body,"size");if(id.isBlank())throw bad("Locker ID is required.");if(!List.of("Block A","Block B","Block C","Block D").contains(block))throw bad("Choose a valid block.");if(!List.of("Small","Medium","Large").contains(size))throw bad("Choose a valid size.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM lockers WHERE locker_id=?",Integer.class,id)>0)throw bad("A locker with that ID already exists.");jdbc.update("INSERT INTO lockers(locker_id,block_name,size,booked_by) VALUES(?,?,?,NULL)",id,block,size);return map("message","Locker added.");
    }
    @DeleteMapping("/admin/lockers/{id}") public Map<String,Object> deleteLocker(@PathVariable String id,HttpSession session) {
        requireAdmin(session);List<Map<String,Object>> rows=jdbc.queryForList("SELECT booked_by FROM lockers WHERE locker_id=?",id);if(rows.isEmpty())throw notFound("Locker not found.");if(rows.get(0).get("booked_by")!=null&&!String.valueOf(rows.get(0).get("booked_by")).isBlank())throw bad("Booked lockers cannot be deleted.");
        Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM locker_requests WHERE locker_id=? AND status='PENDING'",Integer.class,id);if(pending!=null&&pending>0)throw bad("This locker has a pending request and cannot be deleted.");Integer history=jdbc.queryForObject("SELECT COUNT(*) FROM locker_requests WHERE locker_id=?",Integer.class,id);if(history!=null&&history>0)throw bad("This locker has request history and cannot be deleted.");jdbc.update("DELETE FROM lockers WHERE locker_id=?",id);return map("message","Locker deleted.");
    }

    @PostMapping("/notifications/read") public Map<String,Object> markRead(HttpSession session) {
        if("ADMIN".equals(role(session)))jdbc.update("UPDATE notifications SET is_read=1 WHERE recipient_type='ADMIN' AND recipient_id='admin'");
        else if("STUDENT".equals(role(session)))jdbc.update("UPDATE notifications SET is_read=1 WHERE recipient_type='STUDENT' AND recipient_id=?",studentId(session));
        else throw unauthorized("Please log in first.");return map("message","Notifications marked as read.");
    }
}
