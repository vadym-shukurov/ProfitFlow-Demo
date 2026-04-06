package com.profitflow.infrastructure;

import com.profitflow.adapter.out.persistence.entity.AppUserEntity;
import com.profitflow.adapter.out.persistence.jpa.AppUserEntityRepository;
import com.profitflow.domain.security.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Seeds default demo users on first startup if the {@code app_user} table is empty.
 *
 * <p>This component is active on every profile <em>except</em> {@code prod}.
 * In production, users must be provisioned via the future User Management API
 * (see roadmap) or directly via a database migration with hashed passwords.
 *
 * <p>Passwords come from {@link DemoSeedProperties} ({@code profitflow.demo-seed.*} /
 * {@code PROFITFLOW_DEMO_*_PASSWORD} env vars). If any value is missing, seeding is skipped.
 *
 * <p><strong>Override defaults before sharing any environment.</strong>
 */
@Component
@Profile("!prod")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppUserEntityRepository users;
    private final PasswordEncoder encoder;
    private final DemoSeedProperties demoSeed;

    public DemoDataSeeder(
            AppUserEntityRepository users,
            PasswordEncoder encoder,
            DemoSeedProperties demoSeed) {
        this.users = users;
        this.encoder = encoder;
        this.demoSeed = demoSeed;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return; // already seeded
        }
        if (!demoSeed.isComplete()) {
            log.warn(
                    "Skipping demo user seed: set profitflow.demo-seed.admin-password, "
                            + "manager-password, and analyst-password (or PROFITFLOW_DEMO_* env vars).");
            return;
        }
        log.info("Seeding demo users...");

        List<AppUserEntity> seeds = List.of(
                new AppUserEntity(UUID.randomUUID(), "admin",
                        "admin@profitflow.internal",
                        encoder.encode(demoSeed.getAdminPassword()), UserRole.ADMIN),
                new AppUserEntity(UUID.randomUUID(), "manager",
                        "manager@profitflow.internal",
                        encoder.encode(demoSeed.getManagerPassword()), UserRole.FINANCE_MANAGER),
                new AppUserEntity(UUID.randomUUID(), "analyst",
                        "analyst@profitflow.internal",
                        encoder.encode(demoSeed.getAnalystPassword()), UserRole.ANALYST)
        );

        users.saveAll(seeds);
        log.warn("Demo users created — change passwords before sharing this environment!");
    }
}
