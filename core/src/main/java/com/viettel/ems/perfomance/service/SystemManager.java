package com.viettel.ems.perfomance.service;

import com.viettel.ems.perfomance.config.ConfigManager;
import com.viettel.ems.perfomance.config.ContextAwareExecutor;
import com.viettel.ems.perfomance.config.SystemConfig;
import com.viettel.ems.perfomance.config.SystemType;
import com.viettel.ems.perfomance.object.CounterObject;
import com.viettel.ems.perfomance.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemManager {

    private static final int DEFAULT_NUM_OF_THREADS = 20;
    private final Map<SystemType, PerformanceManagement> performanceInstances = new ConcurrentHashMap<>();
    private final Map<SystemType, ContextAwareExecutor> consumerExecutors = new ConcurrentHashMap<>();


    
    private final ConfigManager configManager;
    private final FTPPathRepository ftpPathRepository;
    private final CounterMySqlRepository counterMySqlRepository;
    private final ExtraFieldRepository extraFieldRepository;
    private final NERepository neRepository;
    private final CounterCounterCatRepository counterCounterCatRepository;
    private final ParamsCodeRepository paramsCodeRepository;
    private final NotificationEngineRedisService notificationEngineRedisService;
    private final KafkaTemplate<String, ArrayList<CounterObject>> kafkaTemplate;
    private final KafkaMessageRepository kafkaMessageRepository;
    private final SystemConfig systemConfig;
    private final DataLakeProcess dataLakeProcess;

    // NEW: Reactive dependencies (optional - only if reactive mode enabled)
    private final FileProcessingStateJdbcRepository fileStateRepository;
    private final StringRedisTemplate redisTemplate;
    private final GenericObjectPool<FTPClient> ftpClientPool;



    // the Entrypoint
    @PostConstruct
    public void bootstrap() {
        // Start all systems (5GC, 5GA, 4GA, ONT)
        log.info("🚀 Bootstrapping System Manager...");
        for (SystemType systemType : SystemType.values()) {
            if(configManager.isDeployed(systemType)) {
                startSystem(systemType);
            } else {
                log.info("⚠️ System `{}` is disabled in configuration, skipping startup.", systemType);
            }
        }
        log.info("✅ All systems started successfully");
    }


    public void startSystem(SystemType systemType) {
        log.info("🚀 Starting system: {} ({})", systemType.getCode(), systemType.getDescription());

        Integer consumerThreads = configManager.getCustomInteger(systemType, "consumerThreads");
        if(consumerThreads != null && consumerThreads < 10) {
            log.warn("The number of configured threads is too small, which can lead to unpredictable behavior.");
        }
        if (consumerThreads == null)  {
            log.warn("consumerThreads is not set, use default: {}",DEFAULT_NUM_OF_THREADS );
            consumerThreads = DEFAULT_NUM_OF_THREADS; // default
        }

        ExecutorService baseExecutor = Executors.newFixedThreadPool(consumerThreads);
        ContextAwareExecutor consumerExecutor = new ContextAwareExecutor(baseExecutor, systemType, "PRIMARY");

        // Create Reactive Performance Management instance
        PerformanceManagement performanceInstance = new PerformanceManagement(
            ftpPathRepository,
            counterMySqlRepository,
            neRepository,
            counterCounterCatRepository,
            extraFieldRepository,
            paramsCodeRepository,
            notificationEngineRedisService,
            kafkaTemplate,
            kafkaMessageRepository,
            systemConfig,
            dataLakeProcess,
            systemType,
            configManager,
            consumerExecutor,
            fileStateRepository,
            redisTemplate,
            ftpClientPool
        );

        performanceInstances.put(systemType, performanceInstance);
        consumerExecutors.put(systemType, consumerExecutor);
        consumerExecutor.submit(performanceInstance);

        log.info("✅ System {} started with REACTIVE mode ({} threads)",
            systemType.getCode(), consumerThreads);
    }

    @Scheduled(fixedRate = 60000) // Every 60 seconds
    public void logAllStats() {
        log.info("📊 === MULTI-SYSTEM STATUS ===");
        for (SystemType systemType : SystemType.values()) {
            PerformanceManagement consumer = performanceInstances.get(systemType);
            if (consumer != null) {
                log.info("🔹 System {}: ", systemType.getCode());
                // consumer.logStats(); // TODO: Implement logStats method
            }
        }
        log.info("🧵 Total Active Threads: {}", Thread.activeCount());
        log.info("=================================");
    }

    public PerformanceManagement getPerformanceInstance(SystemType systemType) {
        return performanceInstances.get(systemType);
    }

    public ContextAwareExecutor getExecutor(SystemType systemType) {
        return consumerExecutors.get(systemType);
    }

}
