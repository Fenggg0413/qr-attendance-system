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

// 演示数据生成服务：按 small/medium 预设造一整套校园演示数据
// 范围：院系/班级/教室/教师/学生/课程/排课/选课/8 周历史考勤/今日活跃会话
// 全程在单个事务内执行，失败回滚；学号、考勤分布都用确定性算法（同一预设两次结果完全一致）
@Service
public class DemoDataService {
  // 新建账号统一使用的初始密码（教师、学生都用 123456）
  private static final String DEFAULT_PASSWORD = "123456";
  // 所有演示课程都挂到这一学期下
  private static final String TERM = "2025-2026学年 春季学期";
  private static final ZoneId ZONE = SchedulePeriods.ZONE;
  // 演示排课只用周一到周五，避开周末
  private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五"};
  // 每节"双节课"的起始节次：1-2、3-4、6-7（含午休）、8-9
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

  // 重置入口：清空业务数据 → 按预设规模重新生成 → 返回各类实体的创建数量
  // 整个过程包在一个事务里，任何步骤失败都回滚（避免半套数据残留）
  public Map<String, Object> reset(String preset, CurrentUser admin) {
    DemoPreset size = DemoPreset.from(preset);
    // 用 nanoTime 计时，结果中返回 elapsedMs，便于性能观察
    long started = System.nanoTime();
    return transactions.execute(
        status -> {
          // 先清空旧数据（保留管理员账号），再灌入新数据
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
          // 返回一个教师 / 一个学生作为快速登录样本，便于演示
          response.put("sampleTeacher", result.sampleTeacher);
          response.put("sampleStudent", result.sampleStudent);
          response.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
          return response;
        });
  }

  // 清空所有业务表；删除顺序按外键依赖从子到父，避免外键约束失败
  // 关键：users 表只删非 ADMIN 行（保留登录中的管理员账号）
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

  // 生成整套校园数据的总流程：院系 → 班级 → 教室 → 教师 → 学生 → 课程 → 排课 → 选课 → 历史考勤 → 今日会话
  // 依赖关系严格：班级要先有院系，学生要先有班级，课程要先有班级和教师，排课要先有课程和教室
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
    // 抽第一个教师/学生作为登录示例返给前端
    result.sampleTeacher = Map.of("username", teachers.getFirst().username, "password", DEFAULT_PASSWORD, "name", teachers.getFirst().name);
    result.sampleStudent = Map.of("username", students.getFirst().studentNo, "password", DEFAULT_PASSWORD, "name", students.getFirst().name);
    return result;
  }

  // 按预设数量从 DEPARTMENTS 常量中取前 N 个院系入库
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

  // 为每个院系创建 classesPerDepartment 个班级；班级名形如"计算机科学与技术 2301"，年级在 2023-2025 之间轮转
  private List<ClassData> createClasses(DemoPreset size, List<DepartmentData> departments, DemoResult result) {
    List<ClassData> rows = new ArrayList<>();
    int classIndex = 0;
    for (DepartmentData department : departments) {
      for (int index = 0; index < size.classesPerDepartment; index++) {
        // 年级在 2023/2024/2025 三届中循环（保证有多年级数据）
        int admissionYear = 2023 + (index % 3);
        String grade = String.valueOf(admissionYear);
        // 班级名格式：专业名 + 年级后两位 + 班序号
        String name = department.seed.major + " " + (admissionYear % 100) + String.format("%02d", index + 1);
        long id = insert("INSERT INTO classes(name, grade) VALUES (?, ?)", name, grade);
        rows.add(new ClassData(id, department, grade, classIndex++));
        result.classes++;
      }
    }
    return rows;
  }

  // 生成教室池：6 个建筑循环分配，编号形如"明德楼-101"，容量 60/80/100/120 轮转
  private List<ClassroomData> createClassrooms(DemoPreset size, DemoResult result) {
    List<ClassroomData> rows = new ArrayList<>();
    String[] buildings = {"明德楼", "求是楼", "笃行楼", "创新楼", "实验中心", "信息楼"};
    // 至少 8 间教室，保证排课时有冲突回旋空间
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

  // 为每个院系生成 teachersPerDepartment 个教师：先建 users 行（角色 TEACHER），再建 teachers 档案
  // 用户名格式：t{年}{三位流水号}，如 t2020001；姓名从常量姓/名表里组合
  private List<TeacherData> createTeachers(DemoPreset size, List<DepartmentData> departments, DemoResult result) {
    List<TeacherData> rows = new ArrayList<>();
    int teacherNumber = 1;
    for (DepartmentData department : departments) {
      for (int index = 0; index < size.teachersPerDepartment; index++) {
        // 入职年份在 2020-2024 之间循环
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
                // 用流水号造假手机号"138XXXXXXXX"，保证唯一可识别
                "138" + String.format("%08d", teacherNumber),
                username + "@school.example");
        rows.add(new TeacherData(id, department, username, name));
        result.teachers++;
        teacherNumber++;
      }
    }
    return rows;
  }

  // 为每个班级生成 studentsPerClass 个学生
  // 关键：学号编码格式 B{年级后两位}{院系代码 4 位}{流水号 2 位}，如 B23042101（23 级、计算机学院、01 号）
  // 用 (yy + 院系code) 作 key 维护每个"年级-院系"维度的流水号，保证全局唯一且号段连续
  private List<StudentData> createStudents(DemoPreset size, List<ClassData> classes, DemoResult result) {
    List<StudentData> rows = new ArrayList<>();
    // key = "年份后两位 + 院系code"，value = 该 key 下已分配的最大流水号
    Map<String, Integer> sequenceByYearAndDepartment = new HashMap<>();
    int studentIndex = 0;
    for (ClassData classData : classes) {
      String yy = classData.grade.substring(2);
      String key = yy + classData.department.seed.code;
      // 本班学号起始流水：在同 key 的全局流水基础上 +1
      int next = sequenceByYearAndDepartment.getOrDefault(key, 0) + 1;
      for (int index = 0; index < size.studentsPerClass; index++) {
        int sequence = next + index;
        // 拼出学号：B + 年级后两位 + 院系代码 + 2 位流水
        String studentNo = "B" + yy + classData.department.seed.code + String.format("%02d", sequence);
        // 姓名从姓/名常量表组合，studentIndex 全局递增避免重复
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
      // 更新该 (年级,院系) 维度的流水到本班末尾，下一班继续往后取
      sequenceByYearAndDepartment.put(key, next + size.studentsPerClass - 1);
    }
    return rows;
  }

  // 为每个院系生成 coursesPerDepartment 门课程：班级循环挂、教师循环挂
  // 同时插入 course_assignments 行，把课程挂到固定学期下
  private List<CourseData> createCourses(
      DemoPreset size,
      List<DepartmentData> departments,
      List<ClassData> classes,
      List<TeacherData> teachers,
      DemoResult result) {
    List<CourseData> rows = new ArrayList<>();
    int courseNumber = 1;
    for (DepartmentData department : departments) {
      // 仅取当前院系下的班级和教师，保证课程不会跨院系
      List<ClassData> departmentClasses = classes.stream().filter(item -> item.department == department).toList();
      List<TeacherData> departmentTeachers = teachers.stream().filter(item -> item.department == department).toList();
      for (int index = 0; index < size.coursesPerDepartment; index++) {
        // 用 index % size 在班级/教师上轮转，达到均匀分布
        ClassData classData = departmentClasses.get(index % departmentClasses.size());
        TeacherData teacher = departmentTeachers.get(index % departmentTeachers.size());
        String name = department.seed.major + COURSE_NAMES[index % COURSE_NAMES.length];
        // 课程编号格式：C + 院系代码 + 3 位序号
        String code = "C" + department.seed.code + "-" + String.format("%03d", index + 1);
        long courseId = insert("INSERT INTO courses(name, code, class_id, department_id) VALUES (?, ?, ?, ?)", name, code, classData.id, department.id);
        long assignmentId = insert("INSERT INTO course_assignments(course_id, teacher_id, term) VALUES (?, ?, ?)", courseId, teacher.id, TERM);
        rows.add(new CourseData(courseId, assignmentId, classData, teacher, code, courseNumber++));
        result.courses++;
      }
    }
    return rows;
  }

  // 为每门课安排连续两节的排课位（双节课）
  // 算法：三层循环（星期 → 起始节次 → 教室）枚举所有可能位置，遇到首个无冲突的就插入
  // 冲突维度：教师同一时段不能上两门、教室同一时段不能被两门课占用；用两个 busy Map 记录占用
  // 都失败时（极端紧张场景）走 fallback：基于 course.number 取模强制安排（可能产生冲突但保证一定有数据）
  private void createSchedules(List<CourseData> courses, List<ClassroomData> classrooms) {
    // busy key 形如 "teacherId-周一-1"，含 true 表示该时段被占
    Map<String, Boolean> teacherBusy = new HashMap<>();
    Map<String, Boolean> classroomBusy = new HashMap<>();
    for (CourseData course : courses) {
      boolean placed = false;
      // 外层：星期偏移，按 course.number 打散起始星期，避免所有课都堆在周一
      for (int weekdayOffset = 0; weekdayOffset < WEEKDAYS.length; weekdayOffset++) {
        String weekday = WEEKDAYS[(course.number + weekdayOffset - 1) % WEEKDAYS.length];
        // 中层：起始节次偏移（1/3/6/8 四种双节起点）
        for (int startOffset = 0; startOffset < SLOT_STARTS.length; startOffset++) {
          int start = SLOT_STARTS[(course.number + startOffset - 1) % SLOT_STARTS.length];
          // 内层：依次试每个教室
          for (ClassroomData classroom : classrooms) {
            // 双节课要占用 start 和 start+1 两个时段，因此每个维度构造两个 key 一起检查
            String teacherKeyA = course.teacher.id + "-" + weekday + "-" + start;
            String teacherKeyB = course.teacher.id + "-" + weekday + "-" + (start + 1);
            String roomKeyA = classroom.id + "-" + weekday + "-" + start;
            String roomKeyB = classroom.id + "-" + weekday + "-" + (start + 1);
            // 任意一个 key 已被占用 → 该位置不可用，换下一间教室继续试
            if (teacherBusy.containsKey(teacherKeyA)
                || teacherBusy.containsKey(teacherKeyB)
                || classroomBusy.containsKey(roomKeyA)
                || classroomBusy.containsKey(roomKeyB)) {
              continue;
            }
            // 成对插入两个 slot 实现"连排"，并同步标记占用
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
      // Fallback：三层循环都没找到无冲突位置 —— 按 course.number 取模强行落位
      // 此时可能与已排课程冲突，但演示场景下允许（保证每门课至少有排课记录）
      if (!placed) {
        ClassroomData fallback = classrooms.get((course.number - 1) % classrooms.size());
        String weekday = WEEKDAYS[(course.number - 1) % WEEKDAYS.length];
        int start = SLOT_STARTS[(course.number - 1) % SLOT_STARTS.length];
        insertSlot(course, fallback, weekday, start);
        insertSlot(course, fallback, weekday, start + 1);
      }
    }
  }

  // 插入单个排课槽；起始节次 ≥ 6（下午）的标记为实验课 LAB，否则为讲授课 LECTURE
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

  // 把每门课所属班级的所有学生加入该课的选课表（course_enrollments 行）
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

  // 为每门课生成过去 8 周（含本周）的考勤会话与记录
  // 思路：取课程第一个 slot 的星期+节次，逐周对齐到该星期的日期，生成 CLOSED 状态的历史会话
  private void createAttendanceHistory(List<CourseData> courses, CurrentUser admin, DemoResult result) {
    LocalDate today = LocalDate.now(ZONE);
    for (CourseData course : courses) {
      // 课程必有 ≥1 个 slot（createSchedules 保证），取第一个作为代表
      long slotId = course.slotIds.getFirst();
      Map<String, Object> slot = jdbc.queryForMap("SELECT weekday, period FROM course_schedule_slots WHERE id = ?", slotId);
      String weekday = String.valueOf(slot.get("weekday"));
      int period = ((Number) slot.get("period")).intValue();
      // 倒序生成第 7 周前直到本周（共 8 周）的会话
      for (int week = 7; week >= 0; week--) {
        // 把 today 倒退 week 周后，对齐到 slot 的星期（如周三）
        LocalDate date = alignToWeekday(today.minusWeeks(week), weekday);
        // 安全兜底：本周该课还没上，对齐后日期可能跑到未来 —— 回退一周保证 ≤ today
        if (date.isAfter(today)) {
          date = date.minusWeeks(1);
        }
        // 会话起止 = 该节次起 → 下一节次止（覆盖双节课时长）
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

  // 为某次会话的所有学生生成考勤记录
  // 关键：用 (student.id, sessionId, course.number) 的加权哈希做"确定性伪随机"
  // 同一预设两次重置结果完全一致，便于演示对比；分布近似 84% 出勤 / 7% 迟到 / 5% 缺勤 / 4% 请假
  private void createAttendanceRecords(CourseData course, long sessionId, Instant startedAt, CurrentUser admin, DemoResult result) {
    int row = 0;
    for (StudentData student : course.students) {
      // 用三个 id 的不同质数权重 (17/7/13) 混合，再 mod 100 得到 [0,100) 的桶号
      // 不同学生/会话/课程组合得到的桶号差异大，分布均匀；且结果可复现
      int bucket = Math.floorMod((int) (student.id * 17 + sessionId * 7 + course.number * 13), 100);
      // 按桶号划分状态：[0,84) 出勤、[84,91) 迟到、[91,96) 缺勤、[96,100) 请假
      String status = bucket < 84 ? "PRESENT" : bucket < 91 ? "LATE" : bucket < 96 ? "ABSENT" : "EXCUSED";
      // 打卡时间：正常出勤为开始后 1-4 分钟、迟到为 12 分钟之后、请假为开始时刻、缺勤为 null
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
          // EXCUSED 来源标 LEAVE，其他状态都标 QR（演示用 QR 扫码渠道）
          "EXCUSED".equals(status) ? "LEAVE" : "QR");
      result.attendanceRecords++;
      // 顺便造一些请假记录：EXCUSED 必有已批准请假；ABSENT 中按 bucket%4/%5 派生待审/已拒请假
      // 这样列表既能看到三种请假状态，又不会全部 ABSENT 都关联请假
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

  // 插入一条请假记录；createdAt 设为会话开始前 18 小时，reviewedAt 为开始后 1 小时（若有审批人）
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

  // 为今天生成最多 6 个 OPEN 状态的考勤会话（用于"今日"页演示）
  // 仅挑选与今天星期匹配的 slot，且每门课最多挂 1 个
  private void createTodaySessions(List<CourseData> courses, DemoResult result) {
    String todayWeekday = SchedulePeriods.weekdayLabel(LocalDate.now(ZONE));
    // 起始时间设为 5 分钟前，结束时间设为 1 小时后 —— 制造一个"正在进行中"的窗口
    Instant started = Instant.now().minusSeconds(5 * 60L);
    Instant ends = Instant.now().plusSeconds(60 * 60L);
    int created = 0;
    for (CourseData course : courses) {
      if (created >= 6) return;
      for (Long slotId : course.slotIds) {
        Map<String, Object> slot = jdbc.queryForMap("SELECT weekday, period FROM course_schedule_slots WHERE id = ?", slotId);
        // 只挑落在今天星期的 slot；不匹配则换下一个 slot
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
            // period_end 限制在 9（最大节次），防止越界
            Math.min(9, period + 1),
            "SCHEDULED");
        result.attendanceSessions++;
        created++;
        // 每门课最多挂一个今日会话，break 跳出 slot 内循环
        break;
      }
    }
  }

  // 工具方法：把 date 向后推到指定中文星期（如"周三"），返回该星期对应的日期
  // 用法：与"取最近的星期三"或"该周的星期三"等价
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
    // 朴素实现：一天一天向后移动到目标星期；最多走 6 天，性能可接受
    while (date.getDayOfWeek() != target) {
      date = date.plusDays(1);
    }
    return date;
  }

  // 工具方法：插入 users 行（SHA-256 哈希密码）并返回新行 id
  private long user(String username, String password, String role, String displayName) {
    return insert(
        "INSERT INTO users(username, password_hash, role, display_name) VALUES (?, ?, ?, ?)",
        username,
        PasswordHasher.hash(password),
        role,
        displayName);
  }

  // 工具方法：执行 INSERT 并通过 last_insert_rowid() 取自增主键
  private long insert(String sql, Object... args) {
    jdbc.update(sql, args);
    return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
  }

  // —— 内部数据类型：仅供本服务在内存中传递构造中的中间态 ——
  // 院系种子（常量定义）：包含中文名、4 位代码、专业名
  private record DepartmentSeed(String name, String code, String major) {}

  // 院系实体（已入库）：id + 种子常量
  private record DepartmentData(long id, DepartmentSeed seed) {}

  // 班级实体：归属院系、年级、班级内序号
  private record ClassData(long id, DepartmentData department, String grade, int index) {}

  // 教室实体（极简，只关心 id）
  private record ClassroomData(long id) {}

  // 教师实体：用户名+姓名 + 所属院系
  private record TeacherData(long id, DepartmentData department, String username, String name) {}

  // 学生实体：学号+姓名 + 所属班级
  private record StudentData(long id, ClassData classData, String studentNo, String name) {}

  // 课程实体（非 record，因要在构造后追加 slotIds/students）
  private static final class CourseData {
    final long id;
    final long assignmentId;
    final ClassData classData;
    final TeacherData teacher;
    final String code;
    // 课程全局序号，用于排课的"基于 number 取模"分布算法
    final int number;
    // 该课的所有排课槽 ID（连排时一门课会有多个 slot）
    final List<Long> slotIds = new ArrayList<>();
    // 该课的所有选课学生
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

  // 各类实体的创建计数器，最终随响应返给前端展示
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

  // 演示规模预设：small ~ 24 学生 / medium ~ 720 学生
  // 各维度数量按"院系 × 班级/教师/课程/教室"乘积扩展
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

    // 入参解析：忽略大小写匹配 small，其他一律视为 medium（包括 null）
    static DemoPreset from(String value) {
      if ("small".equalsIgnoreCase(value)) return SMALL;
      return MEDIUM;
    }
  }
}
