package com.mfg.security.dto;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
public record IdSetCommand(@NotNull Set<Long> ids) {}
