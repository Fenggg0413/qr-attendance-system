package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.auth.PasswordHasher;
import com.example.qrattendance.qr.QrTokenService;
import com.example.qrattendance.schedule.SchedulePeriods;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

  @PostMapping("/schedule-slots/{slotId}/attendance-sessions")
  public Map<String, Object> createSessionForSlot(
      @PathVariable long slotId, @RequestBody(required = false) Map<String, Object> body) {
    long teacherId = teacherId();
    Map<String, Object> req = body == null ? Map.of() : body;
    String method = method(req.getOrDefault("method", "QR"));
    Map<String, Object> slot = slotForTeacher(slotId, teacherId);

    LocalDate today = LocalDate.now(SchedulePeriods.ZONE);
    if (!SchedulePeriods.weekdayLabel(today).equals(String.valueOf(slot.get("weekday")))) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "今天不是该课程的排课日");
    }
    int periodStart = ((Number) slot.get("period")).intValue();
    int periodEnd = mergedPeriodEnd(slot, today);
    ZonedDateTime windowStart = SchedulePeriods.startAt(today, periodStart).minus(SchedulePeriods.PREP_WINDOW);
    ZonedDateTime windowEnd = SchedulePeriods.endAt(today, periodEnd).plus(SchedulePeriods.GRACE_WINDOW);
    ZonedDateTime now = ZonedDateTime.now(SchedulePeriods.ZONE);
    if (now.isBefore(windowStart)) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "尚未到上课时间，无法发起考勤");
    }
    if (now.isAfter(windowEnd)) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "已超出本节课时间窗，请走补考勤");
    }

    long courseId = ((Number) slot.get("course_id")).longValue();
    Instant started = Instant.now();
    Instant ends = SchedulePeriods.endAt(today, periodEnd).toInstant();
    if (!ends.isAfter(started)) {
      ends = started.plusSeconds(60);
    }
    jdbc.update(
        "INSERT INTO attendance_sessions(course_id, teacher_id, started_at, ends_at, status, method, schedule_slot_id, period_end, kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        courseId,
        teacherId,
        started.toString(),
        ends.toString(),
        "OPEN",
        method,
        slotId,
        periodEnd,
        "SCHEDULED");
    long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    return sessionPayload(id, courseId, slotId, periodStart, periodEnd, started, ends, method, "SCHEDULED");
  }

  @PostMapping("/attendance-sessions/makeup")
  public Map<String, Object> createMakeupSession(@RequestBody Map<String, Object> body) {
    long teacherId = teacherId();
    if (body == null || body.get("slotId") == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少排课节次");
    }
    long slotId = ((Number) body.get("slotId")).longValue();
    String reason = String.valueOf(body.getOrDefault("reason", "")).trim();
    if (reason.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写补考勤理由");
    }
    String method = method(body.getOrDefault("method", "QR"));
    int durationMinutes = Math.max(1, Math.min(120, number(body.getOrDefault("durationMinutes", 30)).intValue()));
    Map<String, Object> slot = slotForTeacher(slotId, teacherId);

    long courseId = ((Number) slot.get("course_id")).longValue();
    int periodStart = ((Number) slot.get("period")).intValue();
    int periodEnd = mergedPeriodEnd(slot, LocalDate.now(SchedulePeriods.ZONE));
    Instant started = Instant.now();
    Instant ends = started.plusSeconds(durationMinutes * 60L);
    jdbc.update(
        "INSERT INTO attendance_sessions(course_id, teacher_id, started_at, ends_at, status, method, schedule_slot_id, period_end, kind, makeup_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        courseId,
        teacherId,
        started.toString(),
        ends.toString(),
        "OPEN",
        method,
        slotId,
        periodEnd,
        "MAKEUP",
        reason);
    long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    Map<String, Object> payload = new LinkedHashMap<>(sessionPayload(id, courseId, slotId, periodStart, periodEnd, started, ends, method, "MAKEUP"));
    payload.put("makeupReason", reason);
    return payload;
  }

  @GetMapping("/today")
  public List<Map<String, Object>> today() {
    long teacherId = teacherId();
    closeExpiredSessions();
    LocalDate today = LocalDate.now(SchedulePeriods.ZONE);
    String weekday = SchedulePeriods.weekdayLabel(today);
    List<Map<String, Object>> slots =
        jdbc.queryForList(
            """
            SELECT css.id slotId,
                   css.weekday,
                   css.period,
                   css.course_type courseType,
                   css.classroom_id,
                   co.id courseId,
                   co.name courseName,
                   co.code courseCode,
                   COALESCE(cl.name, '') classroomName,
                   COALESCE(cls.name, '') className
            FROM course_schedule_slots css
            JOIN courses co ON co.id = css.course_id
            LEFT JOIN classrooms cl ON cl.id = css.classroom_id
            LEFT JOIN classes cls ON cls.id = co.class_id
            WHERE css.teacher_id = ? AND css.weekday = ?
            ORDER BY css.period, css.id
            """,
            teacherId,
            weekday);

    List<List<Map<String, Object>>> groups = mergeContiguousSlots(slots);
    String todayStr = today.toString();
    Map<Long, Map<String, Object>> sessionBySlot = todaySessionsBySlot(teacherId, todayStr);
    Instant now = Instant.now();
    List<Map<String, Object>> result = new ArrayList<>();
    for (List<Map<String, Object>> group : groups) {
      Map<String, Object> head = group.get(0);
      int periodStart = ((Number) head.get("period")).intValue();
      int periodEnd = ((Number) group.get(group.size() - 1).get("period")).intValue();
      ZonedDateTime startAt = SchedulePeriods.startAt(today, periodStart);
      ZonedDateTime endAt = SchedulePeriods.endAt(today, periodEnd);
      Map<String, Object> session = sessionBySlot.get(((Number) head.get("slotId")).longValue());
      String phase = phaseFor(now, startAt.toInstant(), endAt.toInstant(), session);

      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("slotId", head.get("slotId"));
      entry.put("courseId", head.get("courseId"));
      entry.put("courseName", head.get("courseName"));
      entry.put("courseCode", head.get("courseCode"));
      entry.put("classroomName", head.get("classroomName"));
      entry.put("className", head.get("className"));
      entry.put("courseType", head.get("courseType"));
      entry.put("periodStart", periodStart);
      entry.put("periodEnd", periodEnd);
      entry.put("startTime", startAt.toOffsetDateTime().toString());
      entry.put("endTime", endAt.toOffsetDateTime().toString());
      entry.put("phase", phase);
      entry.put("session", session);
      result.add(entry);
    }
    return result;
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

  private Map<String, Object> slotForTeacher(long slotId, long teacherId) {
    return jdbc.queryForList(
            """
            SELECT css.id, css.course_id, css.teacher_id, css.classroom_id, css.weekday, css.period, css.course_type,
                   co.name course_name, COALESCE(cl.name, '') classroom_name
            FROM course_schedule_slots css
            JOIN courses co ON co.id = css.course_id
            LEFT JOIN classrooms cl ON cl.id = css.classroom_id
            WHERE css.id = ? AND css.teacher_id = ?
            """,
            slotId,
            teacherId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "排课节次未分配给当前教师"));
  }

  private int mergedPeriodEnd(Map<String, Object> slot, LocalDate date) {
    long teacherId = ((Number) slot.get("teacher_id")).longValue();
    long courseId = ((Number) slot.get("course_id")).longValue();
    long classroomId = ((Number) slot.get("classroom_id")).longValue();
    String courseType = String.valueOf(slot.get("course_type"));
    String weekday = String.valueOf(slot.get("weekday"));
    int periodStart = ((Number) slot.get("period")).intValue();
    List<Integer> peers =
        jdbc.queryForList(
                """
                SELECT period FROM course_schedule_slots
                WHERE teacher_id = ? AND course_id = ? AND classroom_id = ? AND weekday = ? AND course_type = ?
                ORDER BY period
                """,
                Integer.class,
                teacherId,
                courseId,
                classroomId,
                weekday,
                courseType);
    int last = periodStart;
    for (int p : peers) {
      if (p <= last) continue;
      if (SchedulePeriods.isContiguous(last, p)) {
        last = p;
      } else if (p > last) {
        break;
      }
    }
    return last;
  }

  private List<List<Map<String, Object>>> mergeContiguousSlots(List<Map<String, Object>> slots) {
    Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
    for (Map<String, Object> slot : slots) {
      String key = slot.get("courseId") + "-" + slot.get("classroom_id") + "-" + slot.get("courseType");
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(slot);
    }
    List<List<Map<String, Object>>> merged = new ArrayList<>();
    for (List<Map<String, Object>> group : groups.values()) {
      group.sort((a, b) -> Integer.compare(((Number) a.get("period")).intValue(), ((Number) b.get("period")).intValue()));
      List<Map<String, Object>> current = new ArrayList<>();
      for (Map<String, Object> slot : group) {
        int p = ((Number) slot.get("period")).intValue();
        if (current.isEmpty()) {
          current.add(slot);
          continue;
        }
        int prev = ((Number) current.get(current.size() - 1).get("period")).intValue();
        if (SchedulePeriods.isContiguous(prev, p)) {
          current.add(slot);
        } else {
          merged.add(current);
          current = new ArrayList<>();
          current.add(slot);
        }
      }
      if (!current.isEmpty()) merged.add(current);
    }
    merged.sort((a, b) -> Integer.compare(((Number) a.get(0).get("period")).intValue(), ((Number) b.get(0).get("period")).intValue()));
    return merged;
  }

  private Map<Long, Map<String, Object>> todaySessionsBySlot(long teacherId, String today) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT se.id,
                   se.schedule_slot_id slotId,
                   se.started_at,
                   se.ends_at,
                   se.status,
                   se.method,
                   se.kind,
                   se.makeup_reason makeupReason,
                   SUM(CASE WHEN ar.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END) presentCount,
                   COUNT(s.id) totalCount
            FROM attendance_sessions se
            JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
            LEFT JOIN course_enrollments ce ON ce.assignment_id = ca.id
            LEFT JOIN students s ON s.id = ce.student_id
            LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = s.id
            WHERE se.teacher_id = ?
              AND se.schedule_slot_id IS NOT NULL
              AND substr(se.started_at, 1, 10) = ?
            GROUP BY se.id
            ORDER BY se.id DESC
            """,
            teacherId,
            today);
    Map<Long, Map<String, Object>> bySlot = new HashMap<>();
    for (Map<String, Object> row : rows) {
      Object slotIdObj = row.get("slotId");
      if (slotIdObj == null) continue;
      long slotId = ((Number) slotIdObj).longValue();
      bySlot.putIfAbsent(slotId, row);
    }
    return bySlot;
  }

  private String phaseFor(Instant now, Instant start, Instant end, Map<String, Object> session) {
    if (session != null && "OPEN".equals(session.get("status")) && now.isBefore(end.plus(SchedulePeriods.GRACE_WINDOW))) {
      return "RUNNING";
    }
    if (now.isBefore(start.minus(SchedulePeriods.PREP_WINDOW))) return "BEFORE";
    if (now.isAfter(end.plus(SchedulePeriods.GRACE_WINDOW))) return "ENDED";
    return "OPENABLE";
  }

  private Map<String, Object> sessionPayload(
      long id,
      long courseId,
      long slotId,
      int periodStart,
      int periodEnd,
      Instant started,
      Instant ends,
      String method,
      String kind) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", id);
    payload.put("courseId", courseId);
    payload.put("course_id", courseId);
    payload.put("scheduleSlotId", slotId);
    payload.put("schedule_slot_id", slotId);
    payload.put("periodStart", periodStart);
    payload.put("periodEnd", periodEnd);
    payload.put("period_end", periodEnd);
    payload.put("startedAt", started.toString());
    payload.put("started_at", started.toString());
    payload.put("endsAt", ends.toString());
    payload.put("ends_at", ends.toString());
    payload.put("status", "OPEN");
    payload.put("method", method);
    payload.put("kind", kind);
    return payload;
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
