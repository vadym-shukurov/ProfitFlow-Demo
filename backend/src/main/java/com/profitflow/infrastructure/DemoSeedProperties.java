package com.profitflow.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Plaintext demo seed passwords (non-prod only). Values are supplied via
 * {@code application.yml} and environment variables — never hard-coded in Java.
 */
@ConfigurationProperties(prefix = "profitflow.demo-seed")
public class DemoSeedProperties {

    private String adminPassword = "";
    private String managerPassword = "";
    private String analystPassword = "";

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword != null ? adminPassword : "";
    }

    public String getManagerPassword() {
        return managerPassword;
    }

    public void setManagerPassword(String managerPassword) {
        this.managerPassword = managerPassword != null ? managerPassword : "";
    }

    public String getAnalystPassword() {
        return analystPassword;
    }

    public void setAnalystPassword(String analystPassword) {
        this.analystPassword = analystPassword != null ? analystPassword : "";
    }

    /** True when all three demo passwords are non-blank (seed may run). */
    public boolean isComplete() {
        return !adminPassword.isBlank() && !managerPassword.isBlank() && !analystPassword.isBlank();
    }
}
