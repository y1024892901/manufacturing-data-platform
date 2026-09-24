package com.mfg.common.integration;

import com.mfg.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PermissionResponseTest {
    @RestController static class ProtectedController {
        @GetMapping("/protected") public String denied() { throw new AccessDeniedException("denied"); }
    }
    @Test void methodDenialReturns403InsteadOfGeneric500() throws Exception {
        MockMvcBuilders.standaloneSetup(new ProtectedController()).setControllerAdvice(new GlobalExceptionHandler())
            .build().perform(get("/protected")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20002));
    }
}
