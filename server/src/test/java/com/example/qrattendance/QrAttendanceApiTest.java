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
  void adminCanManageEnrollmentsAndSessionStatsUseExplicitRoster() throws Exception {
    long suffix = System.nanoTime();
    String adminToken = login("admin", "admin123");
    long classId = createClass("测试选课班级-" + suffix, "2026");
    long teacherId = createTeacher("teacher-enroll-" + suffix, "teacher123", "选课老师", "计算机学院");
    long studentId = createStudent("student-enroll-" + suffix, "选课学生", "E" + suffix, classId);
    createStudent("student-not-enrolled-" + suffix, "未选学生", "N" + suffix, classId);
    long courseId = createCourse("选课测试课程-" + suffix, "ENROLL-" + suffix, classId);
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
    createTeacher("teacher-profile", "oldpass123", "赵老师", "数学学院");
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

  private long createCourse(String name, String code, long classId) {
    jdbc.update("INSERT INTO courses(name, code, class_id) VALUES (?, ?, ?)", name, code, classId);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createAssignment(long courseId, long teacherId) {
    jdbc.update("INSERT INTO course_assignments(course_id, teacher_id) VALUES (?, ?)", courseId, teacherId);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createStudent(String username, String name, String studentNo, long classId) {
    long userId = insertUser(username, "student123", "STUDENT", name);
    jdbc.update("INSERT INTO students(user_id, class_id, name, student_no) VALUES (?, ?, ?, ?)", userId, classId, name, studentNo);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private long createTeacher(String username, String password, String name, String department) {
    long userId = insertUser(username, password, "TEACHER", name);
    jdbc.update("INSERT INTO teachers(user_id, name, department) VALUES (?, ?, ?)", userId, name, department);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
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
