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

    login("teacher-reset-" + suffix, "teacher123");
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
        .andExpect(jsonPath("$.student_count", is(2)));

    JsonNode created =
        mapper.readTree(
            mvc.perform(
                    post("/api/teacher/courses/1/attendance-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("durationMinutes", 5, "method", "CODE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("CODE")))
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
  void studentCheckInRejectsOldQrAndDeduplicatesCurrentQr() throws Exception {
    String teacherToken = login("teacher1", "teacher123");
    long sessionId = createSession(teacherToken);
    String studentToken = login("student1", "student123");
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
    String studentToken = login("student1", "student123");
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
    JsonNode node =
        mapper.readTree(
            mvc.perform(
                    post("/api/teacher/courses/" + courseId + "/attendance-sessions")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("durationMinutes", 5))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return node.get("id").asLong();
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

  private long insertUser(String username, String password, String role, String displayName) {
    jdbc.update(
        "INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)",
        username,
        PasswordHasher.hash(password),
        role,
        displayName);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private String json(Object value) throws Exception {
    return mapper.writeValueAsString(value);
  }
}
