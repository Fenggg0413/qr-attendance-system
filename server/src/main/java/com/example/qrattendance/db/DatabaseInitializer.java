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
    cleanupOrphans();
    seed();
  }

  private void cleanupOrphans() {
    jdbc.execute("DELETE FROM course_enrollments WHERE student_id NOT IN (SELECT id FROM students)");
    jdbc.execute("DELETE FROM course_enrollments WHERE assignment_id NOT IN (SELECT id FROM course_assignments)");
    jdbc.execute("DELETE FROM course_assignments WHERE teacher_id NOT IN (SELECT id FROM teachers)");
    jdbc.execute("DELETE FROM course_assignments WHERE course_id NOT IN (SELECT id FROM courses)");
  }

  private void schema() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL, display_name TEXT NOT NULL)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS departments (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS classes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, grade TEXT)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS teachers (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), name TEXT NOT NULL, department TEXT, department_id INTEGER REFERENCES departments(id), phone TEXT, email TEXT)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), class_id INTEGER REFERENCES classes(id), department_id INTEGER REFERENCES departments(id), grade TEXT, name TEXT NOT NULL, student_no TEXT NOT NULL UNIQUE)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS courses (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT NOT NULL UNIQUE, class_id INTEGER REFERENCES classes(id), department_id INTEGER REFERENCES departments(id))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS course_assignments (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL REFERENCES courses(id), teacher_id INTEGER NOT NULL REFERENCES teachers(id), term TEXT, UNIQUE(course_id, teacher_id))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS course_terms (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL UNIQUE, label TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS course_schedules (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL UNIQUE REFERENCES courses(id) ON DELETE CASCADE, weekday TEXT, start_time TEXT, end_time TEXT, location TEXT)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS classrooms (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, building TEXT, capacity INTEGER)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS course_schedule_slots (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE, teacher_id INTEGER NOT NULL REFERENCES teachers(id), classroom_id INTEGER NOT NULL REFERENCES classrooms(id), weekday TEXT NOT NULL, period INTEGER NOT NULL, course_type TEXT NOT NULL, UNIQUE(course_id, weekday, period))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS course_enrollments (id INTEGER PRIMARY KEY AUTOINCREMENT, assignment_id INTEGER NOT NULL REFERENCES course_assignments(id) ON DELETE CASCADE, student_id INTEGER NOT NULL REFERENCES students(id), UNIQUE(assignment_id, student_id))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS attendance_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL REFERENCES courses(id), teacher_id INTEGER NOT NULL REFERENCES teachers(id), started_at TEXT NOT NULL, ends_at TEXT NOT NULL, status TEXT NOT NULL, method TEXT NOT NULL DEFAULT 'QR', schedule_slot_id INTEGER REFERENCES course_schedule_slots(id) ON DELETE SET NULL, period_end INTEGER, kind TEXT NOT NULL DEFAULT 'SCHEDULED', makeup_reason TEXT)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS attendance_records (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES attendance_sessions(id), student_id INTEGER NOT NULL REFERENCES students(id), status TEXT NOT NULL, checked_in_at TEXT, source TEXT NOT NULL, UNIQUE(session_id, student_id))");
    jdbc.execute("CREATE TABLE IF NOT EXISTS leave_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES attendance_sessions(id), student_id INTEGER NOT NULL REFERENCES students(id), reason TEXT NOT NULL, status TEXT NOT NULL, reviewer_id INTEGER REFERENCES users(id), reviewed_at TEXT, created_at TEXT NOT NULL)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS student_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, teacher_id INTEGER NOT NULL REFERENCES teachers(id), student_id INTEGER NOT NULL REFERENCES students(id), note TEXT NOT NULL DEFAULT '', UNIQUE(teacher_id, student_id))");
    addColumnIfMissing("teachers", "phone", "TEXT");
    addColumnIfMissing("teachers", "email", "TEXT");
    addColumnIfMissing("teachers", "department_id", "INTEGER REFERENCES departments(id)");
    addColumnIfMissing("students", "department_id", "INTEGER REFERENCES departments(id)");
    addColumnIfMissing("students", "grade", "TEXT");
    addColumnIfMissing("courses", "department_id", "INTEGER REFERENCES departments(id)");
    addColumnIfMissing("classrooms", "building", "TEXT");
    addColumnIfMissing("classrooms", "capacity", "INTEGER");
    migrateNullableClassBindings();
    addColumnIfMissing("course_assignments", "term", "TEXT");
    addColumnIfMissing("attendance_sessions", "method", "TEXT NOT NULL DEFAULT 'QR'");
    addColumnIfMissing("attendance_sessions", "schedule_slot_id", "INTEGER REFERENCES course_schedule_slots(id) ON DELETE SET NULL");
    addColumnIfMissing("attendance_sessions", "period_end", "INTEGER");
    addColumnIfMissing("attendance_sessions", "kind", "TEXT NOT NULL DEFAULT 'SCHEDULED'");
    addColumnIfMissing("attendance_sessions", "makeup_reason", "TEXT");
    migrateAttendanceSessionsScheduleSlotForeignKey();
    ensureDefaultDepartment();
    ensureDefaultTerms();
    backfillStudentGrades();
    backfillDepartments();
  }

  private void seed() {
    if (count("users") > 0) return;
    long admin = user("admin", "admin123", "ADMIN", "系统管理员");
    if (admin == 0) throw new IllegalStateException("管理员账号初始化失败");
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

  private void migrateNullableClassBindings() {
    boolean foreignKeysEnabled = foreignKeysEnabled();
    jdbc.execute("PRAGMA foreign_keys = OFF");
    try {
      if (isNotNullColumn("students", "class_id")) {
        jdbc.execute("CREATE TABLE students_new (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), class_id INTEGER REFERENCES classes(id), department_id INTEGER REFERENCES departments(id), grade TEXT, name TEXT NOT NULL, student_no TEXT NOT NULL UNIQUE)");
        jdbc.execute("INSERT INTO students_new(id, user_id, class_id, department_id, grade, name, student_no) SELECT id, user_id, class_id, department_id, grade, name, student_no FROM students");
        jdbc.execute("DROP TABLE students");
        jdbc.execute("ALTER TABLE students_new RENAME TO students");
      }
      if (isNotNullColumn("courses", "class_id")) {
        jdbc.execute("CREATE TABLE courses_new (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT NOT NULL UNIQUE, class_id INTEGER REFERENCES classes(id), department_id INTEGER REFERENCES departments(id))");
        jdbc.execute("INSERT INTO courses_new(id, name, code, class_id, department_id) SELECT id, name, code, class_id, department_id FROM courses");
        jdbc.execute("DROP TABLE courses");
        jdbc.execute("ALTER TABLE courses_new RENAME TO courses");
      }
    } finally {
      jdbc.execute("PRAGMA foreign_keys = " + (foreignKeysEnabled ? "ON" : "OFF"));
    }
  }

  private void migrateAttendanceSessionsScheduleSlotForeignKey() {
    if (scheduleSlotForeignKeySetsNullOnDelete()) {
      return;
    }

    boolean foreignKeysEnabled = foreignKeysEnabled();
    jdbc.execute("PRAGMA foreign_keys = OFF");
    try {
      jdbc.execute("CREATE TABLE attendance_sessions_new (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id INTEGER NOT NULL REFERENCES courses(id), teacher_id INTEGER NOT NULL REFERENCES teachers(id), started_at TEXT NOT NULL, ends_at TEXT NOT NULL, status TEXT NOT NULL, method TEXT NOT NULL DEFAULT 'QR', schedule_slot_id INTEGER REFERENCES course_schedule_slots(id) ON DELETE SET NULL, period_end INTEGER, kind TEXT NOT NULL DEFAULT 'SCHEDULED', makeup_reason TEXT)");
      jdbc.execute(
          """
          INSERT INTO attendance_sessions_new(id, course_id, teacher_id, started_at, ends_at, status, method, schedule_slot_id, period_end, kind, makeup_reason)
          SELECT id, course_id, teacher_id, started_at, ends_at, status, method, schedule_slot_id, period_end, kind, makeup_reason
          FROM attendance_sessions
          """);
      jdbc.execute("DROP TABLE attendance_sessions");
      jdbc.execute("ALTER TABLE attendance_sessions_new RENAME TO attendance_sessions");
    } finally {
      jdbc.execute("PRAGMA foreign_keys = " + (foreignKeysEnabled ? "ON" : "OFF"));
    }
  }

  private boolean scheduleSlotForeignKeySetsNullOnDelete() {
    return jdbc.queryForList("PRAGMA foreign_key_list(attendance_sessions)").stream()
        .anyMatch(
            row ->
                "schedule_slot_id".equalsIgnoreCase(String.valueOf(row.get("from")))
                    && "SET NULL".equalsIgnoreCase(String.valueOf(row.get("on_delete"))));
  }

  private boolean isNotNullColumn(String table, String column) {
    return jdbc.queryForList("PRAGMA table_info(" + table + ")").stream()
        .filter(row -> column.equalsIgnoreCase(String.valueOf(row.get("name"))))
        .map(row -> ((Number) row.get("notnull")).intValue() == 1)
        .findFirst()
        .orElse(false);
  }

  private boolean foreignKeysEnabled() {
    Integer enabled = jdbc.queryForObject("PRAGMA foreign_keys", Integer.class);
    return enabled != null && enabled == 1;
  }

  private void ensureDefaultDepartment() {
    if (count("departments") == 0) {
      insert("INSERT INTO departments(name) VALUES (?)", "计算机学院");
    }
  }

  private void ensureDefaultTerms() {
    String[] terms = {
      "2025-2026学年 秋季学期",
      "2025-2026学年 春季学期",
      "2026-2027学年 秋季学期",
      "2026-2027学年 春季学期"
    };
    for (int index = 0; index < terms.length; index++) {
      jdbc.update(
          "INSERT OR IGNORE INTO course_terms(value, label, sort_order) VALUES (?, ?, ?)",
          terms[index],
          terms[index],
          index + 1);
    }
  }

  private void backfillDepartments() {
    Long departmentId = defaultDepartmentId();
    jdbc.update("UPDATE teachers SET department_id = ? WHERE department_id IS NULL", departmentId);
    jdbc.update("UPDATE students SET department_id = ? WHERE department_id IS NULL", departmentId);
    jdbc.update("UPDATE courses SET department_id = ? WHERE department_id IS NULL", departmentId);
  }

  private void backfillStudentGrades() {
    jdbc.update(
        """
        UPDATE students
        SET grade = (
          SELECT classes.grade FROM classes WHERE classes.id = students.class_id
        )
        WHERE (grade IS NULL OR grade = '')
          AND class_id IS NOT NULL
        """);
  }

  private long defaultDepartmentId() {
    return jdbc.queryForObject("SELECT id FROM departments ORDER BY id LIMIT 1", Long.class);
  }
}
