package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.auth.PasswordHasher;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final JdbcTemplate jdbc;

  public AdminController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/teachers")
  public List<Map<String, Object>> teachers() {
    admin();
    return jdbc.queryForList("SELECT t.id, t.name, t.department, u.username FROM teachers t JOIN users u ON u.id = t.user_id ORDER BY t.id");
  }

  @PostMapping("/teachers")
  public Map<String, Object> createTeacher(@RequestBody Map<String, String> body) {
    admin();
    long userId = user(body.get("username"), body.getOrDefault("password", "teacher123"), "TEACHER", body.get("name"));
    long id = insert("INSERT INTO teachers(user_id, name, department) VALUES (?, ?, ?)", userId, body.get("name"), body.get("department"));
    return Map.of("id", id);
  }

  @PutMapping("/teachers/{id}")
  public Map<String, Object> updateTeacher(@PathVariable long id, @RequestBody Map<String, String> body) {
    admin();
    jdbc.update("UPDATE teachers SET name = ?, department = ? WHERE id = ?", body.get("name"), body.get("department"), id);
    return Map.of("id", id);
  }

  @DeleteMapping("/teachers/{id}")
  public void deleteTeacher(@PathVariable long id) {
    admin();
    jdbc.update("DELETE FROM teachers WHERE id = ?", id);
  }

  @GetMapping("/students")
  public List<Map<String, Object>> students() {
    admin();
    return jdbc.queryForList("SELECT s.id, s.name, s.student_no, s.class_id, c.name class_name, u.username FROM students s JOIN users u ON u.id = s.user_id JOIN classes c ON c.id = s.class_id ORDER BY s.id");
  }

  @PostMapping("/students")
  public Map<String, Object> createStudent(@RequestBody Map<String, String> body) {
    admin();
    long userId = user(body.get("username"), body.getOrDefault("password", "student123"), "STUDENT", body.get("name"));
    long id = insert("INSERT INTO students(user_id, class_id, name, student_no) VALUES (?, ?, ?, ?)", userId, Long.parseLong(body.get("classId")), body.get("name"), body.get("studentNo"));
    return Map.of("id", id);
  }

  @PutMapping("/students/{id}")
  public Map<String, Object> updateStudent(@PathVariable long id, @RequestBody Map<String, String> body) {
    admin();
    jdbc.update("UPDATE students SET class_id = ?, name = ?, student_no = ? WHERE id = ?", Long.parseLong(body.get("classId")), body.get("name"), body.get("studentNo"), id);
    return Map.of("id", id);
  }

  @DeleteMapping("/students/{id}")
  public void deleteStudent(@PathVariable long id) {
    admin();
    jdbc.update("DELETE FROM students WHERE id = ?", id);
  }

  @GetMapping("/classes")
  public List<Map<String, Object>> classes() {
    admin();
    return jdbc.queryForList("SELECT id, name, grade FROM classes ORDER BY id");
  }

  @PostMapping("/classes")
  public Map<String, Object> createClass(@RequestBody Map<String, String> body) {
    admin();
    return Map.of("id", insert("INSERT INTO classes(name, grade) VALUES (?, ?)", body.get("name"), body.get("grade")));
  }

  @PutMapping("/classes/{id}")
  public Map<String, Object> updateClass(@PathVariable long id, @RequestBody Map<String, String> body) {
    admin();
    jdbc.update("UPDATE classes SET name = ?, grade = ? WHERE id = ?", body.get("name"), body.get("grade"), id);
    return Map.of("id", id);
  }

  @DeleteMapping("/classes/{id}")
  public void deleteClass(@PathVariable long id) {
    admin();
    jdbc.update("DELETE FROM classes WHERE id = ?", id);
  }

  @GetMapping("/courses")
  public List<Map<String, Object>> courses() {
    admin();
    return jdbc.queryForList("SELECT co.id, co.name, co.code, co.class_id, cl.name class_name FROM courses co JOIN classes cl ON cl.id = co.class_id ORDER BY co.id");
  }

  @PostMapping("/courses")
  public Map<String, Object> createCourse(@RequestBody Map<String, String> body) {
    admin();
    return Map.of("id", insert("INSERT INTO courses(name, code, class_id) VALUES (?, ?, ?)", body.get("name"), body.get("code"), Long.parseLong(body.get("classId"))));
  }

  @PutMapping("/courses/{id}")
  public Map<String, Object> updateCourse(@PathVariable long id, @RequestBody Map<String, String> body) {
    admin();
    jdbc.update("UPDATE courses SET name = ?, code = ?, class_id = ? WHERE id = ?", body.get("name"), body.get("code"), Long.parseLong(body.get("classId")), id);
    return Map.of("id", id);
  }

  @DeleteMapping("/courses/{id}")
  public void deleteCourse(@PathVariable long id) {
    admin();
    jdbc.update("DELETE FROM courses WHERE id = ?", id);
  }

  @GetMapping("/course-assignments")
  public List<Map<String, Object>> assignments() {
    admin();
    return jdbc.queryForList(
        """
        SELECT ca.id,
               ca.course_id,
               co.name course_name,
               ca.teacher_id,
               t.name teacher_name,
               ca.term
        FROM course_assignments ca
        JOIN courses co ON co.id = ca.course_id
        JOIN teachers t ON t.id = ca.teacher_id
        ORDER BY ca.id
        """);
  }

  @PostMapping("/course-assignments")
  public Map<String, Object> createAssignment(@RequestBody Map<String, String> body) {
    admin();
    long id =
        insert(
            "INSERT INTO course_assignments(course_id, teacher_id, term) VALUES (?, ?, ?)",
            Long.parseLong(body.get("courseId")),
            Long.parseLong(body.get("teacherId")),
            blankToNull(body.get("term")));
    return assignment(id);
  }

  @PutMapping("/course-assignments/{id}")
  public Map<String, Object> updateAssignment(@PathVariable long id, @RequestBody Map<String, String> body) {
    admin();
    jdbc.update(
        "UPDATE course_assignments SET course_id = ?, teacher_id = ?, term = ? WHERE id = ?",
        Long.parseLong(body.get("courseId")),
        Long.parseLong(body.get("teacherId")),
        blankToNull(body.get("term")),
        id);
    return assignment(id);
  }

  @DeleteMapping("/course-assignments/{id}")
  public void deleteAssignment(@PathVariable long id) {
    admin();
    jdbc.update("DELETE FROM course_assignments WHERE id = ?", id);
  }

  @GetMapping("/enrollments")
  public List<Map<String, Object>> enrollments() {
    admin();
    return enrollmentRows(null);
  }

  @PostMapping("/enrollments")
  public Map<String, Object> createEnrollment(@RequestBody Map<String, String> body) {
    admin();
    long assignmentId = Long.parseLong(body.get("assignmentId"));
    long studentId = Long.parseLong(body.get("studentId"));
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_enrollments WHERE assignment_id = ? AND student_id = ?",
            Integer.class,
            assignmentId,
            studentId);
    if (exists != null && exists > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "该学生已在选课名单中");
    }
    long id = insert("INSERT INTO course_enrollments(assignment_id, student_id) VALUES (?, ?)", assignmentId, studentId);
    return enrollment(id);
  }

  @DeleteMapping("/enrollments/{id}")
  public void deleteEnrollment(@PathVariable long id) {
    admin();
    jdbc.update("DELETE FROM course_enrollments WHERE id = ?", id);
  }

  @GetMapping("/attendance-records")
  public List<Map<String, Object>> attendanceRecords() {
    admin();
    return jdbc.queryForList("SELECT ar.id, ar.session_id, co.name course_name, s.name student_name, ar.status, ar.checked_in_at, ar.source FROM attendance_records ar JOIN students s ON s.id = ar.student_id JOIN attendance_sessions se ON se.id = ar.session_id JOIN courses co ON co.id = se.course_id ORDER BY ar.id DESC");
  }

  @GetMapping("/statistics")
  public List<Map<String, Object>> statistics() {
    admin();
    return jdbc.queryForList("SELECT co.name course_name, ar.status, COUNT(*) count FROM attendance_records ar JOIN attendance_sessions se ON se.id = ar.session_id JOIN courses co ON co.id = se.course_id GROUP BY co.name, ar.status ORDER BY co.name, ar.status");
  }

  @GetMapping("/attendance-stats")
  public List<Map<String, Object>> attendanceStats() {
    admin();
    return jdbc.queryForList(
        """
        SELECT se.id session_id,
               co.name course_name,
               t.name teacher_name,
               cl.name class_name,
               COUNT(s.id) total,
               SUM(CASE WHEN ar.status IN ('PRESENT', 'LATE') THEN 1 ELSE 0 END) present,
               SUM(CASE WHEN ar.status = 'EXCUSED' THEN 1 ELSE 0 END) excused,
               COUNT(s.id) - SUM(CASE WHEN ar.status IN ('PRESENT', 'LATE', 'EXCUSED') THEN 1 ELSE 0 END) absent
        FROM attendance_sessions se
        JOIN courses co ON co.id = se.course_id
        JOIN classes cl ON cl.id = co.class_id
        JOIN teachers t ON t.id = se.teacher_id
        LEFT JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
        JOIN students s ON (
          (
            EXISTS (SELECT 1 FROM course_enrollments ce WHERE ce.assignment_id = ca.id)
            AND s.id IN (SELECT ce.student_id FROM course_enrollments ce WHERE ce.assignment_id = ca.id)
          )
          OR (
            NOT EXISTS (SELECT 1 FROM course_enrollments ce WHERE ce.assignment_id = ca.id)
            AND s.class_id = co.class_id
          )
        )
        LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = s.id
        GROUP BY se.id, co.name, t.name, cl.name
        ORDER BY se.started_at DESC, se.id DESC
        """);
  }

  @GetMapping("/leave-requests")
  public List<Map<String, Object>> leaveRequests() {
    admin();
    return jdbc.queryForList("SELECT lr.id, lr.session_id, s.name student_name, lr.reason, lr.status, lr.created_at, lr.reviewed_at FROM leave_requests lr JOIN students s ON s.id = lr.student_id ORDER BY lr.id DESC");
  }

  @PostMapping("/leave-requests/{id}/review")
  public Map<String, Object> reviewLeave(@PathVariable long id, @RequestBody Map<String, String> body) {
    var admin = admin();
    String status = Boolean.parseBoolean(body.getOrDefault("approved", "false")) ? "APPROVED" : "REJECTED";
    jdbc.update("UPDATE leave_requests SET status = ?, reviewer_id = ?, reviewed_at = ? WHERE id = ?", status, admin.id(), Instant.now().toString(), id);
    if ("APPROVED".equals(status)) {
      Map<String, Object> leave = one("SELECT session_id, student_id FROM leave_requests WHERE id = ?", id);
      upsertRecord(((Number) leave.get("session_id")).longValue(), ((Number) leave.get("student_id")).longValue(), "EXCUSED", "LEAVE");
    }
    return Map.of("id", id, "status", status);
  }

  private com.example.qrattendance.auth.CurrentUser admin() {
    return AuthContext.requireRole("ADMIN");
  }

  private long user(String username, String password, String role, String displayName) {
    return insert("INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)", username, PasswordHasher.hash(password), role, displayName);
  }

  private long insert(String sql, Object... args) {
    jdbc.update(sql, args);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private Map<String, Object> one(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private Map<String, Object> assignment(long id) {
    return one(
        """
        SELECT ca.id,
               ca.course_id,
               co.name course_name,
               ca.teacher_id,
               t.name teacher_name,
               ca.term
        FROM course_assignments ca
        JOIN courses co ON co.id = ca.course_id
        JOIN teachers t ON t.id = ca.teacher_id
        WHERE ca.id = ?
        """,
        id);
  }

  private List<Map<String, Object>> enrollmentRows(Long id) {
    String where = id == null ? "" : "WHERE ce.id = ?";
    Object[] args = id == null ? new Object[] {} : new Object[] {id};
    return jdbc.queryForList(
        """
        SELECT ce.id,
               ce.assignment_id,
               ca.course_id,
               co.name course_name,
               ca.teacher_id,
               t.name teacher_name,
               ce.student_id,
               s.name student_name,
               s.student_no
        FROM course_enrollments ce
        JOIN course_assignments ca ON ca.id = ce.assignment_id
        JOIN courses co ON co.id = ca.course_id
        JOIN teachers t ON t.id = ca.teacher_id
        JOIN students s ON s.id = ce.student_id
        """
            + where
            + " ORDER BY ce.id",
        args);
  }

  private Map<String, Object> enrollment(long id) {
    return enrollmentRows(id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private void upsertRecord(long sessionId, long studentId, String status, String source) {
    int updated = jdbc.update("UPDATE attendance_records SET status = ?, source = ? WHERE session_id = ? AND student_id = ?", status, source, sessionId, studentId);
    if (updated == 0) {
      jdbc.update("INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)", sessionId, studentId, status, Instant.now().toString(), source);
    }
  }
}
