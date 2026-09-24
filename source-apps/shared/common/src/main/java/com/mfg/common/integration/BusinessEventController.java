package com.mfg.common.integration;

import com.mfg.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events/business")
@RequiredArgsConstructor
public class BusinessEventController {
    private final BusinessEventService service;
    private final BusinessEventConsumerScheduler consumer;

    @GetMapping
    @PreAuthorize("hasAuthority('SYS:EVENT:VIEW')")
    public ApiResponse<Page<Map<String, Object>>> page(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.page(status, page, size));
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("hasAuthority('SYS:EVENT:VIEW')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String eventId) {
        return ApiResponse.ok(service.detail(eventId));
    }

    @PostMapping("/{eventId}/retry")
    @PreAuthorize("hasAuthority('SYS:EVENT:EXECUTE')")
    public ApiResponse<Void> retry(@PathVariable String eventId) {
        service.retry(eventId);
        return ApiResponse.ok();
    }
    @GetMapping("/inbox/dead-letters")
    @PreAuthorize("hasAuthority('SYS:EVENT:VIEW')")
    public ApiResponse<java.util.List<Map<String, Object>>> deadLetters(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(consumer.deadLetters(limit));
    }

    @PostMapping("/inbox/replay")
    @PreAuthorize("hasAuthority('SYS:EVENT:EXECUTE')")
    public ApiResponse<Integer> replay(@RequestBody java.util.List<Long> ids) {
        return ApiResponse.ok(consumer.replay(ids));
    }
}
