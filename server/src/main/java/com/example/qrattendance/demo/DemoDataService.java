package com.example.qrattendance.demo;

import com.example.qrattendance.auth.CurrentUser;
import com.example.qrattendance.auth.PasswordHasher;
import com.example.qrattendance.schedule.SchedulePeriods;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DemoDataService {
  private static final String DEFAULT_PASSWORD = "123456";
  private static final String TERM = "2025-2026学年 春季学期";
  private static final ZoneId ZONE = SchedulePeriods.ZONE;
  private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五"};
  private static final int[] SLOT_STARTS = {1, 3, 6, 8};
  private static final DepartmentSeed[] DEPARTMENTS = {
    new DepartmentSeed("计算机学院", "0421", "计算机科学与技术"),
    new DepartmentSeed("软件学院", "0422", "软件工程"),
    new DepartmentSeed("人工智能学院", "0423", "人工智能"),
    new DepartmentSeed("信息工程学院", "0424", "电子信息工程"),
    new DepartmentSeed("网络空间安全学院", "0425", "网络空间安全"),
    new DepartmentSeed("数据科学学院", "0426", "数据科学与大数据技术")
  };
  private static final String[] COURSE_NAMES = {
    "程序设计基础",
    "数据结构",
    "计算机组成原理",
    "操作系统",
    "计算机网络",
    "数据库系统",
    "软件工程",
    "Web 应用开发",
    "移动应用开发",
    "人工智能导论",
    "机器学习",
    "深度学习实践",
    "信息安全基础",
    "网络攻防技术",
    "数据挖掘",
    "大数据平台技术",
    "云计算基础",
    "算法设计与分析",
    "Linux 系统管理",
    "项目综合实训"
  };
  private static final String[] STUDENT_SURNAMES = {
    "李", "王", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "胡", "朱", "高", "林"
  };
  private static final String[] STUDENT_GIVEN_NAMES = {
    "一鸣", "雨桐", "子涵", "思远", "佳怡", "明轩", "梓萱", "浩然", "诗涵", "俊杰", "欣怡", "博文", "嘉琪", "宇航", "若曦", "天佑"
  };
  private static final String[] TEACHER_SURNAMES = {
    "刘", "王", "陈", "赵", "周", "林", "何", "郭", "马", "罗", "梁", "宋"
  };
  private static final String[] TEACHER_GIVEN_NAMES = {
    "建国", "敏", "伟", "静", "强", "磊", "芳", "勇", "丽", "涛", "艳", "杰"
  };

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public DemoDataService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public Map<String, Object> reset(String preset, CurrentUser admin) {
    DemoPreset size = DemoPreset.from(preset);
    long started = System.nanoTime();
    return transactions.execute(
        status -> {
          resetBusinessData();
          DemoResult result = importCampus(size, admin);
          Map<String, Object> response = new LinkedHashMap<>();
          response.put("preset", size.name);
          response.put("departments", result.departments);
          response.put("classes", result.classes);
          response.put("classrooms", result.classrooms);
          response.put("teachers", result.teachers);
          response.put("students", result.students);
          response.put("courses", result.courses);
          response.put("enrollments", result.enrollments);
          response.put("attendanceSessions", result.attendanceSessions);
          response.put("attendanceRecords", result.attendanceRecords);
          response.put("leaveRequests", result.leaveRequests);
          response.put("sampleTeacher", result.sampleTeacher);
          response.put("sampleStudent", result.sampleStudent);
          response.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
          return response;
        });
  }

  private void resetBusinessData() {
    jdbc.update("DELETE FROM student_notes");
    jdbc.update("DELETE FROM leave_requests");
    jdbc.update("DELETE FROM attendance_records");
    jdbc.update("DELETE FROM attendance_sessions");
    jdbc.update("DELETE FROM course_enrollments");
    jdbc.update("DELETE FROM course_schedule_slots");
    jdbc.update("DELETE FROM course_schedules");
    jdbc.update("DELETE FROM course_assignments");
    jdbc.update("DELETE FROM courses");
    jdbc.update("DELETE FROM students");
    jdbc.update("DELETE FROM teachers");
    jdbc.update("DELETE FROM classes");
    jdbc.update("DELETE FROM classrooms");
    jdbc.update("DELETE FROM departments");
    jdbc.update("DELETE FROM users WHERE role <> 'ADMIN'");
  }

  private DemoResult importCampus(DemoPreset size, CurrentUser admin) {
    DemoResult result = new DemoResult();
    List<DepartmentData> departments = createDepartments(size, result);
    List<ClassData> classes = createClasses(size, departments, result);
    List<ClassroomData> classrooms = createClassrooms(size, result);
    List<TeacherData> teachers = createTeachers(size, departments, result);
    List<StudentData> students = createStudents(size, classes, result);
    List<CourseData> courses = createCourses(size, departments, classes, teachers, result);
    createSchedules(courses, classrooms);
    createEnrollments(courses, students, result);
    createAttendanceHistory(courses, admin, result);
    createTodaySessions(courses, result);
    result.sampleTeacher = Map.of("username", teachers.getFirst().username, "password", DEFAULT_PASSWORD, "name", teachers.getFirst().name);
    result.sampleStudent = Map.of("username", students.getFirst().studentNo, "password", DEFAULT_PASSWORD, "name", students.getFirst().name);
    return result;
  }

  private List<DepartmentData> createDepartments(DemoPreset size, DemoResult result) {
    List<DepartmentData> rows = new ArrayList<>();
    for (int index = 0; index < size.departments; index++) {
      DepartmentSeed seed = DEPARTMENTS[index];
      long id = insert("INSERT INTO departments(name) VALUES (?)", seed.name);
      rows.add(new DepartmentData(id, seed));
      result.departments++;
    }
    return rows;
  }

  private List<ClassData> createClasses(DemoPreset size, List<DepartmentData> departments, DemoResult result) {
    List<ClassData> rows = new ArrayList<>();
    int classIndex = 0;
    for (DepartmentData department : departments) {
      for (int index = 0; index < size.classesPerDepartment; index++) {
        int admissionYear = 2023 + (index % 3);
        String grade = String.valueOf(admissionYear);
        String name = department.seed.major + " " + (admissionYear % 100) + String.format("%02d", index + 1);
        long id = insert("INSERT INTO classes(name, grade) VALUES (?, ?)", name, grade);
        rows.add(new ClassData(id, department, grade, classIndex++));
        result.classes++;
      }
    }
    return rows;
  }

  private List<ClassroomData> createClassrooms(DemoPreset size, DemoResult result) {
    List<ClassroomData> rows = new ArrayList<>();
    String[] buildings = {"明德楼", "求是楼", "笃行楼", "创新楼", "实验中心", "信息楼"};
    int total = Math.max(8, size.departments * size.classroomsPerDepartment);
    for (int index = 0; index < total; index++) {
      String building = buildings[index % buildings.length];
      int floor = 1 + (index / buildings.length) % 5;
      int room = 1 + (index % 12);
      String name = building + "-" + floor + String.format("%02d", room);
      long id = insert("INSERT INTO classrooms(name, building, capacity) VALUES (?, ?, ?)", name, building, 60 + (index % 4) * 20);
      rows.add(new ClassroomData(id));
      result.classrooms++;
    }
    return rows;
  }

  private List<TeacherData> createTeachers(DemoPreset size, List<DepartmentData> departments, DemoResult result) {
    List<TeacherData> rows = new ArrayList<>();
    int teacherNumber = 1;
    for (DepartmentData department : departments) {
      for (int index = 0; index < size.teachersPerDepartment; index++) {
        int year = 2020 + ((teacherNumber - 1) % 5);
        String username = "t" + year + String.format("%03d", teacherNumber);
        String name = TEACHER_SURNAMES[(teacherNumber - 1) % TEACHER_SURNAMES.length] + TEACHER_GIVEN_NAMES[(teacherNumber + index) % TEACHER_GIVEN_NAMES.length];
        long userId = user(username, DEFAULT_PASSWORD, "TEACHER", name);
        long id =
            insert(
                "INSERT INTO teachers(user_id, name, department, department_id, phone, email) VALUES (?, ?, ?, ?, ?, ?)",
                userId,
                name,
                department.seed.name,
                department.id,
                "138" + String.format("%08d", teacherNumber),
                username + "@school.example");
        rows.add(new TeacherData(id, department, username, name));
        result.teachers++;
        teacherNumber++;
      }
    }
    return rows;
  }

  private List<StudentData> createStudents(DemoPreset size, List<ClassData> classes, DemoResult result) {
    List<StudentData> rows = new ArrayList<>();
    Map<String, Integer> sequenceByYearAndDepartment = new HashMap<>();
    int studentIndex = 0;
    for (ClassData classData : classes) {
      String yy = classData.grade.substring(2);
      String key = yy + classData.department.seed.code;
      int next = sequenceByYearAndDepartment.getOrDefault(key, 0) + 1;
      for (int index = 0; index < size.studentsPerClass; index++) {
        int sequence = next + index;
        String studentNo = "B" + yy + classData.department.seed.code + String.format("%02d", sequence);
        String name = STUDENT_SURNAMES[studentIndex % STUDENT_SURNAMES.length] + STUDENT_GIVEN_NAMES[(studentIndex / STUDENT_SURNAMES.length) % STUDENT_GIVEN_NAMES.length];
        long userId = user(studentNo, DEFAULT_PASSWORD, "STUDENT", name);
        long id =
            insert(
                "INSERT INTO students(user_id, class_id, department_id, grade, name, student_no) VALUES (?, ?, ?, ?, ?, ?)",
                userId,
                classData.id,
                classData.department.id,
                classData.grade,
                name,
                studentNo);
        rows.add(new StudentData(id, classData, studentNo, name));
        result.students++;
        studentIndex++;
      }
      sequenceByYearAndDepartment.put(key, next + size.studentsPerClass - 1);
    }
    return rows;
  }

  private List<CourseData> createCourses(
      DemoPreset size,
      List<DepartmentData> departments,
      List<ClassData> classes,
      List<TeacherData> teachers,
      DemoResult result) {
    List<CourseData> rows = new ArrayList<>();
    int courseNumber = 1;
    for (DepartmentData department : departments) {
      List<ClassData> departmentClasses = classes.stream().filter(item -> item.department == department).toList();
      List<TeacherData> departmentTeachers = teachers.stream().filter(item -> item.department == department).toList();
      for (int index = 0; index < size.coursesPerDepartment; index++) {
        ClassData classData = departmentClasses.get(index % departmentClasses.size());
        TeacherData teacher = departmentTeachers.get(index % departmentTeachers.size());
        String name = department.seed.major + COURSE_NAMES[index % COURSE_NAMES.length];
        String code = "C" + department.seed.code + "-" + String.format("%03d", index + 1);
        long courseId = insert("INSERT INTO courses(name, code, class_id, department_id) VALUES (?, ?, ?, ?)", name, code, classData.id, department.id);
        long assignmentId = insert("INSERT INTO course_assignments(course_id, teacher_id, term) VALUES (?, ?, ?)", courseId, teacher.id, TERM);
        rows.add(new CourseData(courseId, assignmentId, classData, teacher, code, courseNumber++));
        result.courses++;
      }
    }
    return rows;
  }

  private void createSchedules(List<CourseData> courses, List<ClassroomData> classrooms) {
    Map<String, Boolean> teacherBusy = new HashMap<>();
    Map<String, Boolean> classroomBusy = new HashMap<>();
    for (CourseData course : courses) {
      boolean placed = false;
      for (int weekdayOffset = 0; weekdayOffset < WEEKDAYS.length; weekdayOffset++) {
        String weekday = WEEKDAYS[(course.number + weekdayOffset - 1) % WEEKDAYS.length];
        for (int startOffset = 0; startOffset < SLOT_STARTS.length; startOffset++) {
          int start = SLOT_STARTS[(course.number + startOffset - 1) % SLOT_STARTS.length];
          for (ClassroomData classroom : classrooms) {
            String teacherKeyA = course.teacher.id + "-" + weekday + "-" + start;
            String teacherKeyB = course.teacher.id + "-" + weekday + "-" + (start + 1);
            String roomKeyA = classroom.id + "-" + weekday + "-" + start;
            String roomKeyB = classroom.id + "-" + weekday + "-" + (start + 1);
            if (teacherBusy.containsKey(teacherKeyA)
                || teacherBusy.containsKey(teacherKeyB)
                || classroomBusy.containsKey(roomKeyA)
                || classroomBusy.containsKey(roomKeyB)) {
              continue;
            }
            insertSlot(course, classroom, weekday, start);
            insertSlot(course, classroom, weekday, start + 1);
            teacherBusy.put(teacherKeyA, true);
            teacherBusy.put(teacherKeyB, true);
            classroomBusy.put(roomKeyA, true);
            classroomBusy.put(roomKeyB, true);
            placed = true;
            break;
          }
          if (placed) break;
        }
        if (placed) break;
      }
      if (!placed) {
        ClassroomData fallback = classrooms.get((course.number - 1) % classrooms.size());
        String weekday = WEEKDAYS[(course.number - 1) % WEEKDAYS.length];
        int start = SLOT_STARTS[(course.number - 1) % SLOT_STARTS.length];
        insertSlot(course, fallback, weekday, start);
        insertSlot(course, fallback, weekday, start + 1);
      }
    }
  }

  private void insertSlot(CourseData course, ClassroomData classroom, String weekday, int period) {
    long slotId =
        insert(
            "INSERT INTO course_schedule_slots(course_id, teacher_id, classroom_id, weekday, period, course_type) VALUES (?, ?, ?, ?, ?, ?)",
            course.id,
            course.teacher.id,
            classroom.id,
            weekday,
            period,
            period >= 6 ? "LAB" : "LECTURE");
    course.slotIds.add(slotId);
  }

  private void createEnrollments(List<CourseData> courses, List<StudentData> students, DemoResult result) {
    for (CourseData course : courses) {
      List<StudentData> classStudents = students.stream().filter(student -> student.classData == course.classData).toList();
      for (StudentData student : classStudents) {
        insert("INSERT INTO course_enrollments(assignment_id, student_id) VALUES (?, ?)", course.assignmentId, student.id);
        course.students.add(student);
        result.enrollments++;
      }
    }
  }

  private void createAttendanceHistory(List<CourseData> courses, CurrentUser admin, DemoResult result) {
    LocalDate today = LocalDate.now(ZONE);
    for (CourseData course : courses) {
      long slotId = course.slotIds.getFirst();
      Map<String, Object> slot = jdbc.queryForMap("SELECT weekday, period FROM course_schedule_slots WHERE id = ?", slotId);
      String weekday = String.valueOf(slot.get("weekday"));
      int period = ((Number) slot.get("period")).intValue();
      for (int week = 7; week >= 0; week--) {
        LocalDate date = alignToWeekday(today.minusWeeks(week), weekday);
        if (date.isAfter(today)) {
          date = date.minusWeeks(1);
        }
        Instant startedAt = ZonedDateTime.of(date, SchedulePeriods.startOf(period), ZONE).toInstant();
        Instant endsAt = ZonedDateTime.of(date, SchedulePeriods.endOf(period + 1), ZONE).toInstant();
        long sessionId =
            insert(
                "INSERT INTO attendance_sessions(course_id, teacher_id, started_at, ends_at, status, method, schedule_slot_id, period_end, kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                course.id,
                course.teacher.id,
                startedAt.toString(),
                endsAt.toString(),
                "CLOSED",
                "QR",
                slotId,
                period + 1,
                "SCHEDULED");
        result.attendanceSessions++;
        createAttendanceRecords(course, sessionId, startedAt, admin, result);
      }
    }
  }

  private void createAttendanceRecords(CourseData course, long sessionId, Instant startedAt, CurrentUser admin, DemoResult result) {
    int row = 0;
    for (StudentData student : course.students) {
      int bucket = Math.floorMod((int) (student.id * 17 + sessionId * 7 + course.number * 13), 100);
      String status = bucket < 84 ? "PRESENT" : bucket < 91 ? "LATE" : bucket < 96 ? "ABSENT" : "EXCUSED";
      Instant checkedInAt =
          switch (status) {
            case "PRESENT" -> startedAt.plusSeconds(60 + (row % 12) * 20L);
            case "LATE" -> startedAt.plusSeconds(12 * 60L + (row % 8) * 30L);
            case "EXCUSED" -> startedAt;
            default -> null;
          };
      jdbc.update(
          "INSERT INTO attendance_records(session_id, student_id, status, checked_in_at, source) VALUES (?, ?, ?, ?, ?)",
          sessionId,
          student.id,
          status,
          checkedInAt == null ? null : checkedInAt.toString(),
          "EXCUSED".equals(status) ? "LEAVE" : "QR");
      result.attendanceRecords++;
      if ("EXCUSED".equals(status)) {
        createLeaveRequest(sessionId, student.id, "病假，已提交校医院证明", "APPROVED", admin.id(), startedAt, result);
      } else if ("ABSENT".equals(status) && bucket % 4 == 0) {
        createLeaveRequest(sessionId, student.id, "家庭事务，申请补交请假材料", "PENDING", null, startedAt, result);
      } else if ("ABSENT".equals(status) && bucket % 5 == 0) {
        createLeaveRequest(sessionId, student.id, "临时外出，证明材料不足", "REJECTED", admin.id(), startedAt, result);
      }
      row++;
    }
  }

  private void createLeaveRequest(
      long sessionId,
      long studentId,
      String reason,
      String status,
      Long reviewerId,
      Instant sessionStartedAt,
      DemoResult result) {
    Instant createdAt = sessionStartedAt.minusSeconds(18 * 3600L);
    Instant reviewedAt = reviewerId == null ? null : sessionStartedAt.plusSeconds(3600L);
    jdbc.update(
        "INSERT INTO leave_requests(session_id, student_id, reason, status, reviewer_id, reviewed_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        sessionId,
        studentId,
        reason,
        status,
        reviewerId,
        reviewedAt == null ? null : reviewedAt.toString(),
        createdAt.toString());
    result.leaveRequests++;
  }

  private void createTodaySessions(List<CourseData> courses, DemoResult result) {
    String todayWeekday = SchedulePeriods.weekdayLabel(LocalDate.now(ZONE));
    Instant started = Instant.now().minusSeconds(5 * 60L);
    Instant ends = Instant.now().plusSeconds(60 * 60L);
    int created = 0;
    for (CourseData course : courses) {
      if (created >= 6) return;
      for (Long slotId : course.slotIds) {
        Map<String, Object> slot = jdbc.queryForMap("SELECT weekday, period FROM course_schedule_slots WHERE id = ?", slotId);
        if (!todayWeekday.equals(String.valueOf(slot.get("weekday")))) continue;
        int period = ((Number) slot.get("period")).intValue();
        insert(
            "INSERT INTO attendance_sessions(course_id, teacher_id, started_at, ends_at, status, method, schedule_slot_id, period_end, kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            course.id,
            course.teacher.id,
            started.toString(),
            ends.toString(),
            "OPEN",
            "QR",
            slotId,
            Math.min(9, period + 1),
            "SCHEDULED");
        result.attendanceSessions++;
        created++;
        break;
      }
    }
  }

  private LocalDate alignToWeekday(LocalDate date, String weekday) {
    DayOfWeek target =
        switch (weekday) {
          case "周一" -> DayOfWeek.MONDAY;
          case "周二" -> DayOfWeek.TUESDAY;
          case "周三" -> DayOfWeek.WEDNESDAY;
          case "周四" -> DayOfWeek.THURSDAY;
          case "周五" -> DayOfWeek.FRIDAY;
          case "周六" -> DayOfWeek.SATURDAY;
          case "周日" -> DayOfWeek.SUNDAY;
          default -> DayOfWeek.MONDAY;
        };
    while (date.getDayOfWeek() != target) {
      date = date.plusDays(1);
    }
    return date;
  }

  private long user(String username, String password, String role, String displayName) {
    return insert(
        "INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)",
        username,
        PasswordHasher.hash(password),
        role,
        displayName);
  }

  private long insert(String sql, Object... args) {
    jdbc.update(sql, args);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  private record DepartmentSeed(String name, String code, String major) {}

  private record DepartmentData(long id, DepartmentSeed seed) {}

  private record ClassData(long id, DepartmentData department, String grade, int index) {}

  private record ClassroomData(long id) {}

  private record TeacherData(long id, DepartmentData department, String username, String name) {}

  private record StudentData(long id, ClassData classData, String studentNo, String name) {}

  private static final class CourseData {
    final long id;
    final long assignmentId;
    final ClassData classData;
    final TeacherData teacher;
    final String code;
    final int number;
    final List<Long> slotIds = new ArrayList<>();
    final List<StudentData> students = new ArrayList<>();

    CourseData(long id, long assignmentId, ClassData classData, TeacherData teacher, String code, int number) {
      this.id = id;
      this.assignmentId = assignmentId;
      this.classData = classData;
      this.teacher = teacher;
      this.code = code;
      this.number = number;
    }
  }

  private static final class DemoResult {
    int departments;
    int classes;
    int classrooms;
    int teachers;
    int students;
    int courses;
    int enrollments;
    int attendanceSessions;
    int attendanceRecords;
    int leaveRequests;
    Map<String, Object> sampleTeacher = Map.of();
    Map<String, Object> sampleStudent = Map.of();
  }

  private enum DemoPreset {
    SMALL("small", 2, 2, 3, 5, 4, 4),
    MEDIUM("medium", 6, 4, 10, 30, 20, 8);

    final String name;
    final int departments;
    final int classesPerDepartment;
    final int teachersPerDepartment;
    final int studentsPerClass;
    final int coursesPerDepartment;
    final int classroomsPerDepartment;

    DemoPreset(
        String name,
        int departments,
        int classesPerDepartment,
        int teachersPerDepartment,
        int studentsPerClass,
        int coursesPerDepartment,
        int classroomsPerDepartment) {
      this.name = name;
      this.departments = departments;
      this.classesPerDepartment = classesPerDepartment;
      this.teachersPerDepartment = teachersPerDepartment;
      this.studentsPerClass = studentsPerClass;
      this.coursesPerDepartment = coursesPerDepartment;
      this.classroomsPerDepartment = classroomsPerDepartment;
    }

    static DemoPreset from(String value) {
      if ("small".equalsIgnoreCase(value)) return SMALL;
      return MEDIUM;
    }
  }
}
