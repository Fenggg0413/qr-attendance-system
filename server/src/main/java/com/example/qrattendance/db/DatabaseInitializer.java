package com.example.qrattendance.db;

import com.example.qrattendance.auth.PasswordHasher;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 数据库初始化器：应用启动时通过 @PostConstruct 自动执行
// 职责：① 建表与列迁移 ② 清理孤立外键 ③ 初始化默认管理员
// 不使用 Flyway / Liquibase，靠手写 SQL + CREATE TABLE IF NOT EXISTS + addColumnIfMissing 实现简单迁移
@Component
public class DatabaseInitializer {
  private final JdbcTemplate jdbc;

  public DatabaseInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // 初始化入口：创建 data 目录 → 启用外键 → 建表迁移 → 清理孤立外键 → 播种管理员
  // 三步顺序不可调换：schema 必须先于 cleanupOrphans（依赖表存在），cleanupOrphans 在 seed 前避免误删默认数据
  @PostConstruct
  void init() throws Exception {
    // SQLite 数据库文件位于 data/qrattendance.db；目录不存在则创建
    Files.createDirectories(Path.of("data"));
    // SQLite 默认外键约束关闭，必须显式打开才会触发 REFERENCES 检查与 ON DELETE 行为
    jdbc.execute("PRAGMA foreign_keys = ON");
    schema();
    cleanupOrphans();
    seed();
  }

  // 清理因历史 bug 残留的孤立外键记录（删除指向不存在主键的行）
  private void cleanupOrphans() {
    jdbc.execute("DELETE FROM course_enrollments WHERE student_id NOT IN (SELECT id FROM students)");
    jdbc.execute("DELETE FROM course_enrollments WHERE assignment_id NOT IN (SELECT id FROM course_assignments)");
    jdbc.execute("DELETE FROM course_assignments WHERE teacher_id NOT IN (SELECT id FROM teachers)");
    jdbc.execute("DELETE FROM course_assignments WHERE course_id NOT IN (SELECT id FROM courses)");
  }

  // 建表 + 列迁移 + 默认数据回填的总流程
  // CREATE TABLE IF NOT EXISTS 保证幂等；addColumnIfMissing 处理增量字段；两个 migrateXxx 处理结构性变更
  private void schema() {
    // 核心账号表：所有角色（管理员/教师/学生）共用 users 表，靠 role 字段区分
    jdbc.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL, display_name TEXT NOT NULL)");
    // 院系字典
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
    // 性能索引：缺勤预警/学生考勤统计查询会频繁按 (status, student_id) 过滤
    jdbc.execute("CREATE INDEX IF NOT EXISTS idx_attendance_status_student ON attendance_records(status, student_id)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS leave_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES attendance_sessions(id), student_id INTEGER NOT NULL REFERENCES students(id), reason TEXT NOT NULL, status TEXT NOT NULL, reviewer_id INTEGER REFERENCES users(id), reviewed_at TEXT, created_at TEXT NOT NULL)");
    jdbc.execute("CREATE TABLE IF NOT EXISTS student_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, teacher_id INTEGER NOT NULL REFERENCES teachers(id), student_id INTEGER NOT NULL REFERENCES students(id), note TEXT NOT NULL DEFAULT '', UNIQUE(teacher_id, student_id))");
    // —— 以下为增量列迁移：旧版本数据库追加新增字段，幂等 ——
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
    // 默认数据回填：保证字典表至少有一条记录，老数据补齐 department_id / grade
    ensureDefaultDepartment();
    ensureDefaultTerms();
    backfillStudentGrades();
    backfillDepartments();
  }

  // 首次启动播种：users 表为空时创建默认管理员（admin / admin123）
  private void seed() {
    if (count("users") > 0) return;
    long admin = user("admin", "admin123", "ADMIN", "系统管理员");
    if (admin == 0) throw new IllegalStateException("管理员账号初始化失败");
  }

  // 工具方法：插入 users 行并返回新行 id（密码经 SHA-256 哈希后存储）
  private long user(String username, String password, String role, String displayName) {
    return insert(
        "INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)",
        username, PasswordHasher.hash(password), role, displayName);
  }

  // 工具方法：统计指定表的行数
  private long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  // 工具方法：执行 INSERT 并通过 last_insert_rowid() 取自增主键
  private long insert(String sql, Object... args) {
    jdbc.update(sql, args);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  // 工具方法：若列不存在则 ALTER TABLE 追加；用 PRAGMA table_info 检测列存在性
  private void addColumnIfMissing(String table, String column, String definition) {
    boolean exists =
        jdbc.queryForList("PRAGMA table_info(" + table + ")").stream()
            .anyMatch(row -> column.equalsIgnoreCase(String.valueOf(row.get("name"))));
    if (!exists) {
      jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }
  }

  // 把 students.class_id / courses.class_id 从 NOT NULL 改成可空
  // 背景：SQLite 不支持 ALTER COLUMN 修改约束，只能"建新表 → 拷数据 → 删旧表 → 改名"
  // 迁移期间必须临时关闭外键，否则中间状态下外键检查会失败
  private void migrateNullableClassBindings() {
    boolean foreignKeysEnabled = foreignKeysEnabled();
    // 关键：迁移期间关闭外键，结束后恢复
    jdbc.execute("PRAGMA foreign_keys = OFF");
    try {
      // 若 students.class_id 仍是 NOT NULL，则重建表为可空
      if (isNotNullColumn("students", "class_id")) {
        jdbc.execute("CREATE TABLE students_new (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL UNIQUE REFERENCES users(id), class_id INTEGER REFERENCES classes(id), department_id INTEGER REFERENCES departments(id), grade TEXT, name TEXT NOT NULL, student_no TEXT NOT NULL UNIQUE)");
        jdbc.execute("INSERT INTO students_new(id, user_id, class_id, department_id, grade, name, student_no) SELECT id, user_id, class_id, department_id, grade, name, student_no FROM students");
        jdbc.execute("DROP TABLE students");
        jdbc.execute("ALTER TABLE students_new RENAME TO students");
      }
      // courses.class_id 同样处理
      if (isNotNullColumn("courses", "class_id")) {
        jdbc.execute("CREATE TABLE courses_new (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT NOT NULL UNIQUE, class_id INTEGER REFERENCES classes(id), department_id INTEGER REFERENCES departments(id))");
        jdbc.execute("INSERT INTO courses_new(id, name, code, class_id, department_id) SELECT id, name, code, class_id, department_id FROM courses");
        jdbc.execute("DROP TABLE courses");
        jdbc.execute("ALTER TABLE courses_new RENAME TO courses");
      }
    } finally {
      // 恢复外键约束到迁移前的状态（保持幂等性）
      jdbc.execute("PRAGMA foreign_keys = " + (foreignKeysEnabled ? "ON" : "OFF"));
    }
  }

  // 把 attendance_sessions.schedule_slot_id 的外键改为 ON DELETE SET NULL
  // 目的：删除排课槽时，历史考勤会话不被级联删除，仅把槽引用置空（保留考勤记录）
  // 同样因 SQLite 不能 ALTER 约束，只能重建表
  private void migrateAttendanceSessionsScheduleSlotForeignKey() {
    // 幂等：外键已为 SET NULL 直接返回，不重复迁移
    if (scheduleSlotForeignKeySetsNullOnDelete()) {
      return;
    }

    boolean foreignKeysEnabled = foreignKeysEnabled();
    // 关键：重建表期间外键须关闭，否则数据迁移过程中外键检查会拒绝
    jdbc.execute("PRAGMA foreign_keys = OFF");
    try {
      // 新表定义中 schedule_slot_id 改成 ON DELETE SET NULL
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

  // 检测 attendance_sessions.schedule_slot_id 的外键是否为 ON DELETE SET NULL（用于迁移幂等判断）
  private boolean scheduleSlotForeignKeySetsNullOnDelete() {
    return jdbc.queryForList("PRAGMA foreign_key_list(attendance_sessions)").stream()
        .anyMatch(
            row ->
                "schedule_slot_id".equalsIgnoreCase(String.valueOf(row.get("from")))
                    && "SET NULL".equalsIgnoreCase(String.valueOf(row.get("on_delete"))));
  }

  // 检测指定表的指定列是否带 NOT NULL 约束（通过 PRAGMA table_info 的 notnull 字段）
  private boolean isNotNullColumn(String table, String column) {
    return jdbc.queryForList("PRAGMA table_info(" + table + ")").stream()
        .filter(row -> column.equalsIgnoreCase(String.valueOf(row.get("name"))))
        .map(row -> ((Number) row.get("notnull")).intValue() == 1)
        .findFirst()
        .orElse(false);
  }

  // 查询 PRAGMA foreign_keys 当前状态（1=开启，0=关闭）
  private boolean foreignKeysEnabled() {
    Integer enabled = jdbc.queryForObject("PRAGMA foreign_keys", Integer.class);
    return enabled != null && enabled == 1;
  }

  // 若 departments 表为空，插入默认院系"计算机学院"（避免 teachers/students 没有可关联的院系）
  private void ensureDefaultDepartment() {
    if (count("departments") == 0) {
      insert("INSERT INTO departments(name) VALUES (?)", "计算机学院");
    }
  }

  // 初始化 4 个默认学期（25-26、26-27 学年各两学期），用于课程的 term 选项
  // INSERT OR IGNORE：已有同 value 的行跳过，保证幂等
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

  // 历史数据回填：把 teachers/students/courses 中 department_id 为 NULL 的行指向默认院系
  private void backfillDepartments() {
    Long departmentId = defaultDepartmentId();
    jdbc.update("UPDATE teachers SET department_id = ? WHERE department_id IS NULL", departmentId);
    jdbc.update("UPDATE students SET department_id = ? WHERE department_id IS NULL", departmentId);
    jdbc.update("UPDATE courses SET department_id = ? WHERE department_id IS NULL", departmentId);
  }

  // 历史数据回填：students.grade 为空时，从所属班级 classes.grade 反推填入
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

  // 取默认院系 id（按 id 升序首条，通常就是 ensureDefaultDepartment 创建的那条）
  private long defaultDepartmentId() {
    return jdbc.queryForObject("SELECT id FROM departments ORDER BY id LIMIT 1", Long.class);
  }
}
