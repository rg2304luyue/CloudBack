package org.cloudback.user.dto;

public record UpdateUserRequest(String nickname, String phone, String email, String avatar) {}
