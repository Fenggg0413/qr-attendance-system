# QR Attendance System

基于动态二维码的课堂考勤系统，包含 Spring Boot 后端、React Web 管理/教师端和 Android 学生端。

## 项目结构

```
qr-attendance-system/
├── server/   Spring Boot 3.3 后端 (Java 21, SQLite, Maven)
├── web/      React 19 前端 — 管理后台 + 教师面板 (Vite)
├── app/      Android 学生端 (Kotlin, Jetpack Compose)
└── scripts/  辅助脚本
```

## 功能概览

**管理员 Web 端**
- 仪表盘统计：学生总数、教师总数、出勤率趋势、近期考勤概览
- 院系、班级、学期、教室、教师、学生、课程的完整 CRUD
- 排课管理（冲突检测）、选课管理、考勤记录查询与统计
- 请假审批、批量操作与数据导出

**教师 Web 端**
- 请假审批面板：按状态筛选待审/已批/已拒请假，一键审批
- 课程考勤：选择课程与时间段，生成动态 QR 码供学生扫码签到
- 实时 QR 码展示（每 10 秒自动刷新），支持全屏显示
- 点名记录查看、学生出勤统计

**学生 Android 端**
- 基于课表的今日课程仪表盘，一目了然地查看出勤状态
- CameraX + ML Kit 扫码签到，支持手动输入作为降级方案
- 考勤记录查询、课表查看、请假申请与追踪
- 个人资料编辑与密码修改
- 可配置的服务器地址，支持真机调试与局域网部署

**安全机制**
- 自研 HS256 JWT 认证（零外部库依赖），基于角色（admin/teacher/student）的访问控制
- QR 令牌采用 HMAC-SHA256 + 10 秒时间窗口分桶，令牌不落库，仅通过实时计算验证，防止重放攻击

## 快速开始

### 环境要求

| 模块 | 要求 |
| ---- | ---- |
| Server | Java 21+, Maven 3.8+ |
| Web | Node.js 18+, npm 9+ |
| Android | Android Studio, JDK 17+, SDK 36 |

### 1. 启动后端

```sh
cd server
mvn -Dmaven.repo.local=/tmp/dynamic-qr-attendance-m2 spring-boot:run
```

服务运行在 `http://localhost:8080`，首次启动自动创建 SQLite 数据库。仅内置管理员账号，不创建测试教师/学生。

### 2. 生成校园演示数据

后端启动后，运行脚本生成完整的校园演示数据：

```sh
node scripts/reset-campus-demo.mjs
```

脚本会保留管理员账号，清除已有业务数据，然后生成中等规模校园数据：

- 约 6 个学院、24 个班级、60 名教师、720 名学生、120 门课程
- 最近 8 周的考勤历史记录
- 教师账号格式 `tYYYYNNN`（如 `t2024001`），默认密码 `123456`
- 学生账号与学号一致，格式 `BYYCCCCNN`，默认密码 `123456`

可选参数：

```sh
node scripts/reset-campus-demo.mjs \
  --api-base http://localhost:8080/api \
  --username admin --password admin123 \
  --preset small
```

> [!NOTE]
> 演示数据也可通过 API 生成：`POST /api/admin/demo-data/reset`（需 admin 权限），支持 `small` 和 `medium` 两种预设规模。

### 3. 启动前端

```sh
cd web
npm install
npm run dev
```

开发服务器运行在 `http://localhost:5173`，`/api` 请求自动代理到 `:8080`。

### 4. 构建 Android App

```sh
cd app
GRADLE_USER_HOME=/tmp/dynamic_qr_attendance_gradle ./gradlew :app:assembleDebug
```

模拟器中 API 地址默认为 `http://10.0.2.2:8080/api`。真机调试时，在登录页的「服务器地址」中填写后端所在电脑的局域网地址（如 `http://192.168.1.23:8080/api`），手机和电脑需处于同一网络，且电脑防火墙需允许 `8080` 端口访问。

## 默认账号

首次启动仅自动创建管理员账号。教师和学生账号由演示数据脚本生成。

| 角色 | 用户名 | 密码 | 说明 |
| ---- | ------ | ---- | ---- |
| 管理员 | `admin` | `admin123` | 内置，不可删除 |
| 教师 | 运行脚本后输出 | `123456` | 由演示数据脚本创建 |
| 学生 | 运行脚本后输出 | `123456` | 由演示数据脚本创建 |

> [!TIP]
> 通过管理界面创建的新用户默认密码为 `123456`。

## API 概览

### 公开接口

| 方法 | 路径 | 说明 |
| ---- | ---- | ---- |
| POST | `/api/auth/login` | 登录，返回 JWT token |

### 管理员 (`/api/admin/**`)

仪表盘统计、院系/学期/教室 CRUD、教师/学生管理、班级管理、课程 CRUD、排课与选课管理、考勤记录查询、请假审批、演示数据重置。

### 教师 (`/api/teacher/**`)

课程列表、发起/结束考勤、QR 令牌获取、点名记录与统计、请假审批、个人信息修改。

### 学生 (`/api/student/**`)

基于课表的今日课程仪表盘、扫码签到、考勤记录查询、课表查询、请假申请、修改资料与密码。

## 运行测试

```sh
# Server（Spring Boot 集成测试，内存 SQLite）
cd server && mvn -Dmaven.repo.local=/tmp/dynamic-qr-attendance-m2 test

# Web（vitest + jsdom + React Testing Library）
cd web && npm test

# Android（Robolectric + Turbine + MockWebServer）
cd app && GRADLE_USER_HOME=/tmp/dynamic_qr_attendance_gradle ./gradlew testDebugUnitTest
```

## 技术栈

| 层级 | 技术 |
| ---- | ---- |
| 后端框架 | Spring Boot 3.3, JdbcTemplate, SQLite |
| 认证 | 自研 HS256 JWT（零库依赖） |
| QR 令牌 | 10 秒时间分桶 HMAC-SHA256，不落库 |
| Web 前端 | React 19, Vite, lucide-react, qrcode.react |
| Android | Kotlin, Jetpack Compose, Material 3, CameraX, ML Kit, OkHttp |
| 测试 | JUnit 5 + MockMvc, Vitest + Testing Library, Robolectric + Turbine |

## 架构说明

- **后端无 Service/Repository 层**：业务逻辑直接写在 Controller 中，数据访问使用 `JdbcTemplate`。这是有意为之，非代码遗漏。Controller 类按角色拆分（`AdminController` ~1143 行、`TeacherController` ~769 行、`StudentController` ~454 行），辅助方法位于各 Controller 底部。
- **Web 前端为单文件 React 应用**：`web/src/main.jsx`（~3750 行）包含所有页面逻辑，不使用路由库，通过组件状态切换视图。API 客户端封装在 `services/api.js`。
- **Android 为单 Activity Compose 应用**：手动 DI（`AppContainer`），MVVM 架构（ViewModel + StateFlow），不依赖 DI 框架。包结构按 `core/data/ui` 分层，每个功能屏幕独立 ViewModel。
- **数据库**：Schema 通过 `DatabaseInitializer` 以 `CREATE TABLE IF NOT EXISTS` + `addColumnIfMissing` 方式程序化创建和演进，不使用 Flyway/Liquibase 等迁移工具。
