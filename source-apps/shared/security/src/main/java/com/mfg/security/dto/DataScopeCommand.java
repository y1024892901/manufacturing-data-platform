package com.mfg.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record DataScopeCommand(@NotBlank String scopeType, Set<String> values) {}
