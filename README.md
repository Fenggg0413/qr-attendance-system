# 动态二维码考勤系统

基于 HMAC 时间窗口动态二维码的多角色考勤系统，由 Spring Boot 后端、React 管理端与 Android 学生端三个模块构成。
<img width="3164" height="2070" alt="image" src="https://github.com/user-attachments/assets/e5c1fc1a-a6aa-48ba-b7f1-855ebd4808df" />


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

## 技术栈

| 架构层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.3.6 |
| 数据库 | SQLite |
| 认证 | 自定义 HS256 JWT（无第三方库） |
| Web 前端 | React 19 + Vite 7 |
| Android | Jetpack Compose + CameraX + ML Kit |
| 测试 | Vitest (Web) / JUnit 5 (Server) |
