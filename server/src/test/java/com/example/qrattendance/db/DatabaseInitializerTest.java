package com.example.qrattendance.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.qrattendance.auth.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class DatabaseInitializerTest {
  @Test
  void initMigratesLegacyClassBindingsToNullableColumns() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
    jdbc.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL, display_name TEXT NOT NULL)");
    jdbc.execute("CREATE TABLE departments (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
    jdbc.execute("CREATE TABLE classes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, grade TEXT)");
    jdbc.execute("CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), class_id INTEGER NOT NULL REFERENCES classes(id), name TEXT NOT NULL, student_no TEXT NOT NULL UNIQUE, department_id INTEGER REFERENCES departments(id))");
    jdbc.execute("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT NOT NULL UNIQUE, class_id INTEGER NOT NULL REFERENCES classes(id), department_id INTEGER REFERENCES departments(id))");
    jdbc.update("INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)", "seed-admin", "hash", "ADMIN", "管理员");
    jdbc.update("INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)", "legacy-student", "hash", "STUDENT", "旧学生");
    jdbc.update("INSERT INTO departments(name) VALUES (?)", "计算机学院");
    jdbc.update("INSERT INTO classes(name, grade) VALUES (?, ?)", "旧班级", "2026");
    jdbc.update("INSERT INTO students(user_id, class_id, department_id, name, student_no) VALUES (?, ?, ?, ?, ?)", 2, 1, 1, "旧学生", "LEGACY-001");

    new DatabaseInitializer(jdbc).init();

    assertEquals(0, notNullFlag(jdbc, "students", "class_id"));
    assertEquals(0, notNullFlag(jdbc, "courses", "class_id"));
    assertEquals("2026", jdbc.queryForObject("SELECT grade FROM students WHERE student_no = ?", String.class, "LEGACY-001"));
  }

  @Test
  void initPreservesStudentGradeWhenRebuildingLegacyStudentsTable() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
    jdbc.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL, display_name TEXT NOT NULL)");
    jdbc.execute("CREATE TABLE departments (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
    jdbc.execute("CREATE TABLE classes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, grade TEXT)");
    jdbc.execute("CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), class_id INTEGER NOT NULL REFERENCES classes(id), name TEXT NOT NULL, student_no TEXT NOT NULL UNIQUE, department_id INTEGER REFERENCES departments(id), grade TEXT)");
    jdbc.execute("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT NOT NULL UNIQUE, class_id INTEGER NOT NULL REFERENCES classes(id), department_id INTEGER REFERENCES departments(id))");
    jdbc.update("INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)", "seed-admin", "hash", "ADMIN", "管理员");
    jdbc.update("INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)", "legacy-student", "hash", "STUDENT", "旧学生");
    jdbc.update("INSERT INTO departments(name) VALUES (?)", "计算机学院");
    jdbc.update("INSERT INTO classes(name, grade) VALUES (?, ?)", "旧班级", "2026");
    jdbc.update("INSERT INTO students(user_id, class_id, department_id, name, student_no, grade) VALUES (?, ?, ?, ?, ?, ?)", 2, 1, 1, "旧学生", "LEGACY-KEEP", "2022");

    new DatabaseInitializer(jdbc).init();

    assertEquals(0, notNullFlag(jdbc, "students", "class_id"));
    assertEquals("2022", jdbc.queryForObject("SELECT grade FROM students WHERE student_no = ?", String.class, "LEGACY-KEEP"));
  }

  @Test
  void seedCreatesRealStudentAccountAndScheduleWithoutStudent1() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));

    new DatabaseInitializer(jdbc).init();

    assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM users WHERE username = 'student1'"));
    assertEquals(
        1,
        count(
            jdbc,
            "SELECT COUNT(*) FROM users u JOIN students s ON s.user_id = u.id WHERE u.username = 'B22042101' AND u.password_hash = ? AND s.student_no = 'B22042101'",
            PasswordHasher.hash("123456")));
    assertEquals(
        6,
        count(
            jdbc,
            """
            SELECT COUNT(*)
            FROM users u
            JOIN students s ON s.user_id = u.id
            JOIN course_enrollments ce ON ce.student_id = s.id
            JOIN course_assignments ca ON ca.id = ce.assignment_id
            JOIN course_schedule_slots css ON css.course_id = ca.course_id AND css.teacher_id = ca.teacher_id
            WHERE u.username = 'B22042101' AND css.weekday IN ('周一', '周三', '周五')
            """));
    assertTrue(
        jdbc.queryForList(
                "SELECT DISTINCT weekday FROM course_schedule_slots ORDER BY weekday").stream()
            .map(row -> String.valueOf(row.get("weekday")))
            .toList()
            .containsAll(java.util.List.of("周一", "周三", "周五")));
  }

  @Test
  void initMigratesLegacyScheduleSlotForeignKeyToSetNullOnDelete() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
    jdbc.execute("PRAGMA foreign_keys = ON");
    jdbc.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL, display_name TEXT NOT NULL)");
    jdbc.execute("CREATE TABLE departments (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
    jdbc.execute("CREATE TABLE classes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, grade TEXT)");
    jdbc.execute("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT NOT NULL UNIQUE, class_id INTEGER REFERENCES classes(id), department_id INTEGER REFERENCES departments(id))");
    jdbc.execute("CREATE TABLE teachers (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), name TEXT NOT NULL, department TEXT, department_id INTEGER REFERENCES departments(id), phone TEXT, email TEXT)");
    jdbc.execute("CREATE TABLE classrooms (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, building TEXT, capacity INTEGER)");
    jdbc.execute("CREATE TABLE course_schedule_slots (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE, teacher_id INTEGER NOT NULL REFERENCES teachers(id), classroom_id INTEGER NOT NULL REFERENCES classrooms(id), weekday TEXT NOT NULL, period INTEGER NOT NULL, course_type TEXT NOT NULL, UNIQUE(course_id, weekday, period))");
    jdbc.execute("CREATE TABLE attendance_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL REFERENCES courses(id), teacher_id INTEGER NOT NULL REFERENCES teachers(id), started_at TEXT NOT NULL, ends_at TEXT NOT NULL, status TEXT NOT NULL, method TEXT NOT NULL DEFAULT 'QR', schedule_slot_id INTEGER REFERENCES course_schedule_slots(id), period_end INTEGER, kind TEXT NOT NULL DEFAULT 'SCHEDULED', makeup_reason TEXT)");
    jdbc.update("INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)", "seed-admin", "hash", "ADMIN", "管理员");
    jdbc.update("INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)", "teacher", "hash", "TEACHER", "教师");
    jdbc.update("INSERT INTO courses(name, code) VALUES (?, ?)", "旧课程", "LEGACY-COURSE");
    jdbc.update("INSERT INTO teachers(user_id, name) VALUES (?, ?)", 2, "教师");
    jdbc.update("INSERT INTO classrooms(name) VALUES (?)", "旧教室");
    jdbc.update("INSERT INTO course_schedule_slots(course_id, teacher_id, classroom_id, weekday, period, course_type) VALUES (?, ?, ?, ?, ?, ?)", 1, 1, 1, "周一", 1, "LECTURE");
    jdbc.update("INSERT INTO attendance_sessions(course_id, teacher_id, started_at, ends_at, status, schedule_slot_id) VALUES (?, ?, ?, ?, ?, ?)", 1, 1, "2026-01-01T08:00:00", "2026-01-01T08:45:00", "OPEN", 1);

    new DatabaseInitializer(jdbc).init();

    assertEquals("SET NULL", scheduleSlotForeignKeyDeleteAction(jdbc));
    jdbc.update("DELETE FROM course_schedule_slots WHERE id = ?", 1);
    assertEquals(null, jdbc.queryForObject("SELECT schedule_slot_id FROM attendance_sessions WHERE id = ?", Object.class, 1));
  }

  private int notNullFlag(JdbcTemplate jdbc, String table, String column) {
    return jdbc.queryForList("PRAGMA table_info(" + table + ")").stream()
        .filter(row -> column.equals(row.get("name")))
        .map(row -> ((Number) row.get("notnull")).intValue())
        .findFirst()
        .orElseThrow();
  }

  private int count(JdbcTemplate jdbc, String sql, Object... args) {
    Integer value = jdbc.queryForObject(sql, Integer.class, args);
    return value == null ? 0 : value;
  }

  private String scheduleSlotForeignKeyDeleteAction(JdbcTemplate jdbc) {
    return jdbc.queryForList("PRAGMA foreign_key_list(attendance_sessions)").stream()
        .filter(row -> "schedule_slot_id".equals(row.get("from")))
        .map(row -> String.valueOf(row.get("on_delete")))
        .findFirst()
        .orElseThrow();
  }
}
