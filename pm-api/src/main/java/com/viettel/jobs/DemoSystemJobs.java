//package com.viettel.jobs;
//
//import com.viettel.troubleshoot.DatasourceVerifier;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//import com.viettel.config.SystemScheduled;
//import com.viettel.config.SystemType;
//import com.viettel.config.RoutingContextExecutor;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class DemoSystemJobs {
//
//    private final JdbcTemplate jdbcTemplate;
//    private final RoutingContextExecutor routingContext;
//    private final DatasourceVerifier datasourceVerifier;
//
//    @SystemScheduled(key = "recalcKpi", datasource = "KPI")
//    public void recalcKpi() {
//        SystemType system = TenantContextHolder.getCurrentSystem();
//        String dsKey = TenantContextHolder.getCurrentDatasourceKey();
//        try {
//            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
//            log.info("[recalcKpi] system={} ds={} result={} ", system, dsKey, one);
//
//            // Verify which database CommonRepository is using
//            datasourceVerifier.verifyConnection();
//        } catch (Exception ex) {
//            log.error("[recalcKpi] system={} ds={} error:", system, dsKey, ex);
//        }
//    }
//
//    @SystemScheduled(key = "cleanupLogs", datasource = "LOG")
//    public void cleanupLogs() {
//        SystemType system = TenantContextHolder.getCurrentSystem();
//        String dsKey = TenantContextHolder.getCurrentDatasourceKey();
//        try {
//            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
//            log.info("[cleanupLogs] system={} ds={} result={} ", system, dsKey, one);
//        } catch (Exception ex) {
//            log.error("[cleanupLogs] system={} ds={} error:", system, dsKey, ex);
//        }
//    }
//
//    // Demo: use two datasources in one job (KPI then LOG) under the same system
//    @SystemScheduled(key = "kpiThenLog", datasource = "KPI")
//    public void kpiThenLog() {
//        SystemType system = TenantContextHolder.getCurrentSystem();
//        try {
//            Integer kpi = jdbcTemplate.queryForObject("select 1", Integer.class);
//            log.info("[kpiThenLog] system={} step=KPI result={}", system, kpi);
//
//            routingContext.runWith(system, "LOG", () -> {
//                Integer logVal = jdbcTemplate.queryForObject("select 1", Integer.class);
//                log.info("[kpiThenLog] system={} step=LOG result={}", system, logVal);
//            });
//        } catch (Exception ex) {
//            log.error("[kpiThenLog] system={} error:", system, ex);
//        }
//    }
//}
//
//
