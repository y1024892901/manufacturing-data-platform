package com.mfg.plm.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.plm.service.PlmCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Read-only selection data from the MDM product snapshot delivered to PLM. */
@RestController
@RequestMapping("/api/plm/options")
@RequiredArgsConstructor
public class PlmOptionsController {
    private final PlmCatalogService catalog;

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('PLM:CATALOG:VIEW')")
    public ApiResponse<List<Map<String, Object>>> products() {
        return ApiResponse.ok(catalog.publishedProducts());
    }
}
