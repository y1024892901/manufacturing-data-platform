package com.mfg.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoPasswordHashTest {
    @Test
    void seedHashMustMatchDocumentedDemoPassword() {
        String seedHash = "$2a$10$wMGsPx.8vZQdPRB46K3G4.dfFjIafjLx2hhhOyaTbB3y7fBId/AsK";
        assertTrue(new BCryptPasswordEncoder().matches("Test@123456", seedHash));
    }
}
