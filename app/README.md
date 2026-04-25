# 动态二维码考勤管理系统 MVP

本仓库包含三个模块：

- `server`：Spring Boot 3 + Java 21 + Maven + SQLite REST API。
- `web`：React + Vite 教师端/管理员端。
- `app`：Android Compose 学生端，包含扫码 payload 解析、签到、记录和特殊情况申报。

## 默认账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 教师 | `teacher1` | `teacher123` |
| 学生 | `student1` | `student123` |

## 后端

```bash
cd server
mvn -Dmaven.repo.local=/tmp/dynamic-qr-attendance-m2 test
mvn -Dmaven.repo.local=/tmp/dynamic-qr-attendance-m2 spring-boot:run
```

后端默认监听 `http://localhost:8080`。SQLite 文件默认位于 `server/data/qr-attendance.sqlite`，首次启动会自动创建表和演示数据。

核心接口：

- `POST /api/auth/login`
- `GET /api/me`
- `GET /api/teacher/courses`
- `POST /api/teacher/courses/{courseId}/attendance-sessions`
- `GET /api/teacher/attendance-sessions/{id}/qr`
- `POST /api/student/check-ins`
- `GET /api/student/attendance-records`
- `POST /api/student/leave-requests`
- `GET /api/admin/statistics`
- `POST /api/admin/leave-requests/{id}/review`

动态二维码 payload 格式为：

```text
qr-attendance://checkin?sessionId=<sessionId>&token=<token>
```

教师选择的是整场考勤开放时长；单个二维码 token 固定按 10 秒时间桶生成，服务端只接受当前时间桶 token。

## Web

```bash
cd web
npm install
npm test
npm run build
npm run dev
```

开发服务默认是 `http://localhost:5173`，Vite 已配置 `/api` 代理到 `http://localhost:8080`。

管理员端提供教师、学生、班级、课程、课程分配、考勤记录、统计和特殊申报审核。教师端提供课程列表、发起考勤、动态二维码轮询展示和签到结果。

## Android

```bash
cd app
GRADLE_USER_HOME=/tmp/dynamic_qr_attendance_gradle ./gradlew testDebugUnitTest
GRADLE_USER_HOME=/tmp/dynamic_qr_attendance_gradle ./gradlew :app:assembleDebug
```

Android 模拟器访问宿主机后端使用 `http://10.0.2.2:8080/api`，已写入学生端 API 客户端。学生端页面包含登录、个人信息、扫码签到、考勤记录和特殊情况申报。

当前实现包含 CameraX/ML Kit 的 `QrImageAnalyzer`，用于识别 QR 内容；MVP 页面提供二维码内容输入提交，便于模拟器和手工演示快速验收。

## 手工验收建议

1. 启动后端和 Web。
2. 用管理员账号登录 Web，确认演示班级、课程、教师、学生数据存在。
3. 用教师账号登录 Web，选择 `Java Web 开发`，发起 5 分钟考勤。
4. 观察二维码 payload 每 10 秒刷新。
5. 在 Android 学生端登录，输入当前 payload 并签到。
6. 再次提交同一 payload，确认不会重复插入。
7. 使用旧 token 提交会被后端拒绝。
8. 管理员查看考勤记录和统计，审核学生特殊情况申报。
