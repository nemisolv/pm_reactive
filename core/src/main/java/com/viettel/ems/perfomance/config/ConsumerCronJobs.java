package com.viettel.ems.perfomance.config;

import com.viettel.ems.perfomance.service.PerformanceManagement;
import com.viettel.ems.perfomance.service.SystemManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerCronJobs {

    private final SystemManager systemManager;

    @SystemScheduled(key = "remainingPreCounterFileHandler", datasource = "PRIMARY")
    public  synchronized void remainingPreCounterFileHandler() {
        SystemType system = TenantContextHolder.getCurrentSystem();
        log.info("system: {} remainingPreCounterFileHandler is running periodically", system);
        try {
            PerformanceManagement cronWorker = systemManager.getPerformanceInstance(system);
            cronWorker.remainingPreCounterFileHandler();
        } catch (Exception ex) {
            log.error("[scheduleRemainingPreCounterFileHandler] system={} error:", system, ex);
        }
    }

    @SystemScheduled(key = "remainingPreCounterFileHandlerClickhouse", datasource = "PRIMARY")
    public void remainingPreCounterFileHandlerClickhouse() {
        SystemType system = TenantContextHolder.getCurrentSystem();
        log.info("system: {} remainingPreCounterFileHandlerClickhouse is running periodically", system);

        try {
            PerformanceManagement cronWorker = systemManager.getPerformanceInstance(system);
            cronWorker.remainingPreCounterFileHandlerClickhouse();
        } catch (Exception ex) {
            log.error("[scheduleRemainingPreCounterFileHandlerClickhouse] system={} error:", system, ex);
        }
    }


    @SystemScheduled(key = "voteInstanceProcessing", datasource = "PRIMARY")
    public void voteInstanceProcessing() {
        SystemType system = TenantContextHolder.getCurrentSystem();
        log.info("system: {} voteInstanceProcessing is running periodically", system);

        try {
            PerformanceManagement cronWorker = systemManager.getPerformanceInstance(system);
            cronWorker.voteInstanceProcessing();
        } catch (Exception ex) {
            log.error("[scheduleVoteInstanceProcessing] system={} error:", system, ex);
        }
    }

    @SystemScheduled(key = "updateCounterInfo", datasource = "PRIMARY")
    public void updateCounterInfo() {
        SystemType system = TenantContextHolder.getCurrentSystem();
        log.info("system: {} updateCounterInfo is running periodically", system);

        try {
            PerformanceManagement cronWorker = systemManager.getPerformanceInstance(system);
            cronWorker.updateCounterInfo();
        } catch (Exception ex) {
            log.error("[scheduleUpdateCounterInfo] system={} error:", system, ex);
        }
    }



}


