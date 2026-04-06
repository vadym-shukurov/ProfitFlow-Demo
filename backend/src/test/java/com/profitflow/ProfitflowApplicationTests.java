package com.profitflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Fast smoke test (H2 in-memory). Does not require Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProfitflowApplicationTests {

    @Test
    void contextLoads() {
    }
}
