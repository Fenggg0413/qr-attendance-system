package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.qr.QrTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
        one("SELECT se.id, se.course_id, se.ends_at, se.status, co.class_id FROM attendance_sessions se JOIN courses co ON co.id = se.course_id WHERE se.id = ?", sessionId);
    if ("CLOSED".equals(session.get("status")) || Instant.parse((String) session.get("ends_at")).isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.GONE, "考勤已结束");
    }
    if (!qrTokenService.acceptsCurrent(sessionId, token)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二维码已失效");
    }
    Integer eligible =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM students WHERE id = ? AND class_id = ?",
            Integer.class,
            studentId,
            ((Number) session.get("class_id")).longValue());
    if (eligible == null || eligible == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "学生不属于该课程班级");

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

  @PostMapping("/leave-requests")
  public Map<String, Object> createLeave(@RequestBody Map<String, Object> body) {
    long studentId = studentId();
    long sessionId = ((Number) body.get("sessionId")).longValue();
    String reason = (String) body.get("reason");
    String now = Instant.now().toString();
    jdbc.update(
        "INSERT INTO leave_requests(session_id, student_id, reason, status, created_at) VALUES (?, ?, ?, ?, ?)",
        sessionId, studentId, reason, "PENDING", now);
    return Map.of("id", jdbc.queryForObject("SELECT last_insert_rowid()", Long.class), "status", "PENDING");
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
}
