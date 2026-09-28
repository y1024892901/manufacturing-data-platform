package com.mfg.mdm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdmReadOnlyInterceptorTest {
    private final MdmReadOnlyInterceptor interceptor = new MdmReadOnlyInterceptor(
            new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void permitsReadRequests() throws Exception {
        for (String method : List.of("GET", "HEAD", "OPTIONS")) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/mdm/materials");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertTrue(interceptor.preHandle(request, response, new Object()));
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void rejectsEveryMutationMethodWithLocalizedResponse() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/mdm/materials");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(405, response.getStatus());
            assertTrue(response.getContentAsString().contains("MDM 当前为只读模式"));
        }
    }
}
