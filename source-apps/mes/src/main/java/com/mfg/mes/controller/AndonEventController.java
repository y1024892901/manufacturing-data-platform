package com.mfg.mes.controller;

import com.mfg.common.api.ApiResponse;
import com.mfg.mes.entity.AndonEvent;
import com.mfg.mes.service.AndonEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/mes/andon-events")
@RequiredArgsConstructor
public class AndonEventController {
    private final AndonEventService events;

    @GetMapping
    @PreAuthorize("hasAuthority('MES:ANDON:VIEW')")
    public ApiResponse<Page<AndonEvent>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(events.page(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:ANDON:VIEW')")
    public ApiResponse<AndonEvent> get(@PathVariable Long id) {
        return ApiResponse.ok(events.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MES:ANDON:CREATE')")
    public ApiResponse<AndonEvent> create(@RequestBody AndonEvent input) {
        return ApiResponse.ok(events.create(input));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:ANDON:UPDATE')")
    public ApiResponse<AndonEvent> update(@PathVariable Long id, @RequestBody AndonEvent input) {
        return ApiResponse.ok(events.update(id, input));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MES:ANDON:DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        events.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("hasAuthority('MES:ANDON:RESPOND')")
    public ApiResponse<AndonEvent> respond(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, String> input) {
        return ApiResponse.ok(events.respond(id, input == null ? null : input.get("responseNote")));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('MES:ANDON:RESOLVE')")
    public ApiResponse<AndonEvent> resolve(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> input) {
        String resolution = input == null ? null : String.valueOf(input.getOrDefault("resolution", ""));
        Integer downtime = null;
        if (input != null && input.get("downtimeMinutes") != null) {
            try { downtime = Integer.valueOf(String.valueOf(input.get("downtimeMinutes"))); }
            catch (NumberFormatException ignored) { downtime = -1; }
        }
        return ApiResponse.ok(events.resolve(id, resolution, downtime));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('MES:ANDON:CLOSE')")
    public ApiResponse<AndonEvent> close(@PathVariable Long id) {
        return ApiResponse.ok(events.close(id));
    }
}
