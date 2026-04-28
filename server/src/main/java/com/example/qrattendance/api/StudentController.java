package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.auth.PasswordHasher;
import com.example.qrattendance.qr.QrTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/student")
public class StudentController {
  private final JdbcTemplate jdbc;
  private final QrTokenService qrTokenService;

  public StudentController(JdbcTemplate jdbc, QrTokenService qrTokenService) {
    this.jdbc = jdbc;
    this.qrTokenService = qrTokenService;
  }

  @PostMapping("/check-ins")
  public Map<String, Object> checkIn(@RequestBody Map<String, Object> body) {
    long studentId = studentId();
    long sessionId = ((Number) body.get("sessionId")).longValue();
    String token = (String) body.get("token");
    Map<String, Object> session =
        one("SELECT se.id, se.course_id, se.teacher_id, se.ends_at, se.status FROM attendance_sessions se WHERE se.id = ?", sessionId);
    if ("CLOSED".equals(session.get("status")) || Instant.parse((String) session.get("ends_at")).isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.GONE, "考勤已结束");
    }
    if (!qrTokenService.acceptsCurrent(sessionId, token)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二维码已失效");
    }
    Integer eligible =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM course_assignments ca
            JOIN course_enrollments ce ON ce.assignment_id = ca.id
            WHERE ca.course_id = ? AND ca.teacher_id = ? AND ce.student_id = ?
            """,
            Integer.class,
            ((Number) session.get("course_id")).longValue(),
            ((Number) session.get("teacher_id")).longValue(),
            studentId);
    if (eligible == null || eligible == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "学生不属于该课程名单");

    List<Map<String, Object>> existing = jdbc.queryForList("SELECT id, status, checked_in_at FROM attendance_records WHERE session_id = ? AND student_id = ?", sessionId, studentId);
    if (!existing.isEmpty()) {
      return Map.of("record", existing.getFirst(), "duplicate", true);
    }
    String now = Instant.now().toString();
    jdbc.update(
        "INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)",
        sessionId, studentId, "PRESENT", now, "QR");
    long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    return Map.of("record", Map.of("id", id, "session_id", sessionId, "status", "PRESENT", "checked_in_at", now), "duplicate", false);
  }

  @GetMapping("/attendance-records")
  public List<Map<String, Object>> records() {
    long studentId = studentId();
    return jdbc.queryForList(
        "SELECT ar.id, ar.session_id, co.name course_name, ar.status, ar.checked_in_at, ar.source FROM attendance_records ar JOIN attendance_sessions se ON se.id = ar.session_id JOIN courses co ON co.id = se.course_id WHERE ar.student_id = ? ORDER BY ar.id DESC",
        studentId);
  }

  @GetMapping("/courses")
  public List<Map<String, Object>> courses() {
    long studentId = studentId();
    return jdbc.queryForList(
        """
        SELECT co.id,
               co.name,
               co.code,
               u.display_name teacherName,
               ca.term
        FROM course_enrollments ce
        JOIN course_assignments ca ON ca.id = ce.assignment_id
        JOIN courses co ON co.id = ca.course_id
        JOIN teachers t ON t.id = ca.teacher_id
        JOIN users u ON u.id = t.user_id
        WHERE ce.student_id = ?
        ORDER BY co.id
        """,
        studentId);
  }

  @GetMapping("/sessions")
  public List<Map<String, Object>> sessions(@RequestParam(value = "scope", defaultValue = "active") String scope) {
    long studentId = studentId();
    closeExpiredSessions();
    boolean recent = "recent".equalsIgnoreCase(scope);
    String filter = recent ? "" : " AND se.status = 'OPEN'";
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT se.id,
                   co.id courseId,
                   co.name courseName,
                   se.started_at startedAt,
                   se.ends_at endsAt,
                   se.status,
                   se.method,
                   ar.id recordId,
                   ar.status recordStatus,
                   lr.id leaveId
            FROM attendance_sessions se
            JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
            JOIN course_enrollments ce ON ce.assignment_id = ca.id
            JOIN courses co ON co.id = se.course_id
            LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = ce.student_id
            LEFT JOIN leave_requests lr ON lr.session_id = se.id AND lr.student_id = ce.student_id
            WHERE ce.student_id = ?
            """
                + filter
                + " ORDER BY se.started_at DESC, se.id DESC",
            studentId);
    return rows.stream()
        .map(
            row -> {
              boolean checkedIn = row.get("recordId") != null;
              boolean hasLeave = row.get("leaveId") != null;
              return Map.<String, Object>ofEntries(
                  Map.entry("id", row.get("id")),
                  Map.entry("courseId", row.get("courseId")),
                  Map.entry("courseName", row.get("courseName")),
                  Map.entry("startedAt", row.get("startedAt")),
                  Map.entry("endsAt", row.get("endsAt")),
                  Map.entry("status", row.get("status")),
                  Map.entry("method", row.get("method")),
                  Map.entry("checkedIn", checkedIn),
                  Map.entry("recordStatus", row.get("recordStatus") == null ? "" : row.get("recordStatus")),
                  Map.entry("hasLeave", hasLeave),
                  Map.entry("canRequestLeave", !checkedIn && !hasLeave));
            })
        .toList();
  }

  @GetMapping("/leave-requests")
  public List<Map<String, Object>> leaveRequests() {
    long studentId = studentId();
    return jdbc.queryForList(
        """
        SELECT lr.id,
               lr.session_id sessionId,
               co.name courseName,
               lr.reason,
               lr.status,
               lr.created_at createdAt,
               lr.reviewed_at reviewedAt
        FROM leave_requests lr
        JOIN attendance_sessions se ON se.id = lr.session_id
        JOIN courses co ON co.id = se.course_id
        WHERE lr.student_id = ?
        ORDER BY lr.id DESC
        """,
        studentId);
  }

  @PostMapping("/leave-requests")
  public Map<String, Object> createLeave(@RequestBody Map<String, Object> body) {
    long studentId = studentId();
    long sessionId = ((Number) body.get("sessionId")).longValue();
    String reason = (String) body.get("reason");
    ensureStudentCanAccessSession(studentId, sessionId);
    String now = Instant.now().toString();
    jdbc.update(
        "INSERT INTO leave_requests(session_id, student_id, reason, status, created_at) VALUES (?, ?, ?, ?, ?)",
        sessionId, studentId, reason, "PENDING", now);
    return Map.of("id", jdbc.queryForObject("SELECT last_insert_rowid()", Long.class), "status", "PENDING");
  }

  @GetMapping("/profile")
  public Map<String, Object> profile() {
    return profile(studentId());
  }

  @PutMapping("/profile")
  public Map<String, Object> updateProfile(@RequestBody Map<String, String> body) {
    long studentId = studentId();
    String displayName = body.getOrDefault("displayName", "").trim();
    if (displayName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "姓名不能为空");
    }
    jdbc.update(
        """
        UPDATE users
        SET display_name = ?
        WHERE id = (SELECT user_id FROM students WHERE id = ?)
        """,
        displayName,
        studentId);
    return profile(studentId);
  }

  @PostMapping("/password")
  public Map<String, Object> updatePassword(@RequestBody Map<String, String> body) {
    var user = AuthContext.requireRole("STUDENT");
    String currentPassword = body.getOrDefault("currentPassword", "");
    String newPassword = body.getOrDefault("newPassword", "");
    if (newPassword.length() < 6) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码至少 6 位");
    }
    Integer matched =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ? AND password_hash = ?",
            Integer.class,
            user.id(),
            PasswordHasher.hash(currentPassword));
    if (matched == null || matched == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码错误");
    }
    jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", PasswordHasher.hash(newPassword), user.id());
    return Map.of("ok", true);
  }

  private long studentId() {
    var user = AuthContext.requireRole("STUDENT");
    return jdbc.queryForList("SELECT id FROM students WHERE user_id = ?", user.id()).stream()
        .findFirst()
        .map(row -> ((Number) row.get("id")).longValue())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有学生档案"));
  }

  private Map<String, Object> one(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private void ensureStudentCanAccessSession(long studentId, long sessionId) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM attendance_sessions se
            JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
            JOIN course_enrollments ce ON ce.assignment_id = ca.id
            WHERE se.id = ? AND ce.student_id = ?
            """,
            Integer.class,
            sessionId,
            studentId);
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "学生不属于该课程名单");
    }
  }

  private Map<String, Object> profile(long studentId) {
    return one(
        """
        SELECT s.id,
               s.name,
               s.student_no studentNo,
               s.grade,
               u.username,
               u.display_name displayName,
               COALESCE(d.name, '') department
        FROM students s
        JOIN users u ON u.id = s.user_id
        LEFT JOIN departments d ON d.id = s.department_id
        WHERE s.id = ?
        """,
        studentId);
  }

  private void closeExpiredSessions() {
    jdbc.update("UPDATE attendance_sessions SET status = 'CLOSED' WHERE status = 'OPEN' AND ends_at < ?", Instant.now().toString());
  }
}
