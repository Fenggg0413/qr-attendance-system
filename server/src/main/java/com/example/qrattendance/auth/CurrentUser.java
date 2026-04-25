package com.example.qrattendance.auth;

public record CurrentUser(long id, String username, String role, String displayName) {}
