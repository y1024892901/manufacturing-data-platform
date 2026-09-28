package com.mfg.mdm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Keeps MDM records immutable through every API entry point. */
@Configuration
@RequiredArgsConstructor
public class MdmReadOnlyWebConfig implements WebMvcConfigurer {
    private final ObjectMapper objectMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MdmReadOnlyInterceptor(objectMapper))
                .addPathPatterns("/api/mdm", "/api/mdm/**");
    }
}
