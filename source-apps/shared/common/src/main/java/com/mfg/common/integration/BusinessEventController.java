package com.mfg.common.integration;

import com.mfg.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events/business")
@RequiredArgsConstructor
public class BusinessEventController {
    private final BusinessEventService service;

    @GetMapping
    public ApiResponse<Page<Map<String, Object>>> page(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.page(status, page, size));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String eventId) {
        return ApiResponse.ok(service.detail(eventId));
    }

    @PostMapping("/{eventId}/retry")
    public ApiResponse<Void> retry(@PathVariable String eventId) {
        service.retry(eventId);
        return ApiResponse.ok();
    }
}
