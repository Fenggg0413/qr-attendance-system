package com.example.qrattendance.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AuthContext {
  private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

  private AuthContext() {}

  public static void set(CurrentUser user) {
    CURRENT.set(user);
  }

  public static void clear() {
    CURRENT.remove();
  }

  public static CurrentUser require() {
    CurrentUser user = CURRENT.get();
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return user;
  }

  public static CurrentUser requireRole(String role) {
    CurrentUser user = require();
    if (!role.equals(user.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
    return user;
  }
}
