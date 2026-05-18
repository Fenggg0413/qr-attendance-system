package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.auth.PasswordHasher;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 管理员端 API：仪表板、字典管理（院系/学期/教室/班级）、用户与档案管理（教师/学生）、课程与排课、统计分析
// 全部接口先调 admin() 做角色检查；写操作大多包在 transactions 内保证原子性
@RestController
@RequestMapping("/api/admin")
public class AdminController {
  // 教师/学生重置密码后的默认值
  private static final String DEFAULT_TEACHER_PASSWORD = "123456";
  // 缺勤预警阈值：本学期 ABSENT 次数 > 3 即上榜
  private static final int ABSENCE_WARNING_THRESHOLD = 3;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public AdminController(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  // 管理员仪表板：核心 KPI + 7 日趋势 + 出勤分布 + 课程出勤明细 + 缺勤预警
  // 整体由 5 段独立 SQL 拼起来，前端按 panel 各取所需
  @GetMapping("/dashboard")
  public Map<String, Object> dashboard() {
    admin();
    // ① 每门课程的考勤统计：以课程为主表 LEFT JOIN 全链路，按状态做条件聚合
    //   absent = 选课总人次 - 三种"非缺席"人次（PRESENT/LATE/EXCUSED），保证 absent 兜底也能算出
    List<Map<String, Object>> courseAttendance =
        jdbc.queryForList(
            """
            SELECT co.id course_id,
                   co.name course_name,
                   COUNT(ce.student_id) total,
                   SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END) present,
                   SUM(CASE WHEN ar.status = 'LATE' THEN 1 ELSE 0 END) late,
                   COUNT(ce.student_id) - SUM(CASE WHEN ar.status IN ('PRESENT', 'LATE', 'EXCUSED') THEN 1 ELSE 0 END) absent
            FROM courses co
            LEFT JOIN course_assignments ca ON ca.course_id = co.id
            LEFT JOIN course_enrollments ce ON ce.assignment_id = ca.id
            LEFT JOIN attendance_sessions se ON se.course_id = co.id AND se.teacher_id = ca.teacher_id
            LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = ce.student_id
            GROUP BY co.id, co.name
            ORDER BY co.id
            """);
    // ② 缺勤预警：本学期 ABSENT 次数 > 阈值的学生；HAVING 在 GROUP 后过滤聚合结果
    List<Map<String, Object>> absenceWarnings =
        jdbc.queryForList(
            """
            SELECT s.id student_id,
                   s.name student_name,
                   s.student_no,
                   COUNT(*) absent_count
            FROM attendance_records ar
            JOIN attendance_sessions se ON se.id = ar.session_id
            JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
            JOIN students s ON s.id = ar.student_id
            WHERE ar.status = 'ABSENT'
              AND ca.term = ?
            GROUP BY s.id, s.name, s.student_no
            HAVING COUNT(*) > ?
            ORDER BY absent_count DESC, s.id ASC
            """,
            currentTerm(),
            ABSENCE_WARNING_THRESHOLD);
    String today = LocalDate.now(ZoneId.systemDefault()).toString();
    // ③ 今日出勤分布：按日期前缀过滤（substr 取 ISO 时间戳前 10 位 'YYYY-MM-DD'）
    Map<String, Object> todayCounts =
        firstOrZero(
            """
            SELECT SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END) present,
                   SUM(CASE WHEN ar.status = 'LATE' THEN 1 ELSE 0 END) late,
                   SUM(CASE WHEN ar.status = 'ABSENT' THEN 1 ELSE 0 END) absent
            FROM attendance_records ar
            WHERE substr(COALESCE(ar.checked_in_at, ''), 1, 10) = ?
            """,
            today);
    // ④ 全期出勤分布：用于画饼图
    Map<String, Object> distribution =
        firstOrZero(
            """
            SELECT SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) present,
                   SUM(CASE WHEN status = 'LATE' THEN 1 ELSE 0 END) late,
                   SUM(CASE WHEN status = 'ABSENT' THEN 1 ELSE 0 END) absent
            FROM attendance_records
            """);
    long present = number(distribution.get("present")).longValue();
    long late = number(distribution.get("late")).longValue();
    long absent = number(distribution.get("absent")).longValue();
    long total = present + late + absent;
    Map<String, Object> distributionWithRate = new LinkedHashMap<>(distribution);
    // 出勤率 = (PRESENT + LATE) / total * 100，四舍五入到整数；total = 0 防除零
    distributionWithRate.put("rate", total == 0 ? 0 : Math.round(((present + late) * 100.0) / total));

    return Map.of(
        "kpis",
            Map.of(
                "studentTotal", count("students"),
                "todayPresent", number(todayCounts.get("present")).longValue(),
                "todayAbsent", number(todayCounts.get("absent")).longValue(),
                "todayLate", number(todayCounts.get("late")).longValue(),
                "courseTotal", count("courses"),
                "departmentTotal", count("departments")),
        "trend", sevenDayTrend(),
        "distribution", distributionWithRate,
        "courseAttendance", courseAttendance,
        "absenceWarnings", absenceWarnings);
  }

  // 院系列表（字典）
  @GetMapping("/departments")
  public List<Map<String, Object>> departments() {
    admin();
    return jdbc.queryForList("SELECT id, name FROM departments ORDER BY id");
  }

  // 学期列表（字典，按 sort_order 排序）
  @GetMapping("/terms")
  public List<Map<String, Object>> terms() {
    admin();
    return jdbc.queryForList("SELECT value, label FROM course_terms ORDER BY sort_order, id");
  }

  // 教室列表
  @GetMapping("/classrooms")
  public List<Map<String, Object>> classrooms() {
    admin();
    return jdbc.queryForList("SELECT id, name, building, capacity FROM classrooms ORDER BY id");
  }

  // 创建教室；唯一约束冲突（同名教室）转 409 Conflict
  @PostMapping("/classrooms")
  public Map<String, Object> createClassroom(@RequestBody Map<String, Object> body) {
    admin();
    try {
      long id =
          insert(
              "INSERT INTO classrooms(name, building, capacity) VALUES (?, ?, ?)",
              text(body.get("name")),
              blankToNull(textOrDefault(body.get("building"), "")),
              optionalLong(body.get("capacity")));
      return classroom(id);
    } catch (DataAccessException err) {
      throw conflictIfConstraint(err, "教室已存在");
    }
  }

  // 修改教室
  @PutMapping("/classrooms/{id}")
  public Map<String, Object> updateClassroom(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    try {
      jdbc.update(
          "UPDATE classrooms SET name = ?, building = ?, capacity = ? WHERE id = ?",
          text(body.get("name")),
          blankToNull(textOrDefault(body.get("building"), "")),
          optionalLong(body.get("capacity")),
          id);
      return classroom(id);
    } catch (DataAccessException err) {
      throw conflictIfConstraint(err, "教室已存在");
    }
  }

  // 删除教室；级联清掉相关排课槽（保持事务原子性）
  @DeleteMapping("/classrooms/{id}")
  public void deleteClassroom(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      jdbc.update("DELETE FROM course_schedule_slots WHERE classroom_id = ?", id);
      jdbc.update("DELETE FROM classrooms WHERE id = ?", id);
      return null;
    });
  }

  // 新建院系；唯一约束冲突转 409
  @PostMapping("/departments")
  public Map<String, Object> createDepartment(@RequestBody Map<String, Object> body) {
    admin();
    try {
      long id = insert("INSERT INTO departments(name) VALUES (?)", text(body.get("name")));
      return one("SELECT id, name FROM departments WHERE id = ?", id);
    } catch (DataAccessException err) {
      throw conflictIfConstraint(err, "院系名称已存在");
    }
  }

  // 修改院系名称；空名 400、不存在 404、重名 409
  @PutMapping("/departments/{id}")
  public Map<String, Object> updateDepartment(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    String name = text(body.get("name"));
    if (name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "院系名称不能为空");
    }
    try {
      int updated = jdbc.update("UPDATE departments SET name = ? WHERE id = ?", name, id);
      if (updated == 0) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "院系不存在");
      }
      return one("SELECT id, name FROM departments WHERE id = ?", id);
    } catch (DataAccessException err) {
      throw conflictIfConstraint(err, "院系名称已存在");
    }
  }

  // 删除院系；先查三张依赖表（students/teachers/courses）是否仍有引用，有则拒绝（不级联，避免误删）
  @DeleteMapping("/departments/{id}")
  public void deleteDepartment(@PathVariable long id) {
    admin();
    // 依赖检查：任意一个 count > 0 都不允许删
    Integer studentCount = jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE department_id = ?", Integer.class, id);
    Integer teacherCount = jdbc.queryForObject("SELECT COUNT(*) FROM teachers WHERE department_id = ?", Integer.class, id);
    Integer courseCount = jdbc.queryForObject("SELECT COUNT(*) FROM courses WHERE department_id = ?", Integer.class, id);
    int total = (studentCount != null ? studentCount : 0) + (teacherCount != null ? teacherCount : 0) + (courseCount != null ? courseCount : 0);
    if (total > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "该院系下仍有关联数据（学生、教师或课程），无法删除");
    }
    jdbc.update("DELETE FROM departments WHERE id = ?", id);
  }

  // 教师列表（联表带账号、院系名）
  @GetMapping("/teachers")
  public List<Map<String, Object>> teachers() {
    admin();
    return jdbc.queryForList(
        """
        SELECT t.id,
               t.name,
               t.department_id,
               d.name department_name,
               COALESCE(d.name, t.department) department,
               u.username
        FROM teachers t
        JOIN users u ON u.id = t.user_id
        LEFT JOIN departments d ON d.id = t.department_id
        ORDER BY t.id
        """);
  }

  // 创建教师：事务内先建/复用 users 行（角色 TEACHER），再建 teachers 档案；账号冲突或同账号已绑定 teacher 都会拒绝
  @PostMapping("/teachers")
  public Map<String, Object> createTeacher(@RequestBody Map<String, Object> body) {
    admin();
    long departmentId = departmentId(body);
    return transactions.execute(status -> {
      long userId = userForProfileCreate(text(body.get("username")), textOrDefault(body.get("password"), DEFAULT_TEACHER_PASSWORD), "TEACHER", text(body.get("name")), "teachers");
      try {
        long id = insert("INSERT INTO teachers(user_id, name, department, department_id) VALUES (?, ?, ?, ?)", userId, text(body.get("name")), departmentName(departmentId), departmentId);
        return oneTeacher(id);
      } catch (DataAccessException err) {
        throw conflictIfConstraint(err, "账号已存在");
      }
    });
  }

  // 修改教师档案
  @PutMapping("/teachers/{id}")
  public Map<String, Object> updateTeacher(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    long departmentId = departmentId(body);
    jdbc.update("UPDATE teachers SET name = ?, department = ?, department_id = ? WHERE id = ?", text(body.get("name")), departmentName(departmentId), departmentId, id);
    return oneTeacher(id);
  }

  // 把教师账号密码重置为默认值
  @PostMapping("/teachers/{id}/reset-password")
  public Map<String, Object> resetTeacherPassword(@PathVariable long id) {
    admin();
    long userId =
        jdbc.queryForList("SELECT user_id FROM teachers WHERE id = ?", id).stream()
            .findFirst()
            .map(row -> ((Number) row.get("user_id")).longValue())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "教师不存在"));
    jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", PasswordHasher.hash(DEFAULT_TEACHER_PASSWORD), userId);
    return Map.of("ok", true);
  }

  // 删除教师：按依赖顺序级联清理 备注 → 排课 → 课程分配（含选课） → 考勤会话（含记录/请假） → 教师档案
  // 所有删除在单事务内，任一步失败回滚；外键依赖决定了删除顺序不能调换
  @DeleteMapping("/teachers/{id}")
  public void deleteTeacher(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      // 第一层：教师对学生的备注、排课槽
      jdbc.update("DELETE FROM student_notes WHERE teacher_id = ?", id);
      jdbc.update("DELETE FROM course_schedule_slots WHERE teacher_id = ?", id);

      // 第二层：课程分配 + 该分配下的选课记录
      List<Long> assignments = jdbc.queryForList("SELECT id FROM course_assignments WHERE teacher_id = ?", Long.class, id);
      for (Long assignmentId : assignments) {
        jdbc.update("DELETE FROM course_enrollments WHERE assignment_id = ?", assignmentId);
        jdbc.update("DELETE FROM course_assignments WHERE id = ?", assignmentId);
      }

      // 第三层：考勤会话 + 其下的考勤记录与请假
      List<Long> sessions = jdbc.queryForList("SELECT id FROM attendance_sessions WHERE teacher_id = ?", Long.class, id);
      for (Long sessionId : sessions) {
        jdbc.update("DELETE FROM attendance_records WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM leave_requests WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM attendance_sessions WHERE id = ?", sessionId);
      }

      // 最后删教师档案本身（关联的 users 行保留，可日后复用）
      jdbc.update("DELETE FROM teachers WHERE id = ?", id);
      return null;
    });
  }

  // 学生列表（联表带账号、班级、院系）
  @GetMapping("/students")
  public List<Map<String, Object>> students() {
    admin();
    return jdbc.queryForList(
        """
        SELECT s.id,
               s.name,
               s.student_no,
               s.class_id,
               c.name class_name,
               s.grade,
               s.department_id,
               d.name department_name,
               u.username
        FROM students s
        JOIN users u ON u.id = s.user_id
        LEFT JOIN classes c ON c.id = s.class_id
        LEFT JOIN departments d ON d.id = s.department_id
        ORDER BY s.id
        """);
  }

  // 创建学生：事务内先建/复用 users 行（角色 STUDENT），再建 students 档案；学号冲突 → 409
  @PostMapping("/students")
  public Map<String, Object> createStudent(@RequestBody Map<String, Object> body) {
    admin();
    long departmentId = departmentId(body);
    Long classId = optionalLong(body.get("classId"));
    String grade = blankToNull(textOrDefault(body.get("grade"), ""));
    return transactions.execute(status -> {
      long userId = userForProfileCreate(text(body.get("username")), textOrDefault(body.get("password"), "123456"), "STUDENT", text(body.get("name")), "students");
      try {
        long id = insert("INSERT INTO students(user_id, class_id, department_id, grade, name, student_no) VALUES (?, ?, ?, ?, ?, ?)", userId, classId, departmentId, grade, text(body.get("name")), text(body.get("studentNo")));
        return oneStudent(id);
      } catch (DataAccessException err) {
        throw conflictIfConstraint(err, "学号已存在");
      }
    });
  }

  // 修改学生档案
  @PutMapping("/students/{id}")
  public Map<String, Object> updateStudent(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    jdbc.update(
        "UPDATE students SET class_id = ?, department_id = ?, grade = ?, name = ?, student_no = ? WHERE id = ?",
        optionalLong(body.get("classId")),
        departmentId(body),
        blankToNull(textOrDefault(body.get("grade"), "")),
        text(body.get("name")),
        text(body.get("studentNo")),
        id);
    return oneStudent(id);
  }

  // 删除学生：级联清理 备注/请假/考勤记录/选课 → 学生档案；users 行保留可复用
  @DeleteMapping("/students/{id}")
  public void deleteStudent(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      jdbc.update("DELETE FROM student_notes WHERE student_id = ?", id);
      jdbc.update("DELETE FROM leave_requests WHERE student_id = ?", id);
      jdbc.update("DELETE FROM attendance_records WHERE student_id = ?", id);
      jdbc.update("DELETE FROM course_enrollments WHERE student_id = ?", id);
      jdbc.update("DELETE FROM students WHERE id = ?", id);
      return null;
    });
  }

  // 把学生账号密码重置为默认值 123456
  @PostMapping("/students/{id}/reset-password")
  public Map<String, Object> resetStudentPassword(@PathVariable long id) {
    admin();
    long userId =
        jdbc.queryForList("SELECT user_id FROM students WHERE id = ?", id).stream()
            .findFirst()
            .map(row -> ((Number) row.get("user_id")).longValue())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "学生不存在"));
    jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", PasswordHasher.hash("123456"), userId);
    return Map.of("ok", true);
  }

  // 班级列表
  @GetMapping("/classes")
  public List<Map<String, Object>> classes() {
    admin();
    return jdbc.queryForList("SELECT id, name, grade FROM classes ORDER BY id");
  }

  // 创建班级
  @PostMapping("/classes")
  public Map<String, Object> createClass(@RequestBody Map<String, String> body) {
    admin();
    return Map.of("id", insert("INSERT INTO classes(name, grade) VALUES (?, ?)", body.get("name"), body.get("grade")));
  }

  // 修改班级
  @PutMapping("/classes/{id}")
  public Map<String, Object> updateClass(@PathVariable long id, @RequestBody Map<String, String> body) {
    admin();
    jdbc.update("UPDATE classes SET name = ?, grade = ? WHERE id = ?", body.get("name"), body.get("grade"), id);
    return Map.of("id", id);
  }

  // 删除班级；不级联删学生/课程，仅把 class_id 置空（保留历史数据）
  @DeleteMapping("/classes/{id}")
  public void deleteClass(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      jdbc.update("UPDATE students SET class_id = NULL WHERE class_id = ?", id);
      jdbc.update("UPDATE courses SET class_id = NULL WHERE class_id = ?", id);
      jdbc.update("DELETE FROM classes WHERE id = ?", id);
      return null;
    });
  }

  // 课程列表：复杂多表 LEFT JOIN，把课程、班级、院系、排课、主讲教师、选课人数一次取齐
  @GetMapping("/courses")
  public List<Map<String, Object>> courses() {
    admin();
    return jdbc.queryForList(
        """
        SELECT co.id,
               co.name,
               co.code,
               co.class_id,
               cl.name class_name,
               co.department_id,
               d.name department_name,
               cs.weekday,
               cs.start_time,
               cs.end_time,
               cs.location,
               t.name teacher_name,
               ca.term,
               COALESCE(sc.student_count, 0) student_count
        FROM courses co
        LEFT JOIN classes cl ON cl.id = co.class_id
        LEFT JOIN departments d ON d.id = co.department_id
        LEFT JOIN course_schedules cs ON cs.course_id = co.id
        LEFT JOIN course_assignments ca ON ca.id = (
          SELECT id FROM course_assignments WHERE course_id = co.id ORDER BY id LIMIT 1
        )
        LEFT JOIN teachers t ON t.id = ca.teacher_id
        LEFT JOIN (
          SELECT ca2.course_id, COUNT(DISTINCT ce.student_id) student_count
          FROM course_assignments ca2
          LEFT JOIN course_enrollments ce ON ce.assignment_id = ca2.id
          GROUP BY ca2.course_id
        ) sc ON sc.course_id = co.id
        ORDER BY co.id
        """);
  }

  // 新建课程
  @PostMapping("/courses")
  public Map<String, Object> createCourse(@RequestBody Map<String, Object> body) {
    admin();
    long id = insert("INSERT INTO courses(name, code, class_id, department_id) VALUES (?, ?, ?, ?)", text(body.get("name")), text(body.get("code")), optionalLong(body.get("classId")), departmentId(body));
    return oneCourse(id);
  }

  // 修改课程基本信息
  @PutMapping("/courses/{id}")
  public Map<String, Object> updateCourse(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    jdbc.update("UPDATE courses SET name = ?, code = ?, class_id = ?, department_id = ? WHERE id = ?", text(body.get("name")), text(body.get("code")), optionalLong(body.get("classId")), departmentId(body), id);
    return oneCourse(id);
  }

  // 删除课程：级联清理 排课/排课槽 → 课程分配（含选课） → 考勤会话（含记录/请假） → 课程本身
  @DeleteMapping("/courses/{id}")
  public void deleteCourse(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      // 第一层：排课信息
      jdbc.update("DELETE FROM course_schedules WHERE course_id = ?", id);
      jdbc.update("DELETE FROM course_schedule_slots WHERE course_id = ?", id);

      // 第二层：课程分配 + 选课
      List<Long> assignments = jdbc.queryForList("SELECT id FROM course_assignments WHERE course_id = ?", Long.class, id);
      for (Long assignmentId : assignments) {
        jdbc.update("DELETE FROM course_enrollments WHERE assignment_id = ?", assignmentId);
        jdbc.update("DELETE FROM course_assignments WHERE id = ?", assignmentId);
      }

      // 第三层：考勤会话 + 其下的考勤记录与请假
      List<Long> sessions = jdbc.queryForList("SELECT id FROM attendance_sessions WHERE course_id = ?", Long.class, id);
      for (Long sessionId : sessions) {
        jdbc.update("DELETE FROM attendance_records WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM leave_requests WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM attendance_sessions WHERE id = ?", sessionId);
      }

      jdbc.update("DELETE FROM courses WHERE id = ?", id);
      return null;
    });
  }

  // 课程详情聚合：课程基础 + 排课 + 主讲教师 + 所有授课教师 + 所有排课槽 + 学生名单
  @GetMapping("/courses/{id}")
  public Map<String, Object> courseDetail(@PathVariable long id) {
    admin();
    return Map.of(
        "course", oneCourse(id),
        "schedule", schedule(id),
        "teacher", courseTeacher(id),
        "teachers", courseTeachers(id),
        "scheduleSlots", scheduleSlots(id),
        "students", courseStudents(id));
  }

  // 维护课程的简易排课信息（course_schedules，每课最多一条）；不存在则插入，存在则更新
  @PutMapping("/courses/{id}/schedule")
  public Map<String, Object> updateSchedule(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    oneCourse(id);
    int updated =
        jdbc.update(
            "UPDATE course_schedules SET weekday = ?, start_time = ?, end_time = ?, location = ? WHERE course_id = ?",
            text(body.get("weekday")),
            text(body.get("startTime")),
            text(body.get("endTime")),
            text(body.get("location")),
            id);
    if (updated == 0) {
      insert(
          "INSERT INTO course_schedules(course_id, weekday, start_time, end_time, location) VALUES (?, ?, ?, ?, ?)",
          id,
          text(body.get("weekday")),
          text(body.get("startTime")),
          text(body.get("endTime")),
          text(body.get("location")));
    }
    return schedule(id);
  }

  // 新建或更新排课槽（upsert）；先校验输入与三类时段冲突，再插/改
  // body.id 为空 → 新建；不为空 → 更新（同时按 courseId 限定，防止跨课程改）
  @PutMapping("/courses/{id}/schedule-slots")
  public Map<String, Object> upsertScheduleSlot(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    oneCourse(id);
    Long slotId = optionalLong(body.get("id"));
    long teacherId = number(body.get("teacherId")).longValue();
    long classroomId = number(body.get("classroomId")).longValue();
    String weekday = text(body.get("weekday"));
    long period = number(body.get("period")).longValue();
    String courseType = textOrDefault(body.get("courseType"), "LECTURE");

    // 节次必须 1-9，星期不能空
    if (weekday.isBlank() || period < 1 || period > 9) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效节次");
    }
    // 课程类型白名单（与 demo 数据生成一致）
    if (!"LECTURE".equals(courseType) && !"LAB".equals(courseType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "课程类型无效");
    }
    // 教师/教室存在性
    oneTeacher(teacherId);
    classroom(classroomId);
    // 关键：三类冲突检查（教师、教室、课程）必须在 INSERT/UPDATE 之前
    ensureSlotAvailable(id, slotId, weekday, period, teacherId, classroomId);

    if (slotId == null) {
      // 新建：course_schedule_slots 表唯一索引 (course_id, weekday, period) 兜底
      long createdId =
          insert(
              "INSERT INTO course_schedule_slots(course_id, teacher_id, classroom_id, weekday, period, course_type) VALUES (?, ?, ?, ?, ?, ?)",
              id,
              teacherId,
              classroomId,
              weekday,
              period,
              courseType);
      return scheduleSlot(createdId);
    }

    // 更新：附加 course_id 条件防止越权修改他人课程的槽
    int updated =
        jdbc.update(
            "UPDATE course_schedule_slots SET teacher_id = ?, classroom_id = ?, weekday = ?, period = ?, course_type = ? WHERE id = ? AND course_id = ?",
            teacherId,
            classroomId,
            weekday,
            period,
            courseType,
            slotId,
            id);
    if (updated == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排课不存在");
    }
    return scheduleSlot(slotId);
  }

  // 删除排课槽：先把引用该槽的考勤会话 schedule_slot_id 置空（保留考勤历史），再删槽
  @DeleteMapping("/courses/{courseId}/schedule-slots/{slotId}")
  public void deleteScheduleSlot(@PathVariable long courseId, @PathVariable long slotId) {
    admin();
    jdbc.update(
        "UPDATE attendance_sessions SET schedule_slot_id = NULL WHERE schedule_slot_id IN (SELECT id FROM course_schedule_slots WHERE id = ? AND course_id = ?)",
        slotId,
        courseId);
    jdbc.update("DELETE FROM course_schedule_slots WHERE id = ? AND course_id = ?", slotId, courseId);
  }

  // 设置课程主讲教师；已有 course_assignments 行则更新，否则插入
  @PutMapping("/courses/{id}/teacher")
  public Map<String, Object> updateCourseTeacher(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    oneCourse(id);
    long teacherId = number(body.get("teacherId")).longValue();
    List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM course_assignments WHERE course_id = ? ORDER BY id LIMIT 1", id);
    if (existing.isEmpty()) {
      insert("INSERT INTO course_assignments(course_id, teacher_id, term) VALUES (?, ?, ?)", id, teacherId, blankToNull(textOrDefault(body.get("term"), "")));
    } else {
      jdbc.update(
          "UPDATE course_assignments SET teacher_id = ?, term = ? WHERE id = ?",
          teacherId,
          blankToNull(textOrDefault(body.get("term"), "")),
          ((Number) existing.getFirst().get("id")).longValue());
    }
    return courseTeacher(id);
  }

  // 把学生加入课程选课名单；先查重避免重复插入
  @PostMapping("/courses/{id}/students")
  public Map<String, Object> addCourseStudent(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    long assignmentId = assignmentIdForCourse(id);
    long studentId = number(body.get("studentId")).longValue();
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_enrollments WHERE assignment_id = ? AND student_id = ?",
            Integer.class,
            assignmentId,
            studentId);
    if (exists != null && exists > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "该学生已在选课名单中");
    }
    long enrollmentId = insert("INSERT INTO course_enrollments(assignment_id, student_id) VALUES (?, ?)", assignmentId, studentId);
    return enrollment(enrollmentId);
  }

  // 从课程选课名单中移除学生
  @DeleteMapping("/courses/{courseId}/students/{studentId}")
  public void removeCourseStudent(@PathVariable long courseId, @PathVariable long studentId) {
    admin();
    long assignmentId = assignmentIdForCourse(courseId);
    jdbc.update("DELETE FROM course_enrollments WHERE assignment_id = ? AND student_id = ?", assignmentId, studentId);
  }

  // 所有课程-教师分配列表（管理多教师授课场景用）
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

  // 新建课程-教师分配
  @PostMapping("/course-assignments")
  public Map<String, Object> createAssignment(@RequestBody Map<String, Object> body) {
    admin();
    long id =
        insert(
            "INSERT INTO course_assignments(course_id, teacher_id, term) VALUES (?, ?, ?)",
            number(body.get("courseId")).longValue(),
            number(body.get("teacherId")).longValue(),
            blankToNull(textOrDefault(body.get("term"), "")));
    return assignment(id);
  }

  // 修改课程-教师分配
  @PutMapping("/course-assignments/{id}")
  public Map<String, Object> updateAssignment(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    jdbc.update(
        "UPDATE course_assignments SET course_id = ?, teacher_id = ?, term = ? WHERE id = ?",
        number(body.get("courseId")).longValue(),
        number(body.get("teacherId")).longValue(),
        blankToNull(textOrDefault(body.get("term"), "")),
        id);
    return assignment(id);
  }

  // 删除课程分配；级联清掉对应选课记录
  @DeleteMapping("/course-assignments/{id}")
  public void deleteAssignment(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      jdbc.update("DELETE FROM course_enrollments WHERE assignment_id = ?", id);
      jdbc.update("DELETE FROM course_assignments WHERE id = ?", id);
      return null;
    });
  }

  // 全量选课记录
  @GetMapping("/enrollments")
  public List<Map<String, Object>> enrollments() {
    admin();
    return enrollmentRows(null);
  }

  // 新建选课记录；唯一约束 (assignment_id, student_id) 由前置 COUNT 查重
  @PostMapping("/enrollments")
  public Map<String, Object> createEnrollment(@RequestBody Map<String, Object> body) {
    admin();
    long assignmentId = number(body.get("assignmentId")).longValue();
    long studentId = number(body.get("studentId")).longValue();
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

  // 删除选课记录
  @DeleteMapping("/enrollments/{id}")
  public void deleteEnrollment(@PathVariable long id) {
    admin();
    jdbc.update("DELETE FROM course_enrollments WHERE id = ?", id);
  }

  // 全量考勤记录（按 id 倒序），管理端用于审计
  @GetMapping("/attendance-records")
  public List<Map<String, Object>> attendanceRecords() {
    admin();
    return jdbc.queryForList("SELECT ar.id, ar.session_id, co.name course_name, s.name student_name, ar.status, ar.checked_in_at, ar.source FROM attendance_records ar JOIN students s ON s.id = ar.student_id JOIN attendance_sessions se ON se.id = ar.session_id JOIN courses co ON co.id = se.course_id ORDER BY ar.id DESC");
  }

  // 按课程 × 状态聚合的考勤计数（饼图/热力数据源）
  @GetMapping("/statistics")
  public List<Map<String, Object>> statistics() {
    admin();
    return jdbc.queryForList("SELECT co.name course_name, ar.status, COUNT(*) count FROM attendance_records ar JOIN attendance_sessions se ON se.id = ar.session_id JOIN courses co ON co.id = se.course_id GROUP BY co.name, ar.status ORDER BY co.name, ar.status");
  }

  // 按考勤会话聚合的完整统计：每场会话的出勤/请假/缺勤人数
  // absent 公式：选课总人次 - 三种已记录人次（PRESENT/LATE/EXCUSED），LEFT JOIN 没记录的算缺勤
  @GetMapping("/attendance-stats")
  public List<Map<String, Object>> attendanceStats() {
    admin();
    return jdbc.queryForList(
        """
        SELECT se.id session_id,
               co.name course_name,
               t.name teacher_name,
               d.name department_name,
               COUNT(s.id) total,
               SUM(CASE WHEN ar.status IN ('PRESENT', 'LATE') THEN 1 ELSE 0 END) present,
               SUM(CASE WHEN ar.status = 'EXCUSED' THEN 1 ELSE 0 END) excused,
               COUNT(s.id) - SUM(CASE WHEN ar.status IN ('PRESENT', 'LATE', 'EXCUSED') THEN 1 ELSE 0 END) absent
        FROM attendance_sessions se
        JOIN courses co ON co.id = se.course_id
        LEFT JOIN departments d ON d.id = co.department_id
        JOIN teachers t ON t.id = se.teacher_id
        JOIN course_assignments ca ON ca.course_id = se.course_id AND ca.teacher_id = se.teacher_id
        JOIN course_enrollments ce ON ce.assignment_id = ca.id
        JOIN students s ON s.id = ce.student_id
        LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = s.id
        GROUP BY se.id, co.name, t.name, d.name
        ORDER BY se.started_at DESC, se.id DESC
        """);
  }

  // 管理员视角：全平台请假申请列表
  @GetMapping("/leave-requests")
  public List<Map<String, Object>> leaveRequests() {
    admin();
    return jdbc.queryForList("SELECT lr.id, lr.session_id, s.name student_name, lr.reason, lr.status, lr.created_at, lr.reviewed_at FROM leave_requests lr JOIN students s ON s.id = lr.student_id ORDER BY lr.id DESC");
  }

  // 管理员审核请假；批准则自动写入或更新对应考勤记录为 EXCUSED
  // 与 TeacherController.reviewLeaveRequest 类似，但不做角色权限范围限制（管理员可审任意一条）
  @PostMapping("/leave-requests/{id}/review")
  public Map<String, Object> reviewLeave(@PathVariable long id, @RequestBody Map<String, String> body) {
    var admin = admin();
    String status = Boolean.parseBoolean(body.getOrDefault("approved", "false")) ? "APPROVED" : "REJECTED";
    jdbc.update("UPDATE leave_requests SET status = ?, reviewer_id = ?, reviewed_at = ? WHERE id = ?", status, admin.id(), Instant.now().toString(), id);
    if ("APPROVED".equals(status)) {
      // 批准 → 通过 upsertRecord 同步出勤记录为 EXCUSED；记录存在则 UPDATE，否则 INSERT
      Map<String, Object> leave = one("SELECT session_id, student_id FROM leave_requests WHERE id = ?", id);
      upsertRecord(((Number) leave.get("session_id")).longValue(), ((Number) leave.get("student_id")).longValue(), "EXCUSED", "LEAVE");
    }
    return Map.of("id", id, "status", status);
  }

  // 权限 helper：要求当前请求是 ADMIN，否则 403；所有接口都通过它做角色检查
  private com.example.qrattendance.auth.CurrentUser admin() {
    return AuthContext.requireRole("ADMIN");
  }

  // helper：建一个全新的 users 行（密码经 SHA-256）
  private long user(String username, String password, String role, String displayName) {
    return insert("INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)", username, PasswordHasher.hash(password), role, displayName);
  }

  // helper：为新档案准备 users 行 —— 不存在则建，已存在则复用
  // 关键规则：账号可跨角色复用前提是同角色（不允许同一 username 被 TEACHER/STUDENT 同时占用）
  // 同角色已存在档案（teachers/students 表里有 user_id）→ 视为账号已被占用
  private long userForProfileCreate(String username, String password, String role, String displayName, String profileTable) {
    List<Map<String, Object>> users = jdbc.queryForList("SELECT id, role FROM users WHERE username = ?", username);
    if (users.isEmpty()) {
      // 全新账号，直接建
      return user(username, password, role, displayName);
    }
    Map<String, Object> existing = users.getFirst();
    long userId = ((Number) existing.get("id")).longValue();
    // 角色冲突：账号被其他角色占用 → 409
    if (!role.equals(existing.get("role"))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已被其他角色使用");
    }
    // 同角色已有档案 → 409（避免一个账号绑两份档案）
    Integer profiles = jdbc.queryForObject("SELECT COUNT(*) FROM " + profileTable + " WHERE user_id = ?", Integer.class, userId);
    if (profiles != null && profiles > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
    }
    // 角色匹配但无档案 → 复用 users 行，并刷新密码与显示名
    jdbc.update("UPDATE users SET password_hash = ?, display_name = ? WHERE id = ?", PasswordHasher.hash(password), displayName, userId);
    return userId;
  }

  // helper：判断异常是否为唯一约束冲突；是则转 409，否则原样抛出
  // SQLite 异常文本不固定，匹配三种常见前缀做兜底
  private ResponseStatusException conflictIfConstraint(DataAccessException err, String message) {
    String detail = String.valueOf(err.getMostSpecificCause().getMessage());
    if (detail.contains("SQLITE_CONSTRAINT") || detail.contains("constraint failed") || detail.contains("UNIQUE constraint failed")) {
      return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    throw err;
  }

  // helper：执行 INSERT 并通过 last_insert_rowid() 取自增主键
  private long insert(String sql, Object... args) {
    jdbc.update(sql, args);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  // helper：查询单条记录，不存在抛 404
  private Map<String, Object> one(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  // helper：取课程分配详情（含课程名、教师名、学期）
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

  // helper：查询选课记录（id 为空时查全量，否则按 id 过滤）；同一个 SQL 服务列表与详情两种查询
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

  // helper：取单条选课记录
  private Map<String, Object> enrollment(long id) {
    return enrollmentRows(id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  // helper：取教师详情
  private Map<String, Object> oneTeacher(long id) {
    return one(
        """
        SELECT t.id,
               t.name,
               t.department_id,
               d.name department_name,
               COALESCE(d.name, t.department) department,
               u.username
        FROM teachers t
        JOIN users u ON u.id = t.user_id
        LEFT JOIN departments d ON d.id = t.department_id
        WHERE t.id = ?
        """,
        id);
  }

  // helper：取学生详情
  private Map<String, Object> oneStudent(long id) {
    return one(
        """
        SELECT s.id,
               s.name,
               s.student_no,
               s.class_id,
               c.name class_name,
               s.grade,
               s.department_id,
               d.name department_name,
               u.username
        FROM students s
        JOIN users u ON u.id = s.user_id
        LEFT JOIN classes c ON c.id = s.class_id
        LEFT JOIN departments d ON d.id = s.department_id
        WHERE s.id = ?
        """,
        id);
  }

  // helper：取课程详情
  private Map<String, Object> oneCourse(long id) {
    return one(
        """
        SELECT co.id,
               co.name,
               co.code,
               co.class_id,
               cl.name class_name,
               co.department_id,
               d.name department_name
        FROM courses co
        LEFT JOIN classes cl ON cl.id = co.class_id
        LEFT JOIN departments d ON d.id = co.department_id
        WHERE co.id = ?
        """,
        id);
  }

  private Map<String, Object> schedule(long courseId) {
    return jdbc.queryForList(
            "SELECT course_id, weekday, start_time, end_time, location FROM course_schedules WHERE course_id = ?",
            courseId)
        .stream()
        .findFirst()
        .orElse(Map.of("course_id", courseId, "weekday", "", "start_time", "", "end_time", "", "location", ""));
  }

  // helper：课程主讲教师（取第一条分配）
  private Map<String, Object> courseTeacher(long courseId) {
    return courseTeachers(courseId).stream().findFirst().orElse(Map.of());
  }

  // helper：课程所有授课教师（多教师场景）
  private List<Map<String, Object>> courseTeachers(long courseId) {
    return jdbc.queryForList(
        """
        SELECT t.id,
               t.name,
               ca.id assignment_id,
               ca.teacher_id,
               ca.term,
               d.name department_name,
               u.username
        FROM course_assignments ca
        JOIN teachers t ON t.id = ca.teacher_id
        JOIN users u ON u.id = t.user_id
        LEFT JOIN departments d ON d.id = t.department_id
        WHERE ca.course_id = ?
        ORDER BY ca.id
        """,
        courseId);
  }

  // helper：课程的所有排课槽（按星期+节次排序，CASE 把中文星期映射为数字）
  private List<Map<String, Object>> scheduleSlots(long courseId) {
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
        WHERE css.course_id = ?
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
        courseId);
  }

  // helper：单个排课槽详情（带教师姓名、教室名）
  private Map<String, Object> scheduleSlot(long slotId) {
    return one(
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
        WHERE css.id = ?
        """,
        slotId);
  }

  // helper：取教室详情
  private Map<String, Object> classroom(long id) {
    return one("SELECT id, name, building, capacity FROM classrooms WHERE id = ?", id);
  }

  // helper：校验排课槽是否可插入/更新；检查三类冲突 —— 教师、教室、课程
  // 更新场景下要排除当前槽自身（slotId != null 时附加 "AND id <> ?"），否则会误判为冲突
  private void ensureSlotAvailable(long courseId, Long slotId, String weekday, long period, long teacherId, long classroomId) {
    // slotFilter 用于更新场景排除自身；新建场景为空字符串
    String slotFilter = slotId == null ? "" : " AND id <> ?";

    // ① 教师冲突：同教师在同 weekday+period 已有排课
    List<Object> teacherArgs = new ArrayList<>(List.of(weekday, period, teacherId));
    if (slotId != null) teacherArgs.add(slotId);
    Integer teacherConflicts =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_schedule_slots WHERE weekday = ? AND period = ? AND teacher_id = ?" + slotFilter,
            Integer.class,
            teacherArgs.toArray());
    if (teacherConflicts != null && teacherConflicts > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "该教师此节次已有排课");
    }

    // ② 教室冲突：同教室在同 weekday+period 已被占
    List<Object> classroomArgs = new ArrayList<>(List.of(weekday, period, classroomId));
    if (slotId != null) classroomArgs.add(slotId);
    Integer classroomConflicts =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_schedule_slots WHERE weekday = ? AND period = ? AND classroom_id = ?" + slotFilter,
            Integer.class,
            classroomArgs.toArray());
    if (classroomConflicts != null && classroomConflicts > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "该教室此节次已被占用");
    }

    // ③ 课程冲突：同课程在同 weekday+period 已有槽（防止重复排）
    List<Object> courseArgs = new ArrayList<>(List.of(courseId, weekday, period));
    if (slotId != null) courseArgs.add(slotId);
    Integer courseConflicts =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_schedule_slots WHERE course_id = ? AND weekday = ? AND period = ?" + slotFilter,
            Integer.class,
            courseArgs.toArray());
    if (courseConflicts != null && courseConflicts > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "该课程此节次已有排课");
    }
  }

  // helper：课程的选课学生名单（含院系）
  private List<Map<String, Object>> courseStudents(long courseId) {
    return jdbc.queryForList(
        """
        SELECT ce.id enrollment_id,
               s.id,
               s.name,
               s.student_no,
               s.department_id,
               d.name department_name
        FROM course_assignments ca
        JOIN course_enrollments ce ON ce.assignment_id = ca.id
        JOIN students s ON s.id = ce.student_id
        LEFT JOIN departments d ON d.id = s.department_id
        WHERE ca.course_id = ?
        ORDER BY s.student_no, s.name
        """,
        courseId);
  }

  // helper：取课程主分配 ID（用于选课操作）；未分配教师抛 400 提示先分配
  private long assignmentIdForCourse(long courseId) {
    return jdbc.queryForList("SELECT id FROM course_assignments WHERE course_id = ? ORDER BY id LIMIT 1", courseId).stream()
        .findFirst()
        .map(row -> ((Number) row.get("id")).longValue())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先分配授课教师"));
  }

  // helper：从请求体解析院系 id；支持三种入参形式：departmentId → department(名字) → 兜底取第一个院系
  private long departmentId(Map<String, Object> body) {
    Long id = optionalLong(body.get("departmentId"));
    if (id != null) return id;
    String name = textOrDefault(body.get("department"), "");
    // 按名字找院系，找不到则新建
    if (!name.isBlank()) return ensureDepartment(name);
    // 既没 id 也没名字 → 使用首个院系作为兜底
    return jdbc.queryForObject("SELECT id FROM departments ORDER BY id LIMIT 1", Long.class);
  }

  // helper：按名字找院系，不存在则插入；返回 id
  private long ensureDepartment(String name) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM departments WHERE name = ?", name);
    if (!rows.isEmpty()) return ((Number) rows.getFirst().get("id")).longValue();
    return insert("INSERT INTO departments(name) VALUES (?)", name);
  }

  // helper：按 id 取院系名
  private String departmentName(long id) {
    return String.valueOf(one("SELECT name FROM departments WHERE id = ?", id).get("name"));
  }

  // helper：宽松转换为 Number，兼容 JSON 数字/字符串/空值
  private Number number(Object value) {
    if (value instanceof Number numeric) return numeric;
    if (value == null || String.valueOf(value).isBlank()) return 0;
    return Long.parseLong(String.valueOf(value));
  }

  // helper：可选 long，null 或空串返 null，否则解析为 long
  private Long optionalLong(Object value) {
    if (value == null || String.valueOf(value).isBlank()) return null;
    return number(value).longValue();
  }

  // helper：对象转字符串，null 返空串
  private String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  // helper：对象转字符串，空时返回 fallback
  private String textOrDefault(Object value, String fallback) {
    String text = text(value);
    return text.isBlank() ? fallback : text;
  }

  // helper：统计表行数
  private long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  // helper：查询首行，没有数据返回空 Map（用于 dashboard 的 SUM 聚合查询，避免 null 透传）
  private Map<String, Object> firstOrZero(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElse(Map.of());
  }

  // helper：根据当前月份判断本学期标签 —— 8 月及之后为秋季学期，否则为前一年的春季学期
  private String currentTerm() {
    LocalDate today = LocalDate.now(ZoneId.systemDefault());
    int year = today.getYear();
    if (today.getMonthValue() >= 8) {
      return year + "-" + (year + 1) + "学年 秋季学期";
    }
    return (year - 1) + "-" + year + "学年 春季学期";
  }

  // helper：生成最近 7 天的考勤趋势数据；保证 7 天都有记录（即使当天 0 考勤也填 0）
  // 关键：先预生成 7 天的 0 值占位 Map，再用查询结果回填 —— 避免前端图表出现日期缺口
  private List<Map<String, Object>> sevenDayTrend() {
    LocalDate today = LocalDate.now(ZoneId.systemDefault());
    // 查询过去 7 天（含今天）的考勤聚合；用 COALESCE(checked_in_at, started_at) 兜底 ABSENT 无打卡时间的情况
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT substr(COALESCE(ar.checked_in_at, se.started_at), 1, 10) day,
                   SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END) present,
                   SUM(CASE WHEN ar.status = 'LATE' THEN 1 ELSE 0 END) late,
                   SUM(CASE WHEN ar.status = 'ABSENT' THEN 1 ELSE 0 END) absent
            FROM attendance_records ar
            JOIN attendance_sessions se ON se.id = ar.session_id
            WHERE substr(COALESCE(ar.checked_in_at, se.started_at), 1, 10) >= ?
            GROUP BY day
            """,
            today.minusDays(6).toString());
    // 预生成 7 天的空 Map（含今天，今天-1，... 今天-6），保证返回数组连续
    Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
    for (int index = 6; index >= 0; index--) {
      String day = today.minusDays(index).toString();
      byDay.put(day, new LinkedHashMap<>(Map.of("date", day, "present", 0L, "absent", 0L, "late", 0L)));
    }
    // 把查询出来的实际数据回填到对应日期；查询里没有的日期保持 0 值
    for (Map<String, Object> row : rows) {
      Map<String, Object> target = byDay.get(String.valueOf(row.get("day")));
      if (target != null) {
        target.put("present", number(row.get("present")).longValue());
        target.put("absent", number(row.get("absent")).longValue());
        target.put("late", number(row.get("late")).longValue());
      }
    }
    return new ArrayList<>(byDay.values());
  }

  // helper：空串或 null 转 null
  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  // helper：考勤记录 upsert —— 存在 (session, student) 行则 UPDATE，否则 INSERT
  // 用于"批准请假"等需要保证记录存在并同步状态的场景
  private void upsertRecord(long sessionId, long studentId, String status, String source) {
    int updated = jdbc.update("UPDATE attendance_records SET status = ?, source = ? WHERE session_id = ? AND student_id = ?", status, source, sessionId, studentId);
    if (updated == 0) {
      jdbc.update("INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)", sessionId, studentId, status, Instant.now().toString(), source);
    }
  }
}
