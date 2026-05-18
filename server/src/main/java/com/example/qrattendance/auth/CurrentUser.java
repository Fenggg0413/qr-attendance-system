package com.example.qrattendance.auth;

// 已认证用户的最小身份信息：登录后存入 AuthContext，由控制器读取做权限判定
// id = users.id；role ∈ {ADMIN, TEACHER, STUDENT}；displayName 为前端展示用昵称
public record CurrentUser(long id, String username, String role, String displayName) {}
