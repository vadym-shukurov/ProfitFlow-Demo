package com.profitflow;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL integration tests via Testcontainers — validates the full stack (JPA,
 * Flyway-less ddl in test profile, security filters, JWT issuance) against a real
 * database, complementing JaCoCo unit-test coverage.
 *
 * <p>Requires Docker. Run: {@code mvn verify -DskipITs=false} (Failsafe, {@code @Tag("integration")}).
 * When Docker is not available (e.g. minimal local setups), the test class is skipped rather than
 * failing the build.
 */
@SpringBootTest(classes = ProfitflowApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class ProfitflowPostgresIntegrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("profitflow")
            .withUsername("profitflow")
            .withPassword("profitflow");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    void contextLoadsAgainstPostgres() {
    }

    @Test
    void actuatorHealthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void analystCannotPostAllocationRun() throws Exception {
        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"analyst\",\"password\":\"Analyst123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/v1/allocations/run")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanRunAllocationAgainstPostgres() throws Exception {
        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager\",\"password\":\"Manager123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/v1/allocations/run")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
