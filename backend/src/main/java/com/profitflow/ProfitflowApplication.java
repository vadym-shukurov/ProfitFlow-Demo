package com.profitflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.profitflow.config.RedisRateLimitAutoConfiguration;

/**
 * ProfitFlow application entry point.
 *
 * <p>{@code @ConfigurationPropertiesScan} enables {@link com.profitflow.security.RsaKeyProperties}
 * and any other {@code @ConfigurationProperties} beans to be discovered without explicit
 * {@code @EnableConfigurationProperties} on every configuration class.
 *
 * <p>{@code @EnableScheduling} activates scheduled tasks, including the daily
 * refresh token cleanup job in {@link com.profitflow.security.RefreshTokenService}.
 */
@SpringBootApplication(exclude = {RedisAutoConfiguration.class})
@ConfigurationPropertiesScan
@EnableScheduling
@Import(RedisRateLimitAutoConfiguration.class)
public class ProfitflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfitflowApplication.class, args);
    }
}
