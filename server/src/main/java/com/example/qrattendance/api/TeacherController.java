package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.auth.PasswordHasher;
import com.example.qrattendance.qr.QrTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
  private static final String DEFAULT_SEMESTER = "2025-2026 第二学期";

  private final JdbcTemplate jdbc;
  private final QrTokenService qrTokenService;

  public TeacherController(JdbcTemplate jdbc, QrTokenService qrTokenService) {
    this.jdbc = jdbc;
    this.qrTokenService = qrTokenService;
  }

  @GetMapping("/courses")
  public List<Map<String, Object>> courses() {
    long teacherId = teacherId();
    return jdbc.queryForList(
        """
        SELECT co.id,
               co.name,
               co.code,
               cl.name class_name,
               d.name department_name,
               COALESCE(ca.term, ?) semester,
               COUNT(DISTINCT ce.student_id) student_count
        FROM courses co
        JOIN course_assignments ca ON ca.course_id = co.id
        LEFT JOIN course_enrollments ce ON ce.assignment_id = ca.id
        LEFT JOIN classes cl ON cl.id = co.class_id
        LEFT JOIN departments d ON d.id = co.department_id
        WHERE ca.teacher_id = ?
        GROUP BY co.id, co.name, co.code, cl.name, d.name, ca.term
        ORDER BY co.id
        """,
        DEFAULT_SEMESTER,
        teacherId);
  }

  @GetMapping("/courses/{courseId}")
  public Map<String, Object> course(@PathVariable long courseId) {
    long teacherId = teacherId();
    return courseForTeacher(courseId, teacherId);
  }

  @PostMapping("/courses/{courseId}/attendance-sessions")
  public Map<String, Object> createSession(@PathVariable long courseId, @RequestBody Map<String, Object> body) {
    long teacherId = teacherId();
    courseForTeacher(courseId, teacherId);
    int durationMinutes = Math.max(1, number(body.getOrDefault("durationMinutes", 5)).intValue());
    String method = method(body.getOrDefault("method", "QR"));
    Instant started = Instant.now();
    Instant ends = started.plusSeconds(durationMinutes * 60L);
    jdbc.update(
        "INSERT INTO attendance_sessions(course_id, teacher_id, started_at, ends_at, status, method) VALUES (?, ?, ?, ?, ?, ?)",
        courseId,
        teacherId,
        started.toString(),
        ends.toString(),
        "OPEN",
        method);
    long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    return Map.of(
        "id", id,
        "courseId", courseId,
        "course_id", courseId,
        "startedAt", started.toString(),
        "started_at", started.toString(),
        "endsAt", ends.toString(),
        "ends_at", ends.toString(),
        "status", "OPEN",
        "method", method);
  }

  @GetMapping("/courses/{courseId}/attendance-sessions")
  public List<Map<String, Object>> sessions(@PathVariable long courseId) {
    long teacherId = teacherId();
    courseForTeacher(courseId, teacherId);
    closeExpiredSessions();
    return jdbc.queryForList(
        """
        SELECT se.id,
               se.course_id,
               se.teacher_id,
               se.started_at,
               se.ends_at,
               se.status,
               se.method,
               SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END) present_count,
               SUM(CASE WHEN ar.status = 'LATE' THEN 1 ELSE 0 END) late_count,
               SUM(CASE WHEN ar.status = 'EXCUSED' THEN 1 ELSE 0 END) excused_count,
               COUNT(s.id) - SUM(CASE WHEN ar.status IN ('PRESENT', 'LATE', 'EXCUSED') THEN 1 ELSE 0 END) absent_count,
               COUNT(s.id) total_count
        FROM attendance_sessions se
        JOIN courses co ON co.id = se.course_id
        JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
        LEFT JOIN course_enrollments ce ON ce.assignment_id = ca.id
        LEFT JOIN students s ON s.id = ce.student_id
        LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = s.id
        WHERE se.course_id = ? AND se.teacher_id = ?
        GROUP BY se.id
        ORDER BY se.started_at DESC, se.id DESC
        """,
        courseId,
        teacherId);
  }

  @GetMapping("/attendance-sessions/{id}/qr")
  public Map<String, Object> qr(@PathVariable long id) {
    long teacherId = teacherId();
    Map<String, Object> session = one("SELECT id, ends_at, status FROM attendance_sessions WHERE id = ? AND teacher_id = ?", id, teacherId);
    if ("CLOSED".equals(session.get("status")) || Instant.parse((String) session.get("ends_at")).isBefore(Instant.now())) {
      jdbc.update("UPDATE attendance_sessions SET status = ? WHERE id = ?", "CLOSED", id);
      throw new ResponseStatusException(HttpStatus.GONE, "考勤已结束");
    }
    QrTokenService.TokenSnapshot snapshot = qrTokenService.current(id);
    return Map.of("sessionId", id, "token", snapshot.token(), "expiresAt", snapshot.expiresAt().toString(), "payload", snapshot.payload());
  }

  @GetMapping("/attendance-sessions/{id}/records")
  public List<Map<String, Object>> records(@PathVariable long id) {
    long teacherId = teacherId();
    sessionForTeacher(id, teacherId);
    return jdbc.queryForList(
        """
        SELECT ar.id,
               se.id session_id,
               s.id student_id,
               s.name student_name,
               s.student_no,
               COALESCE(ar.status, 'ABSENT') status,
               ar.checked_in_at,
               ar.source,
               COALESCE(sn.note, '') note
        FROM attendance_sessions se
        JOIN courses co ON co.id = se.course_id
        JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
        JOIN course_enrollments ce ON ce.assignment_id = ca.id
        JOIN students s ON s.id = ce.student_id
        LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = s.id
        LEFT JOIN student_notes sn ON sn.teacher_id = se.teacher_id AND sn.student_id = s.id
        WHERE se.id = ? AND se.teacher_id = ?
        ORDER BY s.student_no, s.name
        """,
        id,
        teacherId);
  }

  @PostMapping("/attendance-sessions/{id}/close")
  public Map<String, Object> close(@PathVariable long id) {
    long teacherId = teacherId();
    sessionForTeacher(id, teacherId);
    String now = Instant.now().toString();
    jdbc.update("UPDATE attendance_sessions SET status = ?, ends_at = ? WHERE id = ? AND teacher_id = ?", "CLOSED", now, id, teacherId);
    return one("SELECT id, course_id, teacher_id, started_at, ends_at, status, method FROM attendance_sessions WHERE id = ? AND teacher_id = ?", id, teacherId);
  }

  @DeleteMapping("/attendance-sessions/{id}")
  public void deleteSession(@PathVariable long id) {
    long teacherId = teacherId();
    sessionForTeacher(id, teacherId);
    jdbc.update("DELETE FROM attendance_records WHERE session_id = ?", id);
    jdbc.update("DELETE FROM leave_requests WHERE session_id = ?", id);
    jdbc.update("DELETE FROM attendance_sessions WHERE id = ? AND teacher_id = ?", id, teacherId);
  }

  @GetMapping("/leave-requests")
  public List<Map<String, Object>> leaveRequests(@RequestParam(value = "status", required = false) String status) {
    long teacherId = teacherId();
    String normalized = status == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
    List<String> allowed = List.of("PENDING", "APPROVED", "REJECTED", "ALL");
    if (!allowed.contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的状态过滤");
    }
    String baseSql =
        """
        SELECT lr.id,
               lr.session_id,
               lr.student_id,
               lr.reason,
               lr.status,
               lr.created_at,
               lr.reviewed_at,
               s.name student_name,
               s.student_no,
               co.id course_id,
               co.name course_name,
               co.code course_code,
               se.started_at session_started_at,
               se.ends_at session_ends_at,
               COALESCE(u.display_name, u.username) reviewer_name
          FROM leave_requests lr
          JOIN students s ON s.id = lr.student_id
          JOIN attendance_sessions se ON se.id = lr.session_id
          JOIN courses co ON co.id = se.course_id
          LEFT JOIN users u ON u.id = lr.reviewer_id
         WHERE se.teacher_id = ?
        """;
    if ("ALL".equals(normalized)) {
      return jdbc.queryForList(
          baseSql + " ORDER BY (lr.status = 'PENDING') DESC, lr.id DESC", teacherId);
    }
    return jdbc.queryForList(
        baseSql + " AND lr.status = ? ORDER BY lr.id DESC", teacherId, normalized);
  }

  @PostMapping("/leave-requests/{id}/review")
  public Map<String, Object> reviewLeaveRequest(
      @PathVariable long id, @RequestBody Map<String, Object> body) {
    var user = AuthContext.requireRole("TEACHER");
    long teacherId = teacherId();
    Map<String, Object> request =
        jdbc.queryForList(
                """
                SELECT lr.id, lr.session_id, lr.student_id, lr.status, se.teacher_id
                  FROM leave_requests lr
                  JOIN attendance_sessions se ON se.id = lr.session_id
                 WHERE lr.id = ?
                """,
                id)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申报不存在"));
    long sessionTeacherId = ((Number) request.get("teacher_id")).longValue();
    if (sessionTeacherId != teacherId) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权审核该申报");
    }
    if (!"PENDING".equals(request.get("status"))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "申报已审核，无法重复操作");
    }
    boolean approved = Boolean.parseBoolean(String.valueOf(body.getOrDefault("approved", false)));
    String newStatus = approved ? "APPROVED" : "REJECTED";
    String now = Instant.now().toString();
    jdbc.update(
        "UPDATE leave_requests SET status = ?, reviewer_id = ?, reviewed_at = ? WHERE id = ?",
        newStatus,
        user.id(),
        now,
        id);
    if (approved) {
      long sessionId = ((Number) request.get("session_id")).longValue();
      long studentId = ((Number) request.get("student_id")).longValue();
      int updated =
          jdbc.update(
              "UPDATE attendance_records SET status = ?, source = ? WHERE session_id = ? AND student_id = ?",
              "EXCUSED",
              "LEAVE",
              sessionId,
              studentId);
      if (updated == 0) {
        jdbc.update(
            "INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)",
            sessionId,
            studentId,
            "EXCUSED",
            now,
            "LEAVE");
      }
    }
    return Map.of("id", id, "status", newStatus, "reviewed_at", now);
  }

  @GetMapping("/courses/{courseId}/students")
  public List<Map<String, Object>> students(@PathVariable long courseId) {
    long teacherId = teacherId();
    courseForTeacher(courseId, teacherId);
    return jdbc.queryForList(
        """
        SELECT s.id,
               s.name,
               s.student_no,
               COALESCE(sn.note, '') note
        FROM courses co
        JOIN course_assignments ca ON ca.course_id = co.id AND ca.teacher_id = ?
        JOIN course_enrollments ce ON ce.assignment_id = ca.id
        JOIN students s ON s.id = ce.student_id
        LEFT JOIN student_notes sn ON sn.teacher_id = ? AND sn.student_id = s.id
        WHERE co.id = ?
        ORDER BY s.student_no, s.name
        """,
        teacherId,
        teacherId,
        courseId);
  }

  @PutMapping("/students/{studentId}/note")
  public Map<String, Object> saveNote(@PathVariable long studentId, @RequestBody Map<String, String> body) {
    long teacherId = teacherId();
    ensureTeacherCanAccessStudent(teacherId, studentId);
    String note = body.getOrDefault("note", "");
    int updated = jdbc.update("UPDATE student_notes SET note = ? WHERE teacher_id = ? AND student_id = ?", note, teacherId, studentId);
    if (updated == 0) {
      jdbc.update("INSERT INTO student_notes(teacher_id, student_id, note) VALUES (?, ?, ?)", teacherId, studentId, note);
    }
    return Map.of("studentId", studentId, "note", note);
  }

  @GetMapping("/profile")
  public Map<String, Object> profile() {
    long teacherId = teacherId();
    return profile(teacherId);
  }

  @PutMapping("/profile")
  public Map<String, Object> updateProfile(@RequestBody Map<String, String> body) {
    long teacherId = teacherId();
    jdbc.update(
        "UPDATE teachers SET phone = ?, email = ? WHERE id = ?",
        blankToNull(body.get("phone")),
        blankToNull(body.get("email")),
        teacherId);
    return profile(teacherId);
  }

  @PostMapping("/password")
  public Map<String, Object> updatePassword(@RequestBody Map<String, String> body) {
    var user = AuthContext.requireRole("TEACHER");
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

  private long teacherId() {
    var user = AuthContext.requireRole("TEACHER");
    return jdbc.queryForList("SELECT id FROM teachers WHERE user_id = ?", user.id()).stream()
        .findFirst()
        .map(row -> ((Number) row.get("id")).longValue())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有教师档案"));
  }

  private Map<String, Object> courseForTeacher(long courseId, long teacherId) {
    return jdbc.queryForList(
            """
            SELECT co.id,
                   co.name,
                   co.code,
                   cl.name class_name,
                   d.name department_name,
                   COALESCE(ca.term, ?) semester,
                   COUNT(DISTINCT ce.student_id) student_count
            FROM courses co
            JOIN course_assignments ca ON ca.course_id = co.id
            LEFT JOIN course_enrollments ce ON ce.assignment_id = ca.id
            LEFT JOIN classes cl ON cl.id = co.class_id
            LEFT JOIN departments d ON d.id = co.department_id
            WHERE co.id = ? AND ca.teacher_id = ?
            GROUP BY co.id, co.name, co.code, cl.name, d.name, ca.term
            """,
            DEFAULT_SEMESTER,
            courseId,
            teacherId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "课程未分配给当前教师"));
  }

  private Map<String, Object> sessionForTeacher(long sessionId, long teacherId) {
    return one("SELECT id, course_id, teacher_id, started_at, ends_at, status, method FROM attendance_sessions WHERE id = ? AND teacher_id = ?", sessionId, teacherId);
  }

  private Map<String, Object> profile(long teacherId) {
    return one(
        """
        SELECT t.id,
               t.name,
               COALESCE(d.name, t.department) department,
               t.phone,
               t.email,
               u.username,
               u.display_name
        FROM teachers t
        JOIN users u ON u.id = t.user_id
        LEFT JOIN departments d ON d.id = t.department_id
        WHERE t.id = ?
        """,
        teacherId);
  }

  private void ensureTeacherCanAccessStudent(long teacherId, long studentId) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM students s
            JOIN course_enrollments ce ON ce.student_id = s.id
            JOIN course_assignments ca ON ca.id = ce.assignment_id
            WHERE s.id = ? AND ca.teacher_id = ?
            """,
            Integer.class,
            studentId,
            teacherId);
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该学生");
    }
  }

  private void closeExpiredSessions() {
    jdbc.update("UPDATE attendance_sessions SET status = 'CLOSED' WHERE status = 'OPEN' AND ends_at < ?", Instant.now().toString());
  }

  private Map<String, Object> one(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private Number number(Object value) {
    if (value instanceof Number numeric) return numeric;
    if (value instanceof String text && !text.isBlank()) return Integer.parseInt(text);
    return 0;
  }

  private String method(Object value) {
    String method = String.valueOf(value).toUpperCase(Locale.ROOT);
    if (!List.of("QR", "CODE", "MANUAL").contains(method)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的考勤方式");
    }
    return method;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
