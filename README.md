# 动态二维码考勤系统

基于 HMAC 时间窗口动态二维码的多角色考勤系统，由 Spring Boot 后端、React 管理端与 Android 学生端三个模块构成。

## 解决的问题

传统静态二维码考勤中，学生截屏转发二维码即可实现远程代签，失去考勤的防伪意义。本系统通过 **10 秒时间窗口 + HMAC-SHA256 动态令牌** 机制，使二维码内容每 10 秒自动刷新，验证过程无需在服务端存储令牌状态，从原理上杜绝截屏代签行为。

## 系统架构

```
qr-attendance-system/
├── server/          Spring Boot 3.3.6 REST API (Java 21)
├── web/             React 19 管理端/教师端 (Vite)
├── app/             Android Compose 学生端 (Kotlin)
└── scripts/         演示数据生成脚本
```

三模块职责：

| 模块 | 角色 | 核心功能 |
|------|------|----------|
| `server/` | 后端服务 | REST API、JWT 认证、QR 令牌生成与验证、SQLite 数据管理 |
| `web/` | 管理员 / 教师 | 考勤场次管理、出勤统计、学生/教师/课程管理、请假审批 |
| `app/` | 学生 | 扫码签到、出勤记录查询、课表查看、请假申请 |

## 环境要求

- **Server:** Java 21 + Maven 3.9+
- **Web:** Node.js 20+
- **App:** Android Studio (Koala+), JDK 21, Gradle 8.x

## 快速开始

### 1. 启动后端

```bash
cd server
mvn -Dmaven.repo.local=/tmp/dynamic-qr-attendance-m2 spring-boot:run
# 服务启动在 http://localhost:8080
```

### 2. 启动 Web 前端

```bash
cd web
npm install
npm run dev
# 开发服务器启动在 http://localhost:5173，API 自动代理到 :8080
```

### 3. 生成演示数据（推荐）

```bash
node scripts/reset-campus-demo.mjs --preset small
```

也可直接调用 API：

```bash
curl -X POST http://localhost:8080/api/admin/demo-data/reset \
  -H "Content-Type: application/json" \
  -d '{"preset": "small"}'
```

### 4. 构建 Android App

```bash
cd app
GRADLE_USER_HOME=/tmp/dynamic_qr_attendance_gradle ./gradlew :app:assembleDebug
```

## 演示账号

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 管理员 | `admin` | `admin123` | 系统启动时自动创建 |
| 教师 | 如 `t2020001` | `123456` | 演示数据生成，格式 `t{年}{序号}` |
| 学生 | 如 `B2204XX01` | `123456` | 演示数据生成，格式 `B{年后两位}{系代码}{序号}` |

新建用户的默认密码为 `123456`。

## 运行测试

```bash
# 后端测试（内存 SQLite，无需配置）
cd server && mvn -Dmaven.repo.local=/tmp/dynamic-qr-attendance-m2 test

# Web 前端测试
cd web && npm test

# Android 单元测试
cd app && GRADLE_USER_HOME=/tmp/dynamic_qr_attendance_gradle ./gradlew :app:testDebugUnitTest
```

## 核心设计

**动态二维码防伪机制：** `QrTokenService` 将当前 10 秒时间窗口与会话 ID 组合后计算 HMAC-SHA256，生成动态令牌。验证端对同一时间窗口重新计算 HMAC 进行比对，无需存储令牌——只要时间窗口过期，旧二维码即失效，从根本上防止截屏转发。

**无 ORM + 薄服务层：** 后端使用原生 `JdbcTemplate` 直接操作 SQLite，业务逻辑集中在按角色拆分的 Controller 中（AdminController / TeacherController / StudentController）。这是有意为之的架构选择，而非临时方案。

**无框架前端：** Web 端单文件 React 应用（`main.jsx`），无路由库、无状态管理库，纯 `useState`/`useContext` 驱动，`localStorage` 管理会话。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.3.6 |
| 数据库 | SQLite |
| 认证 | 自定义 HS256 JWT（无第三方库） |
| Web 前端 | React 19 + Vite 7 |
| Android | Jetpack Compose + CameraX + ML Kit |
| 测试 | Vitest (Web) / JUnit 5 (Server) |
