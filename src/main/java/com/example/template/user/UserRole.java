package com.example.template.user;

/**
 * Deliberately minimal for a template. A real MVP might extend this to
 * a full permission/role table, but a fixed enum covers the vast majority
 * of MVPs that just need "regular user" vs "admin".
 */
public enum UserRole {
  USER,
  ADMIN
}
