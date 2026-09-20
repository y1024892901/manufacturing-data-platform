package com.mfg.security.config;

import com.mfg.security.principal.LoginUser;
import com.mfg.security.service.OperationAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Set;

@Slf4j @Aspect @Component @RequiredArgsConstructor
public class OperationAuditAspect {
    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final OperationAuditService auditService;

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object audit(ProceedingJoinPoint point) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return point.proceed();
        HttpServletRequest request = attrs.getRequest();
        String uri = request.getRequestURI();
        if (!MUTATIONS.contains(request.getMethod()) || uri.startsWith("/api/auth/")) return point.proceed();
        try {
            Object result = point.proceed();
            write(request, request.getMethod(), "{\"status\":\"SUCCESS\"}");
            return result;
        } catch (Throwable ex) {
            write(request, "FAILED_" + request.getMethod(), "{\"status\":\"FAILED\"}");
            throw ex;
        }
    }

    private void write(HttpServletRequest request, String action, String result) {
        try {
            LoginUser user = CurrentUser.get();
            String[] path = request.getRequestURI().replaceFirst("^/api/", "").split("/");
            String system = path.length == 0 ? "platform" : path[0];
            if ("admin".equals(system)) system = "platform";
            String objectType = path.length > 1 ? path[1].toUpperCase().replace('-', '_') : "UNKNOWN";
            String objectId = path.length > 2 && path[2].matches("[0-9A-Za-z_-]+") ? path[2] : null;
            auditService.record(user.getUsername(), user.getRealName(), system, action, objectType,
                    objectId, request.getRequestURI(), result, request.getRemoteAddr());
        } catch (Exception auditError) { log.warn("写入操作审计失败: {}", auditError.getMessage()); }
    }
}
