package com.mfg.security.dto;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
public record SystemSetCommand(@NotNull Set<String> systems) {}
