# QR Attendance System

> 基于二维码的课堂考勤系统 — 教师动态生成 QR 码，学生扫码签到，管理员通过 Web 后台管理全校数据。

## 功能概览

- **QR 码签到** — 基于 HMAC-SHA256 的 10 秒时间窗口动态令牌，令牌不落库，仅通过计算验证
- **管理后台** — 仪表盘统计、院系/班级/教师/学生/课程/教室 CRUD、排课冲突检测、请假审批
- **教师面板** — 发起考勤、实时 QR 码展示（自动刷新）、点名记录查看、学生笔记管理
- **学生 App** — Android 扫码签到（CameraX + ML Kit）、考勤记录查阅、请假申请

## 项目结构

```
qr-attendance-system/
├── server/        Spring Boot 3.3 后端 (Java 21, SQLite, Maven)
├── web/           React 19 管理端 + 教师端 (Vite)
└── app/           Android 学生端 (Kotlin, Jetpack Compose)
```

## 快速开始

### 环境要求

- **Server:** Java 21+, Maven 3.8+
- **Web:** Node.js 18+, npm 9+
- **Android:** Android Studio, JDK 17+, SDK 36

### 启动后端

```sh
cd server
mvn -Dmaven.repo.local=/tmp/dynamic-qr-attendance-m2 spring-boot:run
```

服务运行在 `http://localhost:8080`，首次启动自动创建 SQLite 数据库并填充演示数据。

### 启动前端

```sh
cd web
npm install
npm run dev
```

开发服务器运行在 `http://localhost:5173`，`/api` 请求自动代理到 `:8080`。

### 构建 Android App

```sh
cd app
GRADLE_USER_HOME=/tmp/dynamic_qr_attendance_gradle ./gradlew :app:assembleDebug
```

模拟器中 API 地址默认为 `http://10.0.2.2:8080/api`。

## 演示账号

首次启动时自动创建以下账号（默认密码 `123456`）：

| 角色   | 用户名       | 密码         | 显示名     |
| ------ | ------------ | ------------ | ---------- |
| 管理员 | `admin`      | `admin123`   | 系统管理员 |
| 教师   | `teacher1`   | `teacher123` | 张老师     |
| 学生   | `student1`   | `student123` | 李同学     |

## API 概览

### 公开接口

| 方法 | 路径              | 说明               |
| ---- | ----------------- | ------------------ |
| POST | `/api/auth/login` | 登录，返回 JWT     |

### 管理员 (`/api/admin/**`)

仪表盘、院系/学期/教室/教师/学生/班级/课程 CRUD、排课管理、选课管理、考勤记录查询与统计、请假审批。

### 教师 (`/api/teacher/**`)

课程列表、发起考勤、QR 令牌获取、点名记录、学生笔记、个人信息修改。

### 学生 (`/api/student/**`)

扫码签到、考勤记录查询、请假申请。

## 运行测试

```sh
# Server
cd server && mvn -Dmaven.repo.local=/tmp/dynamic-qr-attendance-m2 test

# Web
cd web && npm test

# Android
cd app && GRADLE_USER_HOME=/tmp/dynamic_qr_attendance_gradle ./gradlew testDebugUnitTest
```

## 技术栈

| 层级       | 技术                                                  |
| ---------- | ----------------------------------------------------- |
| 后端       | Spring Boot 3.3, JdbcTemplate, SQLite, 自研 HS256 JWT |
| Web 前端   | React 19, Vite, lucide-react, qrcode.react            |
| Android    | Kotlin 2.3, Compose Material 3, CameraX, ML Kit       |
| 认证       | 自定义 HMAC-SHA256 JWT（零库依赖）                    |
| QR 令牌    | 10 秒时间分桶 HMAC，不落库                            |

## 架构说明

- **后端无 Service/Repository 层** — 业务逻辑直接写在 Controller 中，数据访问使用 `JdbcTemplate`。这是有意为之，非代码遗漏。
- **Web 前端为单文件 React 应用** — `web/src/main.jsx` 包含所有页面逻辑，无路由库，通过状态切换视图。
- **Android 为单 Activity Compose 应用** — 不依赖 DI 框架和网络库（直接使用 `HttpURLConnection`）。
