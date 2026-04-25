package com.example.qrattendance.db;

import com.example.qrattendance.auth.PasswordHasher;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {
  private final JdbcTemplate jdbc;

  public DatabaseInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  void init() throws Exception {
    Files.createDirectories(Path.of("data"));
    jdbc.execute("PRAGMA foreign_keys = ON");
    schema();
    seed();
  }

  private void schema() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL, display_name TEXT NOT NULL)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS classes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, grade TEXT)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS teachers (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), name TEXT NOT NULL, department TEXT, phone TEXT, email TEXT)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), class_id INTEGER NOT NULL REFERENCES classes(id), name TEXT NOT NULL, student_no TEXT NOT NULL UNIQUE)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS courses (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT NOT NULL UNIQUE, class_id INTEGER NOT NULL REFERENCES classes(id))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS course_assignments (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL REFERENCES courses(id), teacher_id INTEGER NOT NULL REFERENCES teachers(id), term TEXT, UNIQUE(course_id, teacher_id))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS course_enrollments (id INTEGER PRIMARY KEY AUTOINCREMENT, assignment_id INTEGER NOT NULL REFERENCES course_assignments(id) ON DELETE CASCADE, student_id INTEGER NOT NULL REFERENCES students(id), UNIQUE(assignment_id, student_id))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS attendance_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL REFERENCES courses(id), teacher_id INTEGER NOT NULL REFERENCES teachers(id), started_at TEXT NOT NULL, ends_at TEXT NOT NULL, status TEXT NOT NULL, method TEXT NOT NULL DEFAULT 'QR')");
    jdbc.execute("CREATE TABLE IF NOT EXISTS attendance_records (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES attendance_sessions(id), student_id INTEGER NOT NULL REFERENCES students(id), status TEXT NOT NULL, checked_in_at TEXT, source TEXT NOT NULL, UNIQUE(session_id, student_id))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS leave_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES attendance_sessions(id), student_id INTEGER NOT NULL REFERENCES students(id), reason TEXT NOT NULL, status TEXT NOT NULL, reviewer_id INTEGER REFERENCES users(id), reviewed_at TEXT, created_at TEXT NOT NULL)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS student_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, teacher_id INTEGER NOT NULL REFERENCES teachers(id), student_id INTEGER NOT NULL REFERENCES students(id), note TEXT NOT NULL DEFAULT '', UNIQUE(teacher_id, student_id))");
    addColumnIfMissing("teachers", "phone", "TEXT");
    addColumnIfMissing("teachers", "email", "TEXT");
    addColumnIfMissing("course_assignments", "term", "TEXT");
    addColumnIfMissing("attendance_sessions", "method", "TEXT NOT NULL DEFAULT 'QR'");
  }

  private void seed() {
    if (count("users") > 0) return;
    long admin = user("admin", "admin123", "ADMIN", "系统管理员");
    long teacherUser = user("teacher1", "teacher123", "TEACHER", "张老师");
    long studentUser = user("student1", "student123", "STUDENT", "李同学");
    long classId = insert("INSERT INTO classes(name, grade) VALUES (?, ?)", "软件 2301", "2023");
    long teacherId = insert("INSERT INTO teachers(user_id, name, department) VALUES (?, ?, ?)", teacherUser, "张老师", "计算机学院");
    long studentId = insert("INSERT INTO students(user_id, class_id, name, student_no) VALUES (?, ?, ?, ?)", studentUser, classId, "李同学", "20230001");
    long courseId = insert("INSERT INTO courses(name, code, class_id) VALUES (?, ?, ?)", "Java Web 开发", "JAVA-WEB-01", classId);
    insert("INSERT INTO course_assignments(course_id, teacher_id) VALUES (?, ?)", courseId, teacherId);
    admin += studentId; // 避免演示数据变量被误删。
  }

  private long user(String username, String password, String role, String displayName) {
    return insert(
        "INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)",
        username, PasswordHasher.hash(password), role, displayName);
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  private long insert(String sql, Object... args) {
    jdbc.update(sql, args);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private void addColumnIfMissing(String table, String column, String definition) {
    boolean exists =
        jdbc.queryForList("PRAGMA table_info(" + table + ")").stream()
            .anyMatch(row -> column.equalsIgnoreCase(String.valueOf(row.get("name"))));
    if (!exists) {
      jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }
  }
}
