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

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private static final String DEFAULT_TEACHER_PASSWORD = "123456";

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public AdminController(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard() {
    admin();
    List<Map<String, Object>> courseAttendance =
        jdbc.queryForList(
            """
            SELECT co.id course_id,
                   co.name course_name,
                   COUNT(DISTINCT ce.student_id) total,
                   SUM(CASE WHEN ar.status = 'PRESENT' THEN 1 ELSE 0 END) present,
                   SUM(CASE WHEN ar.status = 'LATE' THEN 1 ELSE 0 END) late,
                   COUNT(DISTINCT ce.student_id) - SUM(CASE WHEN ar.status IN ('PRESENT', 'LATE', 'EXCUSED') THEN 1 ELSE 0 END) absent
            FROM courses co
            LEFT JOIN course_assignments ca ON ca.course_id = co.id
            LEFT JOIN course_enrollments ce ON ce.assignment_id = ca.id
            LEFT JOIN attendance_sessions se ON se.course_id = co.id AND se.teacher_id = ca.teacher_id
            LEFT JOIN attendance_records ar ON ar.session_id = se.id AND ar.student_id = ce.student_id
            GROUP BY co.id, co.name
            ORDER BY co.id
            """);
    List<Map<String, Object>> activities =
        jdbc.queryForList(
            """
            SELECT ar.id,
                   ar.session_id,
                   co.name course_name,
                   s.name student_name,
                   s.student_no,
                   ar.status,
                   COALESCE(ar.checked_in_at, se.started_at) checked_in_at
            FROM attendance_records ar
            JOIN students s ON s.id = ar.student_id
            JOIN attendance_sessions se ON se.id = ar.session_id
            JOIN courses co ON co.id = se.course_id
            ORDER BY COALESCE(ar.checked_in_at, se.started_at) DESC, ar.id DESC
            LIMIT 8
            """);
    String today = LocalDate.now(ZoneId.systemDefault()).toString();
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
        "recentActivities", activities);
  }

  @GetMapping("/departments")
  public List<Map<String, Object>> departments() {
    admin();
    return jdbc.queryForList("SELECT id, name FROM departments ORDER BY id");
  }

  @GetMapping("/terms")
  public List<Map<String, Object>> terms() {
    admin();
    return jdbc.queryForList("SELECT value, label FROM course_terms ORDER BY sort_order, id");
  }

  @GetMapping("/classrooms")
  public List<Map<String, Object>> classrooms() {
    admin();
    return jdbc.queryForList("SELECT id, name, building, capacity FROM classrooms ORDER BY id");
  }

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

  @DeleteMapping("/classrooms/{id}")
  public void deleteClassroom(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      jdbc.update("DELETE FROM course_schedule_slots WHERE classroom_id = ?", id);
      jdbc.update("DELETE FROM classrooms WHERE id = ?", id);
      return null;
    });
  }

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

  @DeleteMapping("/departments/{id}")
  public void deleteDepartment(@PathVariable long id) {
    admin();
    // Check if department is referenced by students, teachers, or courses
    Integer studentCount = jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE department_id = ?", Integer.class, id);
    Integer teacherCount = jdbc.queryForObject("SELECT COUNT(*) FROM teachers WHERE department_id = ?", Integer.class, id);
    Integer courseCount = jdbc.queryForObject("SELECT COUNT(*) FROM courses WHERE department_id = ?", Integer.class, id);
    int total = (studentCount != null ? studentCount : 0) + (teacherCount != null ? teacherCount : 0) + (courseCount != null ? courseCount : 0);
    if (total > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "该院系下仍有关联数据（学生、教师或课程），无法删除");
    }
    jdbc.update("DELETE FROM departments WHERE id = ?", id);
  }

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

  @PutMapping("/teachers/{id}")
  public Map<String, Object> updateTeacher(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    long departmentId = departmentId(body);
    jdbc.update("UPDATE teachers SET name = ?, department = ?, department_id = ? WHERE id = ?", text(body.get("name")), departmentName(departmentId), departmentId, id);
    return oneTeacher(id);
  }

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

  @DeleteMapping("/teachers/{id}")
  public void deleteTeacher(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      jdbc.update("DELETE FROM student_notes WHERE teacher_id = ?", id);
      jdbc.update("DELETE FROM course_schedule_slots WHERE teacher_id = ?", id);
      
      List<Long> assignments = jdbc.queryForList("SELECT id FROM course_assignments WHERE teacher_id = ?", Long.class, id);
      for (Long assignmentId : assignments) {
        jdbc.update("DELETE FROM course_enrollments WHERE assignment_id = ?", assignmentId);
        jdbc.update("DELETE FROM course_assignments WHERE id = ?", assignmentId);
      }
      
      List<Long> sessions = jdbc.queryForList("SELECT id FROM attendance_sessions WHERE teacher_id = ?", Long.class, id);
      for (Long sessionId : sessions) {
        jdbc.update("DELETE FROM attendance_records WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM leave_requests WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM attendance_sessions WHERE id = ?", sessionId);
      }
      
      jdbc.update("DELETE FROM teachers WHERE id = ?", id);
      return null;
    });
  }

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
    transactions.execute(status -> {
      jdbc.update("UPDATE students SET class_id = NULL WHERE class_id = ?", id);
      jdbc.update("UPDATE courses SET class_id = NULL WHERE class_id = ?", id);
      jdbc.update("DELETE FROM classes WHERE id = ?", id);
      return null;
    });
  }

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

  @PostMapping("/courses")
  public Map<String, Object> createCourse(@RequestBody Map<String, Object> body) {
    admin();
    long id = insert("INSERT INTO courses(name, code, class_id, department_id) VALUES (?, ?, ?, ?)", text(body.get("name")), text(body.get("code")), optionalLong(body.get("classId")), departmentId(body));
    return oneCourse(id);
  }

  @PutMapping("/courses/{id}")
  public Map<String, Object> updateCourse(@PathVariable long id, @RequestBody Map<String, Object> body) {
    admin();
    jdbc.update("UPDATE courses SET name = ?, code = ?, class_id = ?, department_id = ? WHERE id = ?", text(body.get("name")), text(body.get("code")), optionalLong(body.get("classId")), departmentId(body), id);
    return oneCourse(id);
  }

  @DeleteMapping("/courses/{id}")
  public void deleteCourse(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      jdbc.update("DELETE FROM course_schedules WHERE course_id = ?", id);
      jdbc.update("DELETE FROM course_schedule_slots WHERE course_id = ?", id);
      
      List<Long> assignments = jdbc.queryForList("SELECT id FROM course_assignments WHERE course_id = ?", Long.class, id);
      for (Long assignmentId : assignments) {
        jdbc.update("DELETE FROM course_enrollments WHERE assignment_id = ?", assignmentId);
        jdbc.update("DELETE FROM course_assignments WHERE id = ?", assignmentId);
      }
      
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

    if (weekday.isBlank() || period < 1 || period > 9) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效节次");
    }
    if (!"LECTURE".equals(courseType) && !"LAB".equals(courseType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "课程类型无效");
    }
    oneTeacher(teacherId);
    classroom(classroomId);
    ensureSlotAvailable(id, slotId, weekday, period, teacherId, classroomId);

    if (slotId == null) {
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

  @DeleteMapping("/courses/{courseId}/schedule-slots/{slotId}")
  public void deleteScheduleSlot(@PathVariable long courseId, @PathVariable long slotId) {
    admin();
    jdbc.update("DELETE FROM course_schedule_slots WHERE id = ? AND course_id = ?", slotId, courseId);
  }

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

  @DeleteMapping("/courses/{courseId}/students/{studentId}")
  public void removeCourseStudent(@PathVariable long courseId, @PathVariable long studentId) {
    admin();
    long assignmentId = assignmentIdForCourse(courseId);
    jdbc.update("DELETE FROM course_enrollments WHERE assignment_id = ? AND student_id = ?", assignmentId, studentId);
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

  @DeleteMapping("/course-assignments/{id}")
  public void deleteAssignment(@PathVariable long id) {
    admin();
    transactions.execute(status -> {
      jdbc.update("DELETE FROM course_enrollments WHERE assignment_id = ?", id);
      jdbc.update("DELETE FROM course_assignments WHERE id = ?", id);
      return null;
    });
  }

  @GetMapping("/enrollments")
  public List<Map<String, Object>> enrollments() {
    admin();
    return enrollmentRows(null);
  }

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

  private long userForProfileCreate(String username, String password, String role, String displayName, String profileTable) {
    List<Map<String, Object>> users = jdbc.queryForList("SELECT id, role FROM users WHERE username = ?", username);
    if (users.isEmpty()) {
      return user(username, password, role, displayName);
    }
    Map<String, Object> existing = users.getFirst();
    long userId = ((Number) existing.get("id")).longValue();
    if (!role.equals(existing.get("role"))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已被其他角色使用");
    }
    Integer profiles = jdbc.queryForObject("SELECT COUNT(*) FROM " + profileTable + " WHERE user_id = ?", Integer.class, userId);
    if (profiles != null && profiles > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
    }
    jdbc.update("UPDATE users SET password_hash = ?, display_name = ? WHERE id = ?", PasswordHasher.hash(password), displayName, userId);
    return userId;
  }

  private ResponseStatusException conflictIfConstraint(DataAccessException err, String message) {
    String detail = String.valueOf(err.getMostSpecificCause().getMessage());
    if (detail.contains("SQLITE_CONSTRAINT") || detail.contains("constraint failed") || detail.contains("UNIQUE constraint failed")) {
      return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    throw err;
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

  private Map<String, Object> courseTeacher(long courseId) {
    return courseTeachers(courseId).stream().findFirst().orElse(Map.of());
  }

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

  private Map<String, Object> classroom(long id) {
    return one("SELECT id, name, building, capacity FROM classrooms WHERE id = ?", id);
  }

  private void ensureSlotAvailable(long courseId, Long slotId, String weekday, long period, long teacherId, long classroomId) {
    String slotFilter = slotId == null ? "" : " AND id <> ?";
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

  private long assignmentIdForCourse(long courseId) {
    return jdbc.queryForList("SELECT id FROM course_assignments WHERE course_id = ? ORDER BY id LIMIT 1", courseId).stream()
        .findFirst()
        .map(row -> ((Number) row.get("id")).longValue())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先分配授课教师"));
  }

  private long departmentId(Map<String, Object> body) {
    Long id = optionalLong(body.get("departmentId"));
    if (id != null) return id;
    String name = textOrDefault(body.get("department"), "");
    if (!name.isBlank()) return ensureDepartment(name);
    return jdbc.queryForObject("SELECT id FROM departments ORDER BY id LIMIT 1", Long.class);
  }

  private long ensureDepartment(String name) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM departments WHERE name = ?", name);
    if (!rows.isEmpty()) return ((Number) rows.getFirst().get("id")).longValue();
    return insert("INSERT INTO departments(name) VALUES (?)", name);
  }

  private String departmentName(long id) {
    return String.valueOf(one("SELECT name FROM departments WHERE id = ?", id).get("name"));
  }

  private Number number(Object value) {
    if (value instanceof Number numeric) return numeric;
    if (value == null || String.valueOf(value).isBlank()) return 0;
    return Long.parseLong(String.valueOf(value));
  }

  private Long optionalLong(Object value) {
    if (value == null || String.valueOf(value).isBlank()) return null;
    return number(value).longValue();
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String textOrDefault(Object value, String fallback) {
    String text = text(value);
    return text.isBlank() ? fallback : text;
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  private Map<String, Object> firstOrZero(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElse(Map.of());
  }

  private List<Map<String, Object>> sevenDayTrend() {
    LocalDate today = LocalDate.now(ZoneId.systemDefault());
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
    Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
    for (int index = 6; index >= 0; index--) {
      String day = today.minusDays(index).toString();
      byDay.put(day, new LinkedHashMap<>(Map.of("date", day, "present", 0L, "absent", 0L, "late", 0L)));
    }
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
