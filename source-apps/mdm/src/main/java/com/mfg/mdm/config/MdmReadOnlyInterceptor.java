package com.mfg.mdm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

public class MdmReadOnlyInterceptor implements HandlerInterceptor {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String READ_ONLY_MESSAGE = "MDM 当前为只读模式，不允许修改数据或提交审批";

    private final ObjectMapper objectMapper;

    public MdmReadOnlyInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (READ_METHODS.contains(request.getMethod())) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(405, READ_ONLY_MESSAGE));
        return false;
    }
}
