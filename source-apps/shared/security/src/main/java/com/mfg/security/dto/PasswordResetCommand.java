package com.mfg.security.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record PasswordResetCommand(@NotBlank @Size(min = 8, max = 64) String password) {}
