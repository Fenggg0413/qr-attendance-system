package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.auth.PasswordHasher;
import com.example.qrattendance.qr.QrTokenService;
import java.time.Instant;
import java.time.LocalDate;
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

// 学生端 API：扫码打卡、查看课表/考勤/请假、个人信息维护
// 所有方法都通过 studentId() 取当前登录学生（角色 STUDENT），权限隔离在 helper 层
@RestController
@RequestMapping("/api/student")
public class StudentController {
  private final JdbcTemplate jdbc;
  private final QrTokenService qrTokenService;

  public StudentController(JdbcTemplate jdbc, QrTokenService qrTokenService) {
    this.jdbc = jdbc;
    this.qrTokenService = qrTokenService;
  }

  // 扫码打卡：四步校验 → 写入考勤记录
  // ① 会话存在且未关闭/未超时 ② QR Token 仍在当前 10s 桶内 ③ 学生在该课选课名单内 ④ 该会话未重复打卡
  // 重复打卡返回 duplicate=true 但不报错，方便客户端幂等重试
  @PostMapping("/check-ins")
  public Map<String, Object> checkIn(@RequestBody Map<String, Object> body) {
    long studentId = studentId();
    long sessionId = ((Number) body.get("sessionId")).longValue();
    String token = (String) body.get("token");
    // 取会话基础信息；不存在则 one() 抛 404
    Map<String, Object> session =
        one("SELECT se.id, se.course_id, se.teacher_id, se.ends_at, se.status FROM attendance_sessions se WHERE se.id = ?", sessionId);
    // 状态/时间校验：CLOSED 或 ends_at 已过 → 410 Gone（语义：考勤已结束）
    if ("CLOSED".equals(session.get("status")) || Instant.parse((String) session.get("ends_at")).isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.GONE, "考勤已结束");
    }
    // QR Token 校验：调用 QrTokenService 用当前 10s 桶重算 HMAC 比较
    if (!qrTokenService.acceptsCurrent(sessionId, token)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二维码已失效");
    }
    // 选课资格校验：用 course_assignments + course_enrollments 三表 JOIN 确认学生选了这门课
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

    // 去重：查是否已有同 (session, student) 的考勤记录；有则直接返回 duplicate=true
    List<Map<String, Object>> existing = jdbc.queryForList("SELECT id, status, checked_in_at FROM attendance_records WHERE session_id = ? AND student_id = ?", sessionId, studentId);
    if (!existing.isEmpty()) {
      return Map.of("record", existing.getFirst(), "duplicate", true);
    }
    // 首次打卡：固定写入 PRESENT 状态（迟到由教师另行调整），来源 QR
    String now = Instant.now().toString();
    jdbc.update(
        "INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)",
        sessionId, studentId, "PRESENT", now, "QR");
    long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    return Map.of("record", Map.of("id", id, "sessionId", sessionId, "status", "PRESENT", "checkedInAt", now), "duplicate", false);
  }

  // 查询本学生的全部考勤记录（按 id 倒序，最新在前）；联表带出课程名、教师名、教室名
  @GetMapping("/attendance-records")
  public List<Map<String, Object>> records() {
    long studentId = studentId();
    return jdbc.queryForList(
        "SELECT ar.id, ar.session_id sessionId, co.name courseName, ar.status, ar.checked_in_at checkedInAt, ar.source, u.display_name teacherName, COALESCE(cl.name, '') classroomName FROM attendance_records ar JOIN attendance_sessions se ON se.id = ar.session_id JOIN courses co ON co.id = se.course_id JOIN teachers t ON t.id = se.teacher_id JOIN users u ON u.id = t.user_id LEFT JOIN course_schedule_slots css ON css.id = se.schedule_slot_id LEFT JOIN classrooms cl ON cl.id = css.classroom_id WHERE ar.student_id = ? ORDER BY ar.id DESC",
        studentId);
  }

  // 查询本学生已选课程列表（含授课教师姓名、所属学期）
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

  // 查询本学生的周课表：按 周一→周日 + 节次 排序；ORDER BY 中的 CASE 让中文星期可比较
  @GetMapping("/schedule")
  public List<Map<String, Object>> schedule() {
    long studentId = studentId();
    return jdbc.queryForList(
        """
        SELECT css.id slotId,
               css.weekday,
               css.period,
               css.course_type courseType,
               co.id courseId,
               co.name courseName,
               co.code courseCode,
               cl.name classroomName,
               COALESCE(cl.building, '') classroomLocation,
               u.display_name teacherName,
               COALESCE(ca.term, '') term
        FROM course_enrollments ce
        JOIN course_assignments ca ON ca.id = ce.assignment_id
        JOIN courses co ON co.id = ca.course_id
        JOIN course_schedule_slots css ON css.course_id = co.id AND css.teacher_id = ca.teacher_id
        JOIN classrooms cl ON cl.id = css.classroom_id
        JOIN teachers t ON t.id = ca.teacher_id
        JOIN users u ON u.id = t.user_id
        WHERE ce.student_id = ?
        ORDER BY CASE css.weekday
                   WHEN '周一' THEN 1
                   WHEN '周二' THEN 2
                   WHEN '周三' THEN 3
                   WHEN '周四' THEN 4
                   WHEN '周五' THEN 5
                   WHEN '周六' THEN 6
                   WHEN '周日' THEN 7
                   ELSE 8
                 END,
                 css.period,
                 co.id
        """,
        studentId);
  }

  // 学生端首页仪表板：今日课程数、四种状态统计、本学期出勤率、今日课程的实时考勤状态
  // 关键查询：用 CTE (WITH latest_sessions) 取每个 slot 当天最新一条会话，再 LEFT JOIN 当前学生的出勤/请假状态
  @GetMapping("/dashboard")
  public Map<String, Object> dashboard() {
    long studentId = studentId();
    String today = LocalDate.now().toString();
    String weekday = todayWeekday();
    // 统计今天有几节课（按 weekday 匹配）
    int todayCount =
        count(
            """
            SELECT COUNT(*)
            FROM course_enrollments ce
            JOIN course_assignments ca ON ca.id = ce.assignment_id
            JOIN course_schedule_slots css ON css.course_id = ca.course_id AND css.teacher_id = ca.teacher_id
            WHERE ce.student_id = ? AND css.weekday = ?
            """,
            studentId,
            weekday);
    // 四类状态分别统计
    int checkedInCount = attendanceStatusCount(studentId, "PRESENT");
    int absentCount = attendanceStatusCount(studentId, "ABSENT");
    int lateCount = attendanceStatusCount(studentId, "LATE");
    int excusedCount = attendanceStatusCount(studentId, "EXCUSED");
    int pendingLeaveCount = count("SELECT COUNT(*) FROM leave_requests WHERE student_id = ? AND status = 'PENDING'", studentId);
    int total = checkedInCount + absentCount + lateCount + excusedCount;
    // 出勤率：PRESENT + LATE 都算到课；total = 0 防止除零
    double attendanceRate = total == 0 ? 0.0 : ((double) checkedInCount + lateCount) / total;

    // 今日各排课槽的最新会话与当前学生的出勤状态
    List<Map<String, Object>> todaySessions =
        jdbc.queryForList(
            """
            WITH latest_sessions AS (
              SELECT *
              FROM attendance_sessions
              WHERE id IN (
                -- 子查询：按 schedule_slot_id 分组取 MAX(id)，即每个 slot 当天最新一条会话
                SELECT MAX(id)
                FROM attendance_sessions
                WHERE substr(started_at, 1, 10) = ? AND schedule_slot_id IS NOT NULL
                GROUP BY schedule_slot_id
              )
            )
            SELECT se.id,
                   css.id slotId,
                   css.period,
                   co.id courseId,
                   co.name courseName,
                   COALESCE(cl.name, '') classroomName,
                   se.started_at startedAt,
                   se.ends_at endsAt,
                   se.status,
                   se.method,
                   COALESCE(ar.status, '') recordStatus,
                   CASE WHEN lr.id IS NULL THEN 0 ELSE 1 END hasLeave
            -- 主查询从排课槽起步，LEFT JOIN 会话（可能未开课）/ 考勤 / 请假
            FROM course_schedule_slots css
            JOIN course_assignments ca ON ca.course_id = css.course_id AND ca.teacher_id = css.teacher_id
            JOIN course_enrollments ce ON ce.assignment_id = ca.id
            JOIN courses co ON co.id = css.course_id
            JOIN classrooms cl ON cl.id = css.classroom_id
            LEFT JOIN latest_sessions se ON se.schedule_slot_id = css.id
            LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = ce.student_id
            LEFT JOIN leave_requests lr ON lr.session_id = se.id AND lr.student_id = ce.student_id
            WHERE ce.student_id = ? AND css.weekday = ?
            ORDER BY css.period, co.id, css.id
            """,
            today,
            studentId,
            weekday);

    return Map.of(
        "todayCount",
        todayCount,
        "checkedInCount",
        checkedInCount,
        "pendingLeaveCount",
        pendingLeaveCount,
        "absentCount",
        absentCount,
        "lateCount",
        lateCount,
        "excusedCount",
        excusedCount,
        "semesterAttendanceRate",
        attendanceRate,
        "todaySessions",
        todaySessions.stream()
            .map(
                row -> {
                    // 流式转响应：LEFT JOIN 可能产生 null，需逐字段做默认值兜底，防止 NPE 透传到前端
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", row.get("id") == null ? 0 : row.get("id"));
                    map.put("slotId", row.get("slotId"));
                    map.put("period", row.get("period"));
                    map.put("courseId", row.get("courseId"));
                    map.put("courseName", row.get("courseName"));
                    map.put("classroomName", row.get("classroomName") == null ? "" : row.get("classroomName"));
                    map.put("startedAt", row.get("startedAt") == null ? "" : row.get("startedAt"));
                    map.put("endsAt", row.get("endsAt") == null ? "" : row.get("endsAt"));
                    map.put("status", row.get("status") == null ? "" : row.get("status"));
                    map.put("method", row.get("method") == null ? "QR" : row.get("method"));
                    map.put("recordStatus", row.get("recordStatus") == null ? "" : row.get("recordStatus"));
                    map.put("hasLeave", asBoolean(row.get("hasLeave")));
                    return map;
                })
            .toList());
  }

  // 学生可参与的考勤会话列表
  // scope=active（默认）：仅返回 OPEN 状态会话；scope=recent：返回最近所有（含 CLOSED）
  // 每条带出三个状态：是否已打卡 checkedIn、是否已申请请假 hasLeave、是否可申请请假 canRequestLeave
  @GetMapping("/sessions")
  public List<Map<String, Object>> sessions(@RequestParam(value = "scope", defaultValue = "active") String scope) {
    long studentId = studentId();
    // 先把已超时但仍 OPEN 的会话置为 CLOSED，保证返回数据语义正确
    closeExpiredSessions();
    boolean recent = "recent".equalsIgnoreCase(scope);
    // scope=active 时附加状态过滤，否则不限制状态
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
              // 通过 LEFT JOIN 出的 id 是否为 null 判定状态
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
                  // 只有"未打卡 且 未申请请假"才允许新建请假
                  Map.entry("canRequestLeave", !checkedIn && !hasLeave));
            })
        .toList();
  }

  // 查询本学生的全部请假申请（含三态：PENDING/APPROVED/REJECTED）
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

  // 提交请假申请；先校验学生确实在该会话所属课程的名单内，避免越权
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

  // 查询当前学生的档案（姓名/学号/年级/院系/账号）
  @GetMapping("/profile")
  public Map<String, Object> profile() {
    return profile(studentId());
  }

  // 更新学生显示名（写到 users.display_name，不允许修改学号、班级等关键字段）
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

  // 修改密码：先用旧密码哈希比对，再写入新密码哈希
  @PostMapping("/password")
  public Map<String, Object> updatePassword(@RequestBody Map<String, String> body) {
    var user = AuthContext.requireRole("STUDENT");
    String currentPassword = body.getOrDefault("currentPassword", "");
    String newPassword = body.getOrDefault("newPassword", "");
    // 长度校验：至少 6 位
    if (newPassword.length() < 6) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码至少 6 位");
    }
    // 旧密码校验：用 SHA-256 哈希后到 DB 比对，避免明文驻留
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

  // 取当前登录学生的 students.id（user_id 找学生档案）；档案不存在抛 403
  private long studentId() {
    var user = AuthContext.requireRole("STUDENT");
    return jdbc.queryForList("SELECT id FROM students WHERE user_id = ?", user.id()).stream()
        .findFirst()
        .map(row -> ((Number) row.get("id")).longValue())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有学生档案"));
  }

  // 工具：查询单条记录，不存在抛 404
  private Map<String, Object> one(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  // 工具：统计本学生指定状态的考勤数（联表保证只统计当前选课记录）
  private int attendanceStatusCount(long studentId, String status) {
    return count(
        """
        SELECT COUNT(*)
        FROM attendance_records ar
        JOIN attendance_sessions se ON se.id = ar.session_id
        JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
        JOIN course_enrollments ce ON ce.assignment_id = ca.id AND ce.student_id = ar.student_id
        WHERE ar.student_id = ? AND ar.status = ?
        """,
        studentId,
        status);
  }

  // 工具：执行 COUNT 查询；null 视为 0
  private int count(String sql, Object... args) {
    Integer value = jdbc.queryForObject(sql, Integer.class, args);
    return value == null ? 0 : value;
  }

  // 工具：兼容 SQLite 返回的 hasLeave 字段（可能是 Number 0/1 或 Boolean）
  private boolean asBoolean(Object value) {
    return value instanceof Boolean bool ? bool : value instanceof Number number && number.intValue() != 0;
  }

  // 工具：将今天转换为中文星期标签（用于查询 css.weekday 字段）
  private String todayWeekday() {
    return switch (LocalDate.now().getDayOfWeek()) {
      case MONDAY -> "周一";
      case TUESDAY -> "周二";
      case WEDNESDAY -> "周三";
      case THURSDAY -> "周四";
      case FRIDAY -> "周五";
      case SATURDAY -> "周六";
      case SUNDAY -> "周日";
    };
  }

  // 工具：校验学生在该会话所属课程的选课名单内（请假/查看详情前的越权防御）
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

  // 工具：查询学生档案详情（联表带出账号、院系）
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

  // 工具：批量关闭超时但仍处于 OPEN 状态的会话（每次请求 sessions 接口时顺手清理一次）
  private void closeExpiredSessions() {
    jdbc.update("UPDATE attendance_sessions SET status = 'CLOSED' WHERE status = 'OPEN' AND ends_at < ?", Instant.now().toString());
  }
}
