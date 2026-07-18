package com.creditai.dto;

public record AuthResponse(String token, String username, String fullName, String role) {}
