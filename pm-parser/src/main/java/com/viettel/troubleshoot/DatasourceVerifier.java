package com.viettel.troubleshoot;

import com.viettel.config.SystemType;
import com.viettel.config.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatasourceVerifier {
    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    public void verifyConnection() {
        // Only verify in non-production environments
        if ("prod".equalsIgnoreCase(activeProfile)) {
            return;
        }

        try {
            SystemType system = TenantContextHolder.getCurrentSystem();
            String dsKey = TenantContextHolder.getCurrentDatasourceKey();

            // Get datasource URL from the actual connection
            String url = jdbcTemplate.queryForObject("SELECT @@hostname as hostname, DATABASE() as database_name",
                    (rs, rowNum) -> rs.getString("hostname") + "/" + rs.getString("database_name"));

            log.info("[DatasourceVerifier] Current context: system={}, datasource={}, connected to: {}", system, dsKey, url);
        } catch (Exception ex) {
            log.error("[DatasourceVerifier] Error verifying connection:", ex);
        }
    }
}
