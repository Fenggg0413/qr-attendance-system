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

// 教师端 API：授课课程、今日课表、考勤会话生命周期、请假审批、学生备注、个人设置
// 大多数方法靠 courseForTeacher / sessionForTeacher / slotForTeacher 三个 helper 做越权检查
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
  // 当课程未指定学期时使用的兜底学期标签（只用于展示，不入库）
  private static final String DEFAULT_SEMESTER = "2025-2026 第二学期";

  private final JdbcTemplate jdbc;
  private final QrTokenService qrTokenService;

  public TeacherController(JdbcTemplate jdbc, QrTokenService qrTokenService) {
    this.jdbc = jdbc;
    this.qrTokenService = qrTokenService;
  }

  // 教师授课课程列表：含班级、院系、学期、已选课人数
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

  // 查询单门课详情（含排课槽列表）；越权由 courseForTeacher 处理
  @GetMapping("/courses/{courseId}")
  public Map<String, Object> course(@PathVariable long courseId) {
    long teacherId = teacherId();
    return courseForTeacher(courseId, teacherId);
  }

  // 基于某个排课槽发起当节考勤会话
  // 约束：① 必须是今天的排课日 ② 当前时间在 [开始-15min, 结束+5min] 窗口内 ③ ends 至少 60 秒避免立即关闭
  // 越窗的补考勤走另一接口 createMakeupSession
  @PostMapping("/schedule-slots/{slotId}/attendance-sessions")
  public Map<String, Object> createSessionForSlot(
      @PathVariable long slotId, @RequestBody(required = false) Map<String, Object> body) {
    long teacherId = teacherId();
    Map<String, Object> req = body == null ? Map.of() : body;
    String method = method(req.getOrDefault("method", "QR"));
    // 槽存在性 + 教师权限校验
    Map<String, Object> slot = slotForTeacher(slotId, teacherId);

    LocalDate today = LocalDate.now(SchedulePeriods.ZONE);
    // 校验①：今天的中文星期必须等于槽的 weekday
    if (!SchedulePeriods.weekdayLabel(today).equals(String.valueOf(slot.get("weekday")))) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "今天不是该课程的排课日");
    }
    int periodStart = ((Number) slot.get("period")).intValue();
    // 若是连排课程，把 periodEnd 延伸到最后一节
    int periodEnd = mergedPeriodEnd(slot, today);
    // 校验②：时间窗 = [开始-PREP_WINDOW(15min), 结束+GRACE_WINDOW(5min)]
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
    // ends 取节次结束时刻；若已超过则兜底为 started+60s 防止刚开就关
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

  // 补考勤会话：不受节次时间窗约束，由教师指定 1-120 分钟时长 + 理由
  // 用 kind='MAKEUP' 与正常会话区分；其余字段与 createSessionForSlot 类似
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
    // 时长强制在 [1, 120] 分钟内，避免极端值（默认 30 分钟）
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

  // 教师"今日"页：返回今天所有课程行（已合并连排），每行带阶段标签 BEFORE/OPENABLE/RUNNING/ENDED 与当前会话信息
  // 流程：拉今日 slot → 合并连排成组 → 取每组首槽对应的最新会话 → 计算阶段
  @GetMapping("/today")
  public List<Map<String, Object>> today() {
    long teacherId = teacherId();
    // 进入今日页时顺手清掉过期 OPEN 会话
    closeExpiredSessions();
    LocalDate today = LocalDate.now(SchedulePeriods.ZONE);
    String weekday = SchedulePeriods.weekdayLabel(today);
    // 拉今天该教师的所有排课槽（按节次升序）
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

    // 把连续节次（如 1-2、3-4）合并为一组，每组对外呈现为一节"课"
    List<List<Map<String, Object>>> groups = mergeContiguousSlots(slots);
    String todayStr = today.toString();
    // 取每个 slot 当天最新一条会话（用于显示当前状态）
    Map<Long, Map<String, Object>> sessionBySlot = todaySessionsBySlot(teacherId, todayStr);
    Instant now = Instant.now();
    List<Map<String, Object>> result = new ArrayList<>();
    for (List<Map<String, Object>> group : groups) {
      Map<String, Object> head = group.get(0);
      // 组的起止节次：第一节~最后一节
      int periodStart = ((Number) head.get("period")).intValue();
      int periodEnd = ((Number) group.get(group.size() - 1).get("period")).intValue();
      ZonedDateTime startAt = SchedulePeriods.startAt(today, periodStart);
      ZonedDateTime endAt = SchedulePeriods.endAt(today, periodEnd);
      // 用首槽 ID 查最新会话；若该槽今天还未开课返回 null
      Map<String, Object> session = sessionBySlot.get(((Number) head.get("slotId")).longValue());
      // 计算课程阶段：决定前端按钮显示（发起考勤 / 进行中 / 已结束）
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

  // 查询课程的所有考勤会话，附带每场的出勤/迟到/请假/缺勤人数（CASE WHEN 条件聚合）
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

  // 取考勤会话的实时 QR Token（每 10 秒滚动）；若会话已关闭/超时，顺手置 CLOSED 并返回 410
  @GetMapping("/attendance-sessions/{id}/qr")
  public Map<String, Object> qr(@PathVariable long id) {
    long teacherId = teacherId();
    Map<String, Object> session = one("SELECT id, ends_at, status FROM attendance_sessions WHERE id = ? AND teacher_id = ?", id, teacherId);
    if ("CLOSED".equals(session.get("status")) || Instant.parse((String) session.get("ends_at")).isBefore(Instant.now())) {
      // 自愈：超时但仍标 OPEN 的会话置为 CLOSED
      jdbc.update("UPDATE attendance_sessions SET status = ? WHERE id = ?", "CLOSED", id);
      throw new ResponseStatusException(HttpStatus.GONE, "考勤已结束");
    }
    QrTokenService.TokenSnapshot snapshot = qrTokenService.current(id);
    return Map.of("sessionId", id, "token", snapshot.token(), "expiresAt", snapshot.expiresAt().toString(), "payload", snapshot.payload());
  }

  // 查询会话的考勤记录明细：以课程选课名单为主表 LEFT JOIN 出勤记录，缺勤学生也会展示（status=ABSENT）
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

  // 手动关闭考勤会话；ends_at 同步置为 now，避免后续查询误判为仍在窗口内
  @PostMapping("/attendance-sessions/{id}/close")
  public Map<String, Object> close(@PathVariable long id) {
    long teacherId = teacherId();
    sessionForTeacher(id, teacherId);
    String now = Instant.now().toString();
    jdbc.update("UPDATE attendance_sessions SET status = ?, ends_at = ? WHERE id = ? AND teacher_id = ?", "CLOSED", now, id, teacherId);
    return one("SELECT id, course_id, teacher_id, started_at, ends_at, status, method FROM attendance_sessions WHERE id = ? AND teacher_id = ?", id, teacherId);
  }

  // 删除考勤会话；先删关联的考勤记录和请假，最后删会话本身（避免外键约束失败）
  @DeleteMapping("/attendance-sessions/{id}")
  public void deleteSession(@PathVariable long id) {
    long teacherId = teacherId();
    sessionForTeacher(id, teacherId);
    jdbc.update("DELETE FROM attendance_records WHERE session_id = ?", id);
    jdbc.update("DELETE FROM leave_requests WHERE session_id = ?", id);
    jdbc.update("DELETE FROM attendance_sessions WHERE id = ? AND teacher_id = ?", id, teacherId);
  }

  // 查询本教师课程下的请假申请；status 可选 PENDING/APPROVED/REJECTED/ALL（默认 PENDING）
  // ALL 模式下按"待审核优先"+ id 倒序，便于教师先处理待办
  @GetMapping("/leave-requests")
  public List<Map<String, Object>> leaveRequests(@RequestParam(value = "status", required = false) String status) {
    long teacherId = teacherId();
    String normalized = status == null ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
    // 白名单校验：四个合法值之外的状态直接 400
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
      // ORDER BY 中的布尔表达式利用了 SQLite 的"true=1/false=0"，让 PENDING 排到顶部
      return jdbc.queryForList(
          baseSql + " ORDER BY (lr.status = 'PENDING') DESC, lr.id DESC", teacherId);
    }
    return jdbc.queryForList(
        baseSql + " AND lr.status = ? ORDER BY lr.id DESC", teacherId, normalized);
  }

  // 审核请假申请：批准 → APPROVED + 同步写考勤记录为 EXCUSED；拒绝 → REJECTED
  // 关键：批准时考勤记录可能已存在（学生先打卡后又改请假）或不存在 —— 先 UPDATE 试一次，affected=0 再 INSERT
  @PostMapping("/leave-requests/{id}/review")
  public Map<String, Object> reviewLeaveRequest(
      @PathVariable long id, @RequestBody Map<String, Object> body) {
    var user = AuthContext.requireRole("TEACHER");
    long teacherId = teacherId();
    // 取请假基础信息 + 所在会话的 teacher_id（用于权限校验）
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
    // 权限：会话所在课程必须由当前教师授课
    if (sessionTeacherId != teacherId) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权审核该申报");
    }
    // 幂等：已审核过的不允许重复操作（409 Conflict）
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
    // 批准时联动写考勤：先尝试 UPDATE 已有记录，affected=0 说明记录不存在再 INSERT
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
        // 学生此次没打卡也没记录，新插一条 EXCUSED
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

  // 查询课程学生名单（含本教师写在该学生身上的备注）
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

  // 保存或更新教师对学生的备注；同 (teacher, student) 唯一索引 → 先 UPDATE 再 INSERT 兜底
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

  // 查询当前教师档案
  @GetMapping("/profile")
  public Map<String, Object> profile() {
    long teacherId = teacherId();
    return profile(teacherId);
  }

  // 更新教师联系方式（仅 phone/email）；空串转 null 避免脏数据
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

  // 修改密码：旧密码哈希比对通过后写入新密码哈希；与 StudentController.updatePassword 同模式
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

  // 取当前登录教师的 teachers.id；档案不存在抛 403
  private long teacherId() {
    var user = AuthContext.requireRole("TEACHER");
    return jdbc.queryForList("SELECT id FROM teachers WHERE user_id = ?", user.id()).stream()
        .findFirst()
        .map(row -> ((Number) row.get("id")).longValue())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有教师档案"));
  }

  // 查询课程详情并附带 scheduleSlots；课程不归该教师抛 403
  private Map<String, Object> courseForTeacher(long courseId, long teacherId) {
    Map<String, Object> course =
        jdbc.queryForList(
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
    course.put("scheduleSlots", scheduleSlotsForTeacher(courseId, teacherId));
    return course;
  }

  // 课程的所有排课槽（带教室名、教师名）；ORDER BY 中通过 CASE 把中文星期映射成数字
  private List<Map<String, Object>> scheduleSlotsForTeacher(long courseId, long teacherId) {
    return jdbc.queryForList(
        """
        SELECT css.id,
               css.course_id,
               css.weekday,
               css.period,
               css.teacher_id,
               t.name teacher_name,
               css.classroom_id,
               cr.name classroom_name,
               css.course_type
        FROM course_schedule_slots css
        JOIN teachers t ON t.id = css.teacher_id
        JOIN classrooms cr ON cr.id = css.classroom_id
        WHERE css.course_id = ? AND css.teacher_id = ?
        ORDER BY
          CASE css.weekday
            WHEN '周一' THEN 1
            WHEN '周二' THEN 2
            WHEN '周三' THEN 3
            WHEN '周四' THEN 4
            WHEN '周五' THEN 5
            WHEN '周六' THEN 6
            WHEN '周日' THEN 7
            ELSE 8
          END,
          css.period
        """,
        courseId,
        teacherId);
  }

  // 取会话基础信息并要求归属当前教师，否则 404（外人看到也只是 NotFound）
  private Map<String, Object> sessionForTeacher(long sessionId, long teacherId) {
    return one("SELECT id, course_id, teacher_id, started_at, ends_at, status, method FROM attendance_sessions WHERE id = ? AND teacher_id = ?", sessionId, teacherId);
  }

  // 查教师档案详情（联表带账号、院系名）
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

  // 校验：该教师授课课程的选课名单内必须存在该学生；否则 403（防止跨课程查看学生）
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

  // 批量关闭已超时但仍 OPEN 的会话
  private void closeExpiredSessions() {
    jdbc.update("UPDATE attendance_sessions SET status = 'CLOSED' WHERE status = 'OPEN' AND ends_at < ?", Instant.now().toString());
  }

  // 取排课槽详情（含课程名、教室名）；槽不归当前教师抛 403
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

  // 给定一个排课槽，查找同课程/教师/教室/课程类型/星期下的所有节次，沿"严格相邻可合并"向后扩展，返回连排终点节次
  // 用于 createSessionForSlot：把 1-2 节课起的考勤会话 ends_at 延伸到第 2 节末，而非第 1 节末
  private int mergedPeriodEnd(Map<String, Object> slot, LocalDate date) {
    long teacherId = ((Number) slot.get("teacher_id")).longValue();
    long courseId = ((Number) slot.get("course_id")).longValue();
    long classroomId = ((Number) slot.get("classroom_id")).longValue();
    String courseType = String.valueOf(slot.get("course_type"));
    String weekday = String.valueOf(slot.get("weekday"));
    int periodStart = ((Number) slot.get("period")).intValue();
    // 查所有候选槽（同维度）按节次升序
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
    // 前向扫描：能与 last 相邻（间隔≤30min）就吸收，遇到断裂立刻退出
    for (int p : peers) {
      // p <= last 表示这节已被吸收或在起点之前，跳过
      if (p <= last) continue;
      if (SchedulePeriods.isContiguous(last, p)) {
        // 严格相邻 → 把终点推到 p
        last = p;
      } else if (p > last) {
        // 出现断裂（如 1→3）就终止，不再继续扫描
        break;
      }
    }
    return last;
  }

  // 把多个排课槽按"同课程-同教室-同课程类型"分组，组内按节次排序后按严格相邻拆成多个连续段
  // 输出按各段起始节次升序，给"今日"页提供合并视图（如 1-2 节、3-4 节算两段独立的"课"）
  private List<List<Map<String, Object>>> mergeContiguousSlots(List<Map<String, Object>> slots) {
    Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
    // 第一遍：按 key 分桶
    for (Map<String, Object> slot : slots) {
      String key = slot.get("courseId") + "-" + slot.get("classroom_id") + "-" + slot.get("courseType");
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(slot);
    }
    List<List<Map<String, Object>>> merged = new ArrayList<>();
    // 第二遍：每个桶内按节次升序，再用 isContiguous 切段
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
        // 相邻 → 加入当前段；断裂 → 收当前段，开新段
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
    // 最后按段首节次全局排序，保证今日视图按时间从早到晚展示
    merged.sort((a, b) -> Integer.compare(((Number) a.get(0).get("period")).intValue(), ((Number) b.get(0).get("period")).intValue()));
    return merged;
  }

  // 取每个排课槽今天最新一条会话；同一槽可能因补考勤而产生多场会话，这里只保留最新的
  // 关键技巧：SQL 按 id DESC 排序，putIfAbsent 在遍历时跳过同槽后续旧数据，等价于"每槽 MAX(id)"
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
              -- substr(started_at, 1, 10) 取 ISO 时间戳的日期前缀，避免按时间戳精确比较
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
      // 由于已按 id DESC 排序，putIfAbsent 自然保留最新一条
      bySlot.putIfAbsent(slotId, row);
    }
    return bySlot;
  }

  // 课程阶段判定 —— 决定"今日"页按钮的展示状态
  // RUNNING：有 OPEN 会话且未超出结束时间+恩典窗
  // BEFORE：当前时间早于开始-提前窗（还没到能发起考勤的窗口）
  // ENDED：超过结束+恩典窗
  // OPENABLE：在 [开始-提前窗, 结束+恩典窗] 内但还没创建会话
  private String phaseFor(Instant now, Instant start, Instant end, Map<String, Object> session) {
    if (session != null && "OPEN".equals(session.get("status")) && now.isBefore(end.plus(SchedulePeriods.GRACE_WINDOW))) {
      return "RUNNING";
    }
    if (now.isBefore(start.minus(SchedulePeriods.PREP_WINDOW))) return "BEFORE";
    if (now.isAfter(end.plus(SchedulePeriods.GRACE_WINDOW))) return "ENDED";
    return "OPENABLE";
  }

  // 构造考勤会话响应负载；冗余了 camelCase 和 snake_case 两种字段名以同时兼容 Web 和 Android 客户端
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

  // 工具：查询单条记录，未找到抛 404
  private Map<String, Object> one(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  // 工具：宽松地把入参转为 Number（兼容 JSON 数字与字符串数字，空值返 0）
  private Number number(Object value) {
    if (value instanceof Number numeric) return numeric;
    if (value instanceof String text && !text.isBlank()) return Integer.parseInt(text);
    return 0;
  }

  // 工具：规范化并校验考勤方式，仅允许 QR/CODE/MANUAL 三种
  private String method(Object value) {
    String method = String.valueOf(value).toUpperCase(Locale.ROOT);
    if (!List.of("QR", "CODE", "MANUAL").contains(method)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的考勤方式");
    }
    return method;
  }

  // 工具：空串或 null 一律返 null（避免空字符串污染数据库）
  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
