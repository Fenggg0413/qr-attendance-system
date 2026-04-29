package com.example.qrattendance;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.qrattendance.auth.PasswordHasher;
import com.example.qrattendance.qr.QrTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class QrAttendanceApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired QrTokenService qrTokenService;
  @Autowired JdbcTemplate jdbc;

  @Test
  void loginReturnsJwtAndCurrentUserProfile() throws Exception {
    String token = login("admin", "admin123");

    mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role", is("ADMIN")))
        .andExpect(jsonPath("$.username", is("admin")));
  }

  @Test
  void adminCanCreateClassAndCourse() throws Exception {
    String token = login("admin", "admin123");

    mvc.perform(
            post("/api/admin/classes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "测试班级", "grade", "2026"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNumber());

    mvc.perform(get("/api/admin/classes").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").exists());
  }

  @Test
  void adminCanCreateAndUpdateStudentGrade() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("年级学院-" + suffix);
    String username = "student-grade-" + suffix;
    String studentNo = "GRADE-" + suffix;

    JsonNode student =
        mapper.readTree(
            mvc.perform(
                    post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "年级学生", "username", username, "password", "student123", "studentNo", studentNo, "departmentId", departmentId, "grade", "2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade", is("2026")))
                .andReturn()
                .getResponse()
                .getContentAsString());

    long studentId = student.get("id").asLong();
    mvc.perform(
            put("/api/admin/students/" + studentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "年级学生", "studentNo", studentNo, "departmentId", departmentId, "grade", "2027"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.grade", is("2027")));

    mvc.perform(get("/api/admin/students").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.student_no == '" + studentNo + "')].grade").value(org.hamcrest.Matchers.hasItem("2027")));
  }

  @Test
  void adminDashboardAggregatesRealDepartmentCourseAndAttendanceData() throws Exception {
    long suffix = System.nanoTime();
    String adminToken = login("admin", "admin123");
    long departmentId = createDepartment("智能技术学院-" + suffix);
    long teacherId = createTeacher("teacher-dashboard-" + suffix, "teacher123", "仪表盘老师", departmentId);
    long studentId = createStudent("student-dashboard-" + suffix, "仪表盘学生", "D" + suffix, departmentId);
    long courseId = createCourse("仪表盘课程-" + suffix, "DASH-" + suffix, departmentId);
    long assignmentId = createAssignment(courseId, teacherId);
    enroll(assignmentId, studentId);

    String teacherToken = login("teacher-dashboard-" + suffix, "teacher123");
    long sessionId = createSession(teacherToken, courseId);
    jdbc.update(
        "INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)",
        sessionId,
        studentId,
        "LATE",
        "2026-04-26T08:10:00Z",
        "QR");

    mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kpis.studentTotal").isNumber())
        .andExpect(jsonPath("$.kpis.courseTotal").isNumber())
        .andExpect(jsonPath("$.kpis.departmentTotal").isNumber())
        .andExpect(jsonPath("$.distribution.late").isNumber())
        .andExpect(jsonPath("$.trend.length()", is(7)))
        .andExpect(jsonPath("$.courseAttendance[?(@.course_name == '仪表盘课程-" + suffix + "')].total").value(org.hamcrest.Matchers.hasItem(1)))
        .andExpect(jsonPath("$.courseAttendance[?(@.course_name == '仪表盘课程-" + suffix + "')].late").value(org.hamcrest.Matchers.hasItem(1)))
        .andExpect(jsonPath("$.recentActivities[?(@.student_name == '仪表盘学生')].status").value(org.hamcrest.Matchers.hasItem("LATE")));
  }

  @Test
  void adminCanManageDepartmentBackedCourseDetailWithoutClassBinding() throws Exception {
    String token = login("admin", "admin123");

    JsonNode department =
        mapper.readTree(
            mvc.perform(
                    post("/api/admin/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "人工智能学院"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("人工智能学院")))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long departmentId = department.get("id").asLong();

    JsonNode teacher =
        mapper.readTree(
            mvc.perform(
                    post("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "课程老师", "username", "course-teacher", "password", "teacher123", "departmentId", departmentId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    long teacherId = teacher.get("id").asLong();

    JsonNode student =
        mapper.readTree(
            mvc.perform(
                    post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "课程学生", "username", "course-student", "password", "student123", "studentNo", "C20260001", "departmentId", departmentId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    long studentId = student.get("id").asLong();

    JsonNode course =
        mapper.readTree(
            mvc.perform(
                    post("/api/admin/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "生成式 AI", "code", "GEN-AI", "departmentId", departmentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department_name", is("人工智能学院")))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long courseId = course.get("id").asLong();

    mvc.perform(
            put("/api/admin/courses/" + courseId + "/schedule")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("weekday", "周二", "startTime", "14:00", "endTime", "15:40", "location", "教学楼A-301"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location", is("教学楼A-301")));

    mvc.perform(
            put("/api/admin/courses/" + courseId + "/teacher")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("teacherId", teacherId, "term", "2026 春季"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teacher_id", is((int) teacherId)));

    mvc.perform(
            post("/api/admin/courses/" + courseId + "/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("studentId", studentId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student_id", is((int) studentId)));

    mvc.perform(get("/api/admin/courses/" + courseId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.course.name", is("生成式 AI")))
        .andExpect(jsonPath("$.schedule.location", is("教学楼A-301")))
        .andExpect(jsonPath("$.teacher.name", is("课程老师")))
        .andExpect(jsonPath("$.students[0].name", is("课程学生")));

    mvc.perform(get("/api/admin/courses").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == 'GEN-AI')].teacher_name").value(org.hamcrest.Matchers.hasItem("课程老师")))
        .andExpect(jsonPath("$[?(@.code == 'GEN-AI')].term").value(org.hamcrest.Matchers.hasItem("2026 春季")))
        .andExpect(jsonPath("$[?(@.code == 'GEN-AI')].student_count").value(org.hamcrest.Matchers.hasItem(1)));
  }

  @Test
  void adminCanFetchTermOptionsForCourseDetail() throws Exception {
    String token = login("admin", "admin123");

    mvc.perform(get("/api/admin/terms").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].value").isString())
        .andExpect(jsonPath("$[0].label").isString())
        .andExpect(jsonPath("$[?(@.value == '2025-2026学年 秋季学期')].label").value(org.hamcrest.Matchers.hasItem("2025-2026学年 秋季学期")));
  }

  @Test
  void adminCanManageClassroomsAndCourseScheduleSlotsWithConflictChecks() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("排课学院-" + suffix);
    long courseId = createCourse("排课课程-" + suffix, "SLOT-" + suffix, departmentId);
    long otherCourseId = createCourse("冲突课程-" + suffix, "SLOT-OTHER-" + suffix, departmentId);
    long teacherId = createTeacher("slot-teacher-" + suffix, "teacher123", "排课教师", departmentId);
    long otherTeacherId = createTeacher("slot-other-teacher-" + suffix, "teacher123", "备用教师", departmentId);
    createAssignment(courseId, teacherId);
    createAssignment(otherCourseId, otherTeacherId);

    JsonNode classroom =
        mapper.readTree(
            mvc.perform(
                    post("/api/admin/classrooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "实验楼 101-" + suffix, "building", "实验楼", "capacity", 60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("实验楼 101-" + suffix)))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long classroomId = classroom.get("id").asLong();

    mvc.perform(get("/api/admin/classrooms").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + classroomId + ")].name").value(org.hamcrest.Matchers.hasItem("实验楼 101-" + suffix)));

    mvc.perform(
            put("/api/admin/courses/" + courseId + "/schedule-slots")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("weekday", "周一", "period", 1, "teacherId", teacherId, "classroomId", classroomId, "courseType", "LAB"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weekday", is("周一")))
        .andExpect(jsonPath("$.period", is(1)))
        .andExpect(jsonPath("$.teacher_name", is("排课教师")))
        .andExpect(jsonPath("$.classroom_name", is("实验楼 101-" + suffix)))
        .andExpect(jsonPath("$.course_type", is("LAB")));

    mvc.perform(get("/api/admin/courses/" + courseId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teachers[0].name", is("排课教师")))
        .andExpect(jsonPath("$.scheduleSlots[0].classroom_name", is("实验楼 101-" + suffix)))
        .andExpect(jsonPath("$.scheduleSlots[0].course_type", is("LAB")));

    mvc.perform(
            put("/api/admin/courses/" + otherCourseId + "/schedule-slots")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("weekday", "周一", "period", 1, "teacherId", teacherId, "classroomId", classroomId, "courseType", "LECTURE"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("教师")));

    mvc.perform(
            put("/api/admin/courses/" + otherCourseId + "/schedule-slots")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("weekday", "周一", "period", 1, "teacherId", otherTeacherId, "classroomId", classroomId, "courseType", "LECTURE"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("教室")));
  }

  @Test
  void adminCanDeleteScheduleSlotReferencedByAttendanceSession() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("删除排课学院-" + suffix);
    long courseId = createCourse("删除排课课程-" + suffix, "DEL-SLOT-" + suffix, departmentId);
    long teacherId = createTeacher("delete-slot-teacher-" + suffix, "teacher123", "删除排课教师", departmentId);
    createAssignment(courseId, teacherId);
    long classroomId = createClassroom("删除排课教室-" + suffix, "教学楼");
    long slotId = createScheduleSlot(courseId, teacherId, classroomId, "周一", 1, "LECTURE");
    long sessionId = insertSession(courseId, teacherId, slotId, "CLOSED");

    mvc.perform(delete("/api/admin/courses/" + courseId + "/schedule-slots/" + slotId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    Object scheduleSlotId =
        jdbc.queryForObject("SELECT schedule_slot_id FROM attendance_sessions WHERE id = ?", Object.class, sessionId);
    org.junit.jupiter.api.Assertions.assertNull(scheduleSlotId);
  }

  @Test
  void adminTeacherCreateCanRepairExistingOrphanTeacherUser() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("教师修复学院-" + suffix);
    insertUser("teacher-orphan-" + suffix, "teacher123", "TEACHER", "孤立教师");

    mvc.perform(
            post("/api/admin/teachers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "孤立教师", "username", "teacher-orphan-" + suffix, "password", "teacher123", "departmentId", departmentId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username", is("teacher-orphan-" + suffix)))
        .andExpect(jsonPath("$.department_name", is("教师修复学院-" + suffix)));
  }

  @Test
  void adminTeacherCreateReturnsConflictWhenUsernameAlreadyBelongsToTeacherProfile() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("教师账号冲突学院-" + suffix);
    createTeacher("teacher-username-conflict-" + suffix, "teacher123", "已有账号教师", departmentId);

    mvc.perform(
            post("/api/admin/teachers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "重复账号教师", "username", "teacher-username-conflict-" + suffix, "password", "teacher123", "departmentId", departmentId))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("账号")));
  }

  @Test
  void adminCanResetTeacherPasswordToDefault() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("教师重置密码学院-" + suffix);
    long teacherId = createTeacher("teacher-reset-" + suffix, "custompass", "重置密码教师", departmentId);

    mvc.perform(post("/api/admin/teachers/" + teacherId + "/reset-password").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok", is(true)));

    login("teacher-reset-" + suffix, "123456");
  }

  @Test
  void adminStudentCreateReturnsConflictWithoutLeavingOrphanUserWhenStudentNumberExists() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("冲突学院-" + suffix);
    createStudent("student-existing-" + suffix, "已有学生", "DUP-" + suffix, departmentId);

    mvc.perform(
            post("/api/admin/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "重复学号学生", "username", "student-conflict-" + suffix, "password", "student123", "studentNo", "DUP-" + suffix, "departmentId", departmentId))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("学号")));

    Integer orphanUsers =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM users u
            LEFT JOIN students s ON s.user_id = u.id
            WHERE u.username = ? AND s.id IS NULL
            """,
            Integer.class,
            "student-conflict-" + suffix);
    org.junit.jupiter.api.Assertions.assertEquals(0, orphanUsers);
  }

  @Test
  void adminStudentCreateCanRepairExistingOrphanStudentUser() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("修复学院-" + suffix);
    insertUser("student-orphan-" + suffix, "student123", "STUDENT", "孤立学生");

    mvc.perform(
            post("/api/admin/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "孤立学生", "username", "student-orphan-" + suffix, "password", "student123", "studentNo", "ORPHAN-" + suffix, "departmentId", departmentId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username", is("student-orphan-" + suffix)))
        .andExpect(jsonPath("$.student_no", is("ORPHAN-" + suffix)));
  }

  @Test
  void adminStudentCreateReturnsConflictWhenUsernameAlreadyBelongsToStudentProfile() throws Exception {
    long suffix = System.nanoTime();
    String token = login("admin", "admin123");
    long departmentId = createDepartment("账号冲突学院-" + suffix);
    createStudent("student-username-conflict-" + suffix, "已有账号学生", "USER-" + suffix, departmentId);

    mvc.perform(
            post("/api/admin/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "重复账号学生", "username", "student-username-conflict-" + suffix, "password", "student123", "studentNo", "USER-NEW-" + suffix, "departmentId", departmentId))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("账号")));
  }

  @Test
  void adminCanManageEnrollmentsAndSessionStatsUseExplicitRoster() throws Exception {
    long suffix = System.nanoTime();
    String adminToken = login("admin", "admin123");
    long departmentId = createDepartment("测试选课学院-" + suffix);
    long teacherId = createTeacher("teacher-enroll-" + suffix, "teacher123", "选课老师", departmentId);
    long studentId = createStudent("student-enroll-" + suffix, "选课学生", "E" + suffix, departmentId);
    createStudent("student-not-enrolled-" + suffix, "未选学生", "N" + suffix, departmentId);
    long courseId = createCourse("选课测试课程-" + suffix, "ENROLL-" + suffix, departmentId);
    long assignmentId = createAssignment(courseId, teacherId);

    mvc.perform(
            put("/api/admin/course-assignments/" + assignmentId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("courseId", courseId, "teacherId", teacherId, "term", "2026-2027 第一学期"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.term", is("2026-2027 第一学期")));

    JsonNode enrollment =
        mapper.readTree(
            mvc.perform(
                    post("/api/admin/enrollments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assignmentId", assignmentId, "studentId", studentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course_name", is("选课测试课程-" + suffix)))
                .andExpect(jsonPath("$.student_name", is("选课学生")))
                .andReturn()
                .getResponse()
                .getContentAsString());

    mvc.perform(
            post("/api/admin/enrollments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("assignmentId", assignmentId, "studentId", studentId))))
        .andExpect(status().isConflict());

    String teacherToken = login("teacher-enroll-" + suffix, "teacher123");
    mvc.perform(get("/api/teacher/courses/" + courseId + "/students").header("Authorization", "Bearer " + teacherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(1)))
        .andExpect(jsonPath("$[0].student_name").doesNotExist())
        .andExpect(jsonPath("$[0].name", is("选课学生")));

    long sessionId = createSession(teacherToken, courseId);
    jdbc.update(
        "INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)",
        sessionId,
        studentId,
        "PRESENT",
        "2026-04-26T08:00:00Z",
        "QR");

    mvc.perform(get("/api/admin/attendance-stats").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.session_id == " + sessionId + ")].total").value(org.hamcrest.Matchers.hasItem(1)))
        .andExpect(jsonPath("$[?(@.session_id == " + sessionId + ")].present").value(org.hamcrest.Matchers.hasItem(1)))
        .andExpect(jsonPath("$[?(@.session_id == " + sessionId + ")].absent").value(org.hamcrest.Matchers.hasItem(0)));

    mvc.perform(delete("/api/admin/enrollments/" + enrollment.get("id").asLong()).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  void teacherStartsSessionAndQrPayloadUsesTenSecondToken() throws Exception {
    String token = login("teacher1", "teacher123");
    long sessionId = createSession(token);

    mvc.perform(get("/api/teacher/attendance-sessions/" + sessionId + "/qr").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId", is((int) sessionId)))
        .andExpect(jsonPath("$.payload", containsString("qr-attendance://checkin")))
        .andExpect(jsonPath("$.token").isString());
  }

  @Test
  void teacherCourseWorkflowReturnsFullRecordsAndCanCloseSession() throws Exception {
    long extraStudentId = createStudent("student-extra", "王同学", "20230002", 1);
    enroll(1, extraStudentId);
    String token = login("teacher1", "teacher123");

    mvc.perform(get("/api/teacher/courses").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].student_count", is(2)))
        .andExpect(jsonPath("$[0].semester", is("2025-2026 第二学期")));

    mvc.perform(get("/api/teacher/courses/1").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.student_count", is(2)))
        .andExpect(jsonPath("$.scheduleSlots[0].classroom_name", is("教三-101")));

    long seedSlotId = ensureAnySlot(1L, teacherIdForCourse(1L));
    JsonNode created =
        mapper.readTree(
            mvc.perform(
                    post("/api/teacher/attendance-sessions/makeup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slotId", seedSlotId, "reason", "课堂测试", "durationMinutes", 5, "method", "CODE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("CODE")))
                .andExpect(jsonPath("$.kind", is("MAKEUP")))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long sessionId = created.get("id").asLong();

    mvc.perform(get("/api/teacher/courses/1/attendance-sessions").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].method", is("CODE")))
        .andExpect(jsonPath("$[0].absent_count", is(2)));

    mvc.perform(get("/api/teacher/attendance-sessions/" + sessionId + "/records").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(2)))
        .andExpect(jsonPath("$[0].status", is("ABSENT")));

    mvc.perform(
            put("/api/teacher/students/" + extraStudentId + "/note")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("note", "需要课后提醒"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.note", is("需要课后提醒")));

    mvc.perform(get("/api/teacher/courses/1/students").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.student_no == '20230002')].note").value(org.hamcrest.Matchers.hasItem("需要课后提醒")));

    mvc.perform(post("/api/teacher/attendance-sessions/" + sessionId + "/close").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CLOSED")));

    mvc.perform(get("/api/teacher/attendance-sessions/" + sessionId + "/qr").header("Authorization", "Bearer " + token))
        .andExpect(status().isGone());
  }

  @Test
  void teacherProfileCanBeUpdatedAndPasswordChanged() throws Exception {
    long departmentId = createDepartment("数学学院");
    createTeacher("teacher-profile", "oldpass123", "赵老师", departmentId);
    String token = login("teacher-profile", "oldpass123");

    mvc.perform(get("/api/teacher/profile").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.department", is("数学学院")));

    mvc.perform(
            put("/api/teacher/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("phone", "13800000000", "email", "teacher@example.com"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone", is("13800000000")))
        .andExpect(jsonPath("$.email", is("teacher@example.com")));

    mvc.perform(
            post("/api/teacher/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", "oldpass123", "newPassword", "newpass123"))))
        .andExpect(status().isOk());

    login("teacher-profile", "newpass123");
  }

  @Test
  void studentCanFetchCoursesSessionsLeavesAndUpdateAccount() throws Exception {
    StudentCourseSeed seed = seedStudentWithCourse("student-flow");
    String token = login(seed.studentUsername(), "student123");

    mvc.perform(get("/api/student/courses").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is((int) seed.courseId())))
        .andExpect(jsonPath("$[0].name", is(seed.courseName())))
        .andExpect(jsonPath("$[0].teacherName", is(seed.teacherName())));

    mvc.perform(get("/api/student/sessions").param("scope", "active").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is((int) seed.sessionId())))
        .andExpect(jsonPath("$[0].courseName", is(seed.courseName())))
        .andExpect(jsonPath("$[0].checkedIn", is(false)))
        .andExpect(jsonPath("$[0].hasLeave", is(false)))
        .andExpect(jsonPath("$[0].canRequestLeave", is(true)));

    JsonNode leave =
        mapper.readTree(
            mvc.perform(
                    post("/api/student/leave-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sessionId", seed.sessionId(), "reason", "参加竞赛"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    mvc.perform(get("/api/student/leave-requests").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is(leave.get("id").asInt())))
        .andExpect(jsonPath("$[0].courseName", is(seed.courseName())))
        .andExpect(jsonPath("$[0].reason", is("参加竞赛")))
        .andExpect(jsonPath("$[0].status", is("PENDING")));

    mvc.perform(get("/api/student/sessions").param("scope", "active").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].hasLeave", is(true)))
        .andExpect(jsonPath("$[0].canRequestLeave", is(false)));

    mvc.perform(
            put("/api/student/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("displayName", "移动端学生"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName", is("移动端学生")));

    mvc.perform(
            post("/api/student/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", "student123", "newPassword", "newstudent123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok", is(true)));

    login(seed.studentUsername(), "newstudent123");
  }

  @Test
  void studentScheduleReturnsCurrentEnrollments() throws Exception {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("课表学院-" + suffix);
    long teacherId = createTeacher("teacher-schedule-" + suffix, "teacher123", "课表老师", departmentId);
    long studentId = createStudent("student-schedule-" + suffix, "课表学生", "SCH-" + suffix, departmentId);
    long otherStudentId = createStudent("student-schedule-other-" + suffix, "其他学生", "SCHO-" + suffix, departmentId);
    long courseId = createCourse("移动应用开发-" + suffix, "MOBILE-" + suffix, departmentId);
    long assignmentId = createAssignment(courseId, teacherId, "2025-2026学年 春季学期");
    enroll(assignmentId, studentId);
    long classroomId = createClassroom("综合楼 302-" + suffix, "综合楼");
    createScheduleSlot(courseId, teacherId, classroomId, "周三", 2, "必修");

    String token = login("student-schedule-" + suffix, "student123");
    String otherToken = login("student-schedule-other-" + suffix, "student123");

    mvc.perform(get("/api/student/schedule").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].weekday", is("周三")))
        .andExpect(jsonPath("$[0].period", is(2)))
        .andExpect(jsonPath("$[0].courseType", is("必修")))
        .andExpect(jsonPath("$[0].courseId", is((int) courseId)))
        .andExpect(jsonPath("$[0].courseName", is("移动应用开发-" + suffix)))
        .andExpect(jsonPath("$[0].courseCode", is("MOBILE-" + suffix)))
        .andExpect(jsonPath("$[0].classroomName", is("综合楼 302-" + suffix)))
        .andExpect(jsonPath("$[0].classroomLocation", is("综合楼")))
        .andExpect(jsonPath("$[0].teacherName", is("课表老师")))
        .andExpect(jsonPath("$[0].term", is("2025-2026学年 春季学期")));

    mvc.perform(get("/api/student/schedule").header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void studentDashboardReturnsAggregates() throws Exception {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("学生仪表盘学院-" + suffix);
    long teacherId = createTeacher("teacher-student-dashboard-" + suffix, "teacher123", "学生仪表盘老师", departmentId);
    long studentId = createStudent("student-dashboard-api-" + suffix, "学生仪表盘学生", "DASH-STU-" + suffix, departmentId);
    long courseId = createCourse("学生仪表盘课程-" + suffix, "SDASH-" + suffix, departmentId);
    long assignmentId = createAssignment(courseId, teacherId, "2025-2026学年 春季学期");
    enroll(assignmentId, studentId);
    long classroomId = createClassroom("未来教室 101-" + suffix, "未来教室");
    createScheduleSlot(courseId, teacherId, classroomId, todayWeekday(), 1, "必修");

    long presentSession = insertSession(courseId, teacherId, "OPEN");
    long lateSession = insertSession(courseId, teacherId, "CLOSED");
    long absentSession = insertSession(courseId, teacherId, "CLOSED");
    long excusedSession = insertSession(courseId, teacherId, "CLOSED");
    insertRecord(presentSession, studentId, "PRESENT");
    insertRecord(lateSession, studentId, "LATE");
    insertRecord(absentSession, studentId, "ABSENT");
    insertRecord(excusedSession, studentId, "EXCUSED");
    jdbc.update(
        "INSERT INTO leave_requests(session_id, student_id, reason, status, created_at) VALUES (?, ?, ?, ?, ?)",
        absentSession,
        studentId,
        "缺勤申诉",
        "PENDING",
        Instant.now().toString());

    String token = login("student-dashboard-api-" + suffix, "student123");

    mvc.perform(get("/api/student/dashboard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.todayCount", is(1)))
        .andExpect(jsonPath("$.checkedInCount", is(1)))
        .andExpect(jsonPath("$.pendingLeaveCount", is(1)))
        .andExpect(jsonPath("$.absentCount", is(1)))
        .andExpect(jsonPath("$.lateCount", is(1)))
        .andExpect(jsonPath("$.excusedCount", is(1)))
        .andExpect(jsonPath("$.semesterAttendanceRate", is(0.5)))
        .andExpect(jsonPath("$.todaySessions.length()", is(1)))
        .andExpect(jsonPath("$.todaySessions[0].courseName", is("学生仪表盘课程-" + suffix)))
        .andExpect(jsonPath("$.todaySessions[0].classroomName", is("未来教室 101-" + suffix)))
        .andExpect(jsonPath("$.todaySessions[0].period", is(1)))
        .andExpect(jsonPath("$.todaySessions[0].recordStatus", is("EXCUSED")));
  }

  @Test
  void studentDashboardDoesNotShowAttendanceSessionsOutsideTodaySchedule() throws Exception {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("非今日课表学院-" + suffix);
    long teacherId = createTeacher("teacher-not-today-" + suffix, "teacher123", "非今日课表老师", departmentId);
    long studentId = createStudent("student-not-today-" + suffix, "非今日课表学生", "NTODAY-" + suffix, departmentId);
    long courseId = createCourse("非今日课表课程-" + suffix, "NTODAY-" + suffix, departmentId);
    long assignmentId = createAssignment(courseId, teacherId, "2025-2026学年 春季学期");
    enroll(assignmentId, studentId);
    long classroomId = createClassroom("非今日教室 101-" + suffix, "非今日楼");
    createScheduleSlot(courseId, teacherId, classroomId, otherWeekday(), 1, "必修");
    insertSession(courseId, teacherId, "CLOSED");

    String token = login("student-not-today-" + suffix, "student123");

    mvc.perform(get("/api/student/dashboard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.todayCount", is(0)))
        .andExpect(jsonPath("$.todaySessions").isEmpty());
  }

  @Test
  void studentEndpointsRequireBearerToken() throws Exception {
    mvc.perform(get("/api/student/courses")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/student/sessions")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/student/leave-requests")).andExpect(status().isUnauthorized());
    mvc.perform(put("/api/student/profile").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("displayName", "x"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/student/password").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("currentPassword", "a", "newPassword", "b"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void studentEndpointsRejectWrongRoleAndHideOtherStudentsData() throws Exception {
    StudentCourseSeed owner = seedStudentWithCourse("student-owner");
    StudentCourseSeed other = seedStudentWithCourse("student-other");
    String teacherToken = login(owner.teacherUsername(), "teacher123");
    String otherStudentToken = login(other.studentUsername(), "student123");

    mvc.perform(get("/api/student/courses").header("Authorization", "Bearer " + teacherToken))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/student/sessions").header("Authorization", "Bearer " + teacherToken))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/student/leave-requests").header("Authorization", "Bearer " + teacherToken))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/student/profile")
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("displayName", "x"))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/student/password")
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", "teacher123", "newPassword", "newpass123"))))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/student/courses").header("Authorization", "Bearer " + otherStudentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + owner.courseId() + ")]").isEmpty());
    mvc.perform(get("/api/student/sessions").header("Authorization", "Bearer " + otherStudentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + owner.sessionId() + ")]").isEmpty());

    mvc.perform(
            post("/api/student/leave-requests")
                .header("Authorization", "Bearer " + otherStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sessionId", owner.sessionId(), "reason", "跨学生请假"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void studentCheckInRejectsOldQrAndDeduplicatesCurrentQr() throws Exception {
    String teacherToken = login("teacher1", "teacher123");
    long sessionId = createSession(teacherToken);
    String studentToken = login("B22042101", "123456");
    long oldBucket = System.currentTimeMillis() / 1000 / QrTokenService.BUCKET_SECONDS - 1;

    mvc.perform(
            post("/api/student/check-ins")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sessionId", sessionId, "token", qrTokenService.tokenFor(sessionId, oldBucket)))))
        .andExpect(status().isBadRequest());

    JsonNode qr =
        mapper.readTree(
            mvc.perform(get("/api/teacher/attendance-sessions/" + sessionId + "/qr").header("Authorization", "Bearer " + teacherToken))
                .andReturn()
                .getResponse()
                .getContentAsString());
    Map<String, Object> checkIn = Map.of("sessionId", sessionId, "token", qr.get("token").asText());

    mvc.perform(post("/api/student/check-ins").header("Authorization", "Bearer " + studentToken).contentType(MediaType.APPLICATION_JSON).content(json(checkIn)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.duplicate", is(false)))
        .andExpect(jsonPath("$.record.status", is("PRESENT")));

    mvc.perform(post("/api/student/check-ins").header("Authorization", "Bearer " + studentToken).contentType(MediaType.APPLICATION_JSON).content(json(checkIn)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.duplicate", is(true)));
  }

  @Test
  void approvedLeaveCreatesExcusedRecordForStatistics() throws Exception {
    String teacherToken = login("teacher1", "teacher123");
    long sessionId = createSession(teacherToken);
    String studentToken = login("B22042101", "123456");
    String adminToken = login("admin", "admin123");

    JsonNode leave =
        mapper.readTree(
            mvc.perform(
                    post("/api/student/leave-requests")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sessionId", sessionId, "reason", "病假"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    mvc.perform(
            post("/api/admin/leave-requests/" + leave.get("id").asLong() + "/review")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approved", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("APPROVED")));

    mvc.perform(get("/api/admin/statistics").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status", is("EXCUSED")));
  }

  @Test
  void teacherCanReviewLeaveRequestsForOwnCoursesOnly() throws Exception {
    long suffix = System.nanoTime();
    String adminToken = login("admin", "admin123");
    long departmentId = createDepartment("申报学院-" + suffix);
    long ownerTeacherId =
        createTeacher("teacher-leave-owner-" + suffix, "teacher123", "申报老师", departmentId);
    createTeacher("teacher-leave-other-" + suffix, "teacher123", "无关老师", departmentId);
    long studentId =
        createStudent("student-leave-" + suffix, "申报学生", "L" + suffix, departmentId);
    long courseId =
        createCourse("申报课程-" + suffix, "LEAVE-" + suffix, departmentId);
    long assignmentId = createAssignment(courseId, ownerTeacherId);
    enroll(assignmentId, studentId);

    String ownerToken = login("teacher-leave-owner-" + suffix, "teacher123");
    String otherToken = login("teacher-leave-other-" + suffix, "teacher123");
    long sessionId = createSession(ownerToken, courseId);

    String studentToken = login("student-leave-" + suffix, "student123");
    JsonNode leave =
        mapper.readTree(
            mvc.perform(
                    post("/api/student/leave-requests")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sessionId", sessionId, "reason", "高烧请假"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    long leaveId = leave.get("id").asLong();

    mvc.perform(get("/api/teacher/leave-requests").header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + leaveId + ")].student_name").value(org.hamcrest.Matchers.hasItem("申报学生")))
        .andExpect(jsonPath("$[?(@.id == " + leaveId + ")].course_name").value(org.hamcrest.Matchers.hasItem("申报课程-" + suffix)))
        .andExpect(jsonPath("$[?(@.id == " + leaveId + ")].status").value(org.hamcrest.Matchers.hasItem("PENDING")));

    mvc.perform(get("/api/teacher/leave-requests").header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + leaveId + ")]").isEmpty());

    mvc.perform(
            post("/api/teacher/leave-requests/" + leaveId + "/review")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approved", true))))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/teacher/leave-requests/" + leaveId + "/review")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approved", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("APPROVED")));

    Map<String, Object> record =
        jdbc.queryForMap(
            "SELECT status, source FROM attendance_records WHERE session_id = ? AND student_id = ?",
            sessionId,
            studentId);
    org.junit.jupiter.api.Assertions.assertEquals("EXCUSED", record.get("status"));
    org.junit.jupiter.api.Assertions.assertEquals("LEAVE", record.get("source"));

    mvc.perform(
            post("/api/teacher/leave-requests/" + leaveId + "/review")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approved", false))))
        .andExpect(status().isConflict());

    mvc.perform(
            get("/api/teacher/leave-requests")
                .param("status", "APPROVED")
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + leaveId + ")].status").value(org.hamcrest.Matchers.hasItem("APPROVED")));

    // Admin route stays available for cross-cutting review.
    mvc.perform(get("/api/admin/leave-requests").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  void teacherDashboardEndpointIsRemoved() throws Exception {
    String token = login("teacher1", "teacher123");
    mvc.perform(get("/api/teacher/dashboard").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void freeFormSessionCreateEndpointIsRemoved() throws Exception {
    String token = login("teacher1", "teacher123");
    mvc.perform(
            post("/api/teacher/courses/1/attendance-sessions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("durationMinutes", 5))))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void slotSessionRejectsOutsideTimeWindow() throws Exception {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("时间窗学院-" + suffix);
    long teacherId = createTeacher("teacher-window-" + suffix, "teacher123", "窗口老师", departmentId);
    long courseId = createCourse("窗口课-" + suffix, "WIN-" + suffix, departmentId);
    createAssignment(courseId, teacherId);
    long classroomId = createClassroom("窗口教室-" + suffix, "教学楼");
    long slotId = createScheduleSlot(courseId, teacherId, classroomId, otherWeekday(), 1, "LECTURE");

    String token = login("teacher-window-" + suffix, "teacher123");
    mvc.perform(
            post("/api/teacher/schedule-slots/" + slotId + "/attendance-sessions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("method", "QR"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message", containsString("排课日")));
  }

  @Test
  void slotSessionRejectsForeignTeacherSlot() throws Exception {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("跨教师学院-" + suffix);
    long ownerId = createTeacher("teacher-owner-" + suffix, "teacher123", "拥有老师", departmentId);
    createTeacher("teacher-foreign-" + suffix, "teacher123", "外来老师", departmentId);
    long courseId = createCourse("外来课-" + suffix, "FOR-" + suffix, departmentId);
    createAssignment(courseId, ownerId);
    long classroomId = createClassroom("外来教室-" + suffix, "教学楼");
    long slotId = createScheduleSlot(courseId, ownerId, classroomId, "周一", 1, "LECTURE");

    String foreign = login("teacher-foreign-" + suffix, "teacher123");
    mvc.perform(
            post("/api/teacher/schedule-slots/" + slotId + "/attendance-sessions")
                .header("Authorization", "Bearer " + foreign)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("method", "QR"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void makeupSessionRequiresReasonAndPersistsKind() throws Exception {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("补考勤学院-" + suffix);
    long teacherId = createTeacher("teacher-makeup-" + suffix, "teacher123", "补考勤老师", departmentId);
    long courseId = createCourse("补考勤课-" + suffix, "MK-" + suffix, departmentId);
    createAssignment(courseId, teacherId);
    long classroomId = createClassroom("补考勤教室-" + suffix, "教学楼");
    long slotId = createScheduleSlot(courseId, teacherId, classroomId, "周一", 1, "LECTURE");

    String token = login("teacher-makeup-" + suffix, "teacher123");
    mvc.perform(
            post("/api/teacher/attendance-sessions/makeup")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("slotId", slotId, "reason", ""))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("理由")));

    JsonNode created =
        mapper.readTree(
            mvc.perform(
                    post("/api/teacher/attendance-sessions/makeup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slotId", slotId, "reason", "调课补签"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind", is("MAKEUP")))
                .andExpect(jsonPath("$.makeupReason", is("调课补签")))
                .andReturn()
                .getResponse()
                .getContentAsString());

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT schedule_slot_id, kind, makeup_reason FROM attendance_sessions WHERE id = ?",
            created.get("id").asLong());
    org.junit.jupiter.api.Assertions.assertEquals(slotId, ((Number) row.get("schedule_slot_id")).longValue());
    org.junit.jupiter.api.Assertions.assertEquals("MAKEUP", row.get("kind"));
    org.junit.jupiter.api.Assertions.assertEquals("调课补签", row.get("makeup_reason"));
  }

  @Test
  void teacherTodayMergesContiguousPeriodsAndSurfacesActiveSession() throws Exception {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("今日合并学院-" + suffix);
    long teacherId = createTeacher("teacher-today-" + suffix, "teacher123", "今日老师", departmentId);
    long courseId = createCourse("今日课-" + suffix, "TODAY-" + suffix, departmentId);
    createAssignment(courseId, teacherId);
    long classroomId = createClassroom("今日教室-" + suffix, "教学楼");
    String today = todayWeekday();
    long slot1 = createScheduleSlot(courseId, teacherId, classroomId, today, 1, "LECTURE");
    long slot2 = createScheduleSlot(courseId, teacherId, classroomId, today, 2, "LECTURE");

    long sessionId = insertSession(courseId, teacherId, slot1, "OPEN");

    String token = login("teacher-today-" + suffix, "teacher123");
    mvc.perform(get("/api/teacher/today").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.slotId == " + slot1 + ")].periodEnd").value(org.hamcrest.Matchers.hasItem(2)))
        .andExpect(jsonPath("$[?(@.slotId == " + slot1 + ")].session.id").value(org.hamcrest.Matchers.hasItem((int) sessionId)))
        .andExpect(jsonPath("$[?(@.slotId == " + slot2 + ")]").isEmpty());
  }

  @Test
  void teacherTodayDoesNotMergeAcrossLunchGap() throws Exception {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("午休不合并学院-" + suffix);
    long teacherId = createTeacher("teacher-lunch-" + suffix, "teacher123", "午休老师", departmentId);
    long courseId = createCourse("午休课-" + suffix, "LUNCH-" + suffix, departmentId);
    createAssignment(courseId, teacherId);
    long classroomId = createClassroom("午休教室-" + suffix, "教学楼");
    String today = todayWeekday();
    long morning = createScheduleSlot(courseId, teacherId, classroomId, today, 5, "LECTURE");
    long afternoon = createScheduleSlot(courseId, teacherId, classroomId, today, 6, "LECTURE");

    String token = login("teacher-lunch-" + suffix, "teacher123");
    mvc.perform(get("/api/teacher/today").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.slotId == " + morning + ")].periodEnd").value(org.hamcrest.Matchers.hasItem(5)))
        .andExpect(jsonPath("$[?(@.slotId == " + afternoon + ")].periodEnd").value(org.hamcrest.Matchers.hasItem(6)));
  }

  private String login(String username, String password) throws Exception {
    JsonNode node =
        mapper.readTree(
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return node.get("token").asText();
  }

  private long createSession(String teacherToken) throws Exception {
    return createSession(teacherToken, 1);
  }

  private long createSession(String teacherToken, long courseId) throws Exception {
    long teacherId = teacherIdForCourse(courseId);
    long slotId = ensureAnySlot(courseId, teacherId);
    JsonNode node =
        mapper.readTree(
            mvc.perform(
                    post("/api/teacher/attendance-sessions/makeup")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slotId", slotId, "reason", "测试用例", "durationMinutes", 5))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return node.get("id").asLong();
  }

  private long teacherIdForCourse(long courseId) {
    return jdbc.queryForObject(
        "SELECT teacher_id FROM course_assignments WHERE course_id = ? ORDER BY id LIMIT 1",
        Long.class,
        courseId);
  }

  private long ensureAnySlot(long courseId, long teacherId) {
    Long existing =
        jdbc.query(
            "SELECT id FROM course_schedule_slots WHERE course_id = ? AND teacher_id = ? ORDER BY id LIMIT 1",
            rs -> rs.next() ? rs.getLong("id") : null,
            courseId,
            teacherId);
    if (existing != null) return existing;
    long classroomId =
        jdbc.query(
            "SELECT id FROM classrooms ORDER BY id LIMIT 1",
            rs -> rs.next() ? rs.getLong("id") : 0L);
    if (classroomId == 0L) {
      classroomId = createClassroom("测试教室-" + System.nanoTime(), "测试楼");
    }
    return createScheduleSlot(courseId, teacherId, classroomId, "周一", 1, "LECTURE");
  }

  private long createClass(String name, String grade) {
    jdbc.update("INSERT INTO classes(name, grade) VALUES (?, ?)", name, grade);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createDepartment(String name) {
    jdbc.update("INSERT INTO departments(name) VALUES (?)", name);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createCourse(String name, String code, long departmentId) {
    jdbc.update("INSERT INTO courses(name, code, department_id) VALUES (?, ?, ?)", name, code, departmentId);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createAssignment(long courseId, long teacherId) {
    jdbc.update("INSERT INTO course_assignments(course_id, teacher_id) VALUES (?, ?)", courseId, teacherId);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createAssignment(long courseId, long teacherId, String term) {
    jdbc.update("INSERT INTO course_assignments(course_id, teacher_id, term) VALUES (?, ?, ?)", courseId, teacherId, term);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createStudent(String username, String name, String studentNo, long departmentId) {
    long userId = insertUser(username, "student123", "STUDENT", name);
    jdbc.update("INSERT INTO students(user_id, department_id, name, student_no) VALUES (?, ?, ?, ?)", userId, departmentId, name, studentNo);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createTeacher(String username, String password, String name, long departmentId) {
    long userId = insertUser(username, password, "TEACHER", name);
    jdbc.update("INSERT INTO teachers(user_id, name, department_id) VALUES (?, ?, ?)", userId, name, departmentId);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private void enroll(long assignmentId, long studentId) {
    jdbc.update("INSERT INTO course_enrollments(assignment_id, student_id) VALUES (?, ?)", assignmentId, studentId);
  }

  private long createClassroom(String name, String building) {
    jdbc.update("INSERT INTO classrooms(name, building, capacity) VALUES (?, ?, ?)", name, building, 80);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createScheduleSlot(long courseId, long teacherId, long classroomId, String weekday, int period, String courseType) {
    jdbc.update(
        "INSERT INTO course_schedule_slots(course_id, teacher_id, classroom_id, weekday, period, course_type) VALUES (?, ?, ?, ?, ?, ?)",
        courseId,
        teacherId,
        classroomId,
        weekday,
        period,
        courseType);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long insertSession(long courseId, long teacherId, String status) {
    long slotId = ensureAnySlot(courseId, teacherId);
    return insertSession(courseId, teacherId, slotId, status);
  }

  private long insertSession(long courseId, long teacherId, long slotId, String status) {
    Integer periodEnd =
        jdbc.queryForObject(
            "SELECT period FROM course_schedule_slots WHERE id = ?", Integer.class, slotId);
    jdbc.update(
        "INSERT INTO attendance_sessions(course_id, teacher_id, started_at, ends_at, status, method, schedule_slot_id, period_end, kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        courseId,
        teacherId,
        Instant.now().minusSeconds(60).toString(),
        Instant.now().plusSeconds(600).toString(),
        status,
        "QR",
        slotId,
        periodEnd,
        "SCHEDULED");
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private void insertRecord(long sessionId, long studentId, String status) {
    jdbc.update(
        "INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)",
        sessionId,
        studentId,
        status,
        Instant.now().toString(),
        "QR");
  }

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

  private String otherWeekday() {
    return "周一".equals(todayWeekday()) ? "周二" : "周一";
  }

  private long insertUser(String username, String password, String role, String displayName) {
    jdbc.update(
        "INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)",
        username,
        PasswordHasher.hash(password),
        role,
        displayName);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private StudentCourseSeed seedStudentWithCourse(String prefix) {
    long suffix = System.nanoTime();
    long departmentId = createDepartment("学生端学院-" + prefix + "-" + suffix);
    String teacherUsername = "teacher-" + prefix + "-" + suffix;
    String studentUsername = "student-" + prefix + "-" + suffix;
    String teacherName = "学生端老师-" + suffix;
    String courseName = "学生端课程-" + suffix;
    long teacherId = createTeacher(teacherUsername, "teacher123", teacherName, departmentId);
    long studentId = createStudent(studentUsername, "学生端学生", "S" + suffix, departmentId);
    long courseId = createCourse(courseName, "STU-" + prefix + "-" + suffix, departmentId);
    long assignmentId = createAssignment(courseId, teacherId);
    enroll(assignmentId, studentId);
    String startedAt = Instant.now().minusSeconds(60).toString();
    String endsAt = Instant.now().plusSeconds(600).toString();
    jdbc.update(
        "INSERT INTO attendance_sessions(course_id, teacher_id, started_at, ends_at, status, method) VALUES (?, ?, ?, ?, ?, ?)",
        courseId,
        teacherId,
        startedAt,
        endsAt,
        "OPEN",
        "QR");
    long sessionId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    return new StudentCourseSeed(studentUsername, teacherUsername, teacherName, courseName, courseId, sessionId);
  }

  private record StudentCourseSeed(
      String studentUsername,
      String teacherUsername,
      String teacherName,
      String courseName,
      long courseId,
      long sessionId) {}

  private String json(Object value) throws Exception {
    return mapper.writeValueAsString(value);
  }
}
