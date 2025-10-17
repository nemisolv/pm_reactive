# Integration Guide - Reactive Pipeline với Multi-Tenant Architecture

## 🎯 Vấn đề

User chỉ ra vấn đề quan trọng: SystemManager khởi tạo 4 instances riêng biệt (4GA, 5GA, ONT, 5G Core), mỗi instance có:
- Thread pool riêng (ContextAwareExecutor)
- TenantContext riêng (SystemType + datasource)
- PerformanceManagement riêng

Các component reactive của tôi ban đầu dùng `@Component` và `@Scheduled` → chạy global, không tách biệt context → **SAI**.

## ✅ Giải pháp

### Option 1: Integrate vào PerformanceManagement (RECOMMENDED)

Thêm reactive pipeline vào class PerformanceManagement hiện tại, giữ nguyên kiến trúc multi-tenant.

#### Step 1: Add dependencies to PerformanceManagement constructor

```java
public class PerformanceManagement extends Thread {
    // Existing fields...

    // NEW: Add reactive components
    private final FileProcessingStateJdbcRepository fileStateRepository;
    private final StringRedisTemplate redisTemplate;
    private final GenericObjectPool<FTPClient> ftpClientPool;

    public PerformanceManagement(
        // ... existing params ...
        FileProcessingStateJdbcRepository fileStateRepository,
        StringRedisTemplate redisTemplate,
        GenericObjectPool<FTPClient> ftpClientPool
    ) {
        // ... existing init ...
        this.fileStateRepository = fileStateRepository;
        this.redisTemplate = redisTemplate;
        this.ftpClientPool = ftpClientPool;
    }
}
```

#### Step 2: Initialize reactive components in initialize()

```java
public void initialize() {
    try {
        // ... existing initialization ...

        // NEW: Initialize reactive components per SystemType
        initializeReactivePipeline();

    } catch (Exception e) {
        log.error(e.getMessage());
    }
}

private void initializeReactivePipeline() {
    log.info("Initializing reactive pipeline for system: {}", systemType.getCode());

    // Create reactive components for THIS systemType
    RedisOptimizedFileScanner fileScanner = new RedisOptimizedFileScanner(
        systemType,
        null, // ftpServerKey - will scan all servers
        null, // ftpPath - will scan all paths
        fileStateRepository,
        ftpPathRepository,
        redisTemplate,
        ftpClientPool
    );

    ReactiveFileDownloader fileDownloader = new ReactiveFileDownloader(
        fileStateRepository,
        ftpClientPool
    );

    StreamingCounterParser streamingParser = new StreamingCounterParser(
        fileStateRepository,
        parserCounterData
    );

    DualSinkProcessor dualSinkProcessor = new DualSinkProcessor(
        fileStateRepository,
        kafkaTemplate,
        counterMySqlRepository
    );

    CompletedFileMover fileMover = new CompletedFileMover(
        fileStateRepository,
        ftpClientPool
    );

    // Bootstrap Redis from MySQL
    fileScanner.bootstrapRedisFromDatabase();

    // Schedule reactive tasks using ContextAwareExecutor
    scheduleReactiveTasks(fileScanner, fileDownloader, streamingParser, dualSinkProcessor, fileMover);
}

private void scheduleReactiveTasks(
    RedisOptimizedFileScanner fileScanner,
    ReactiveFileDownloader fileDownloader,
    StreamingCounterParser streamingParser,
    DualSinkProcessor dualSinkProcessor,
    CompletedFileMover fileMover
) {
    // File scanning every 10 seconds
    executorService.getScheduledExecutorService().scheduleAtFixedRate(
        () -> {
            try {
                fileScanner.scanAndRegisterNewFiles();
            } catch (Exception e) {
                log.error("[{}] Error in file scan", systemType.getCode(), e);
            }
        },
        0, 10, TimeUnit.SECONDS
    );

    // Download and process pipeline every 5 seconds
    executorService.getScheduledExecutorService().scheduleAtFixedRate(
        () -> {
            try {
                processReactivePipeline(fileDownloader, streamingParser, dualSinkProcessor);
            } catch (Exception e) {
                log.error("[{}] Error in reactive pipeline", systemType.getCode(), e);
            }
        },
        5, 5, TimeUnit.SECONDS
    );

    // File mover every 30 seconds
    executorService.getScheduledExecutorService().scheduleAtFixedRate(
        () -> {
            try {
                fileMover.moveToClickHouseFolder();
                fileMover.moveToDoneFolder();
            } catch (Exception e) {
                log.error("[{}] Error in file mover", systemType.getCode(), e);
            }
        },
        30, 30, TimeUnit.SECONDS
    );

    // Recovery every 5 minutes
    executorService.getScheduledExecutorService().scheduleAtFixedRate(
        () -> {
            try {
                fileScanner.recoverStuckFiles();
            } catch (Exception e) {
                log.error("[{}] Error in recovery", systemType.getCode(), e);
            }
        },
        60, 300, TimeUnit.SECONDS
    );
}

private void processReactivePipeline(
    ReactiveFileDownloader fileDownloader,
    StreamingCounterParser streamingParser,
    DualSinkProcessor dualSinkProcessor
) {
    List<FileProcessingStateObject> pendingFiles =
        fileStateRepository.findByStateOrderByCreatedAt(ProcessingState.PENDING, 100);

    if (pendingFiles.isEmpty()) {
        return;
    }

    log.info("[{}] Processing {} files through reactive pipeline",
        systemType.getCode(), pendingFiles.size());

    Flux.fromIterable(pendingFiles)
        .parallel(10)
        .runOn(Schedulers.boundedElastic())
        .flatMap(fileState ->
            fileDownloader.downloadFileAsync(fileState)
                .flatMapMany(streamingParser::parseFileInBatches)
                .transform(dualSinkProcessor::processDualSink)
                .flatMap(result ->
                    dualSinkProcessor.updateFileStateOnCompletion(fileState, result)
                        .thenReturn(result)
                )
                .onErrorResume(err -> {
                    log.error("[{}] Pipeline error for: {}",
                        systemType.getCode(), fileState.getFileName(), err);
                    fileStateRepository.updateStateError(fileState.getId(), err.getMessage());
                    return Mono.empty();
                })
        )
        .sequential()
        .blockLast(); // Block since we're in scheduled executor
}
```

#### Step 3: Update SystemManager to inject dependencies

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemManager {
    // ... existing fields ...

    // NEW: Add reactive dependencies
    private final FileProcessingStateJdbcRepository fileStateRepository;
    private final StringRedisTemplate redisTemplate;
    private final GenericObjectPool<FTPClient> ftpClientPool;

    public void startSystem(SystemType systemType) {
        // ... existing code ...

        // Create PerformanceManagement with reactive components
        PerformanceManagement performanceInstance = new PerformanceManagement(
            // ... existing params ...
            fileStateRepository,  // NEW
            redisTemplate,        // NEW
            ftpClientPool         // NEW
        );

        // ... rest of existing code ...
    }
}
```

### Option 2: Separate Reactive Service (ALTERNATIVE)

Nếu không muốn modify PerformanceManagement, tạo service riêng:

```java
@Component
@RequiredArgsConstructor
public class ReactivePerformanceService {
    private final SystemManager systemManager;
    private final FileProcessingStateJdbcRepository stateRepository;
    // ... other deps ...

    private final Map<SystemType, ReactivePerformanceManager> reactiveManagers = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        for (SystemType systemType : SystemType.values()) {
            if (configManager.isDeployed(systemType)) {
                startReactiveManager(systemType);
            }
        }
    }

    private void startReactiveManager(SystemType systemType) {
        ContextAwareExecutor executor = systemManager.getExecutor(systemType);

        ReactivePerformanceManager manager = new ReactivePerformanceManager(
            systemType,
            executor,
            stateRepository,
            // ... other deps ...
        );

        reactiveManagers.put(systemType, manager);
        executor.submit(manager);
    }
}
```

## 📋 Checklist Integration

### 1. Remove `@Component` annotations
- [ ] RedisOptimizedFileScanner
- [ ] ReactiveFileDownloader (giữ hoặc xóa `@Scheduled`)
- [ ] StreamingCounterParser
- [ ] DualSinkProcessor
- [ ] CompletedFileMover
- [ ] ReactivePerformancePipeline (có thể xóa toàn bộ file này)

### 2. Update constructors
- [ ] Add SystemType parameter to each component
- [ ] Remove @RequiredArgsConstructor, use explicit constructors

### 3. Remove `@Scheduled` annotations
- [ ] Thay bằng manual calls từ ContextAwareExecutor
- [ ] Sử dụng executorService.getScheduledExecutorService()

### 4. Add to PerformanceManagement
- [ ] Add reactive component fields
- [ ] Update constructor
- [ ] Add initializeReactivePipeline()
- [ ] Add scheduleReactiveTasks()
- [ ] Add processReactivePipeline()

### 5. Update SystemManager
- [ ] Inject reactive dependencies
- [ ] Pass to PerformanceManagement constructor

### 6. Configuration
- [ ] FtpConnectionPoolConfig vẫn giữ @Configuration (global is OK)
- [ ] application-redis.yml không đổi

## 🎯 Kiến trúc sau khi integrate

```
SystemManager (@Component, singleton)
├── SystemType.4GA
│   ├── ContextAwareExecutor (20 threads)
│   ├── PerformanceManagement (instance 1)
│   │   ├── Old pipeline (queues, threads)
│   │   └── NEW: Reactive pipeline
│   │       ├── RedisOptimizedFileScanner
│   │       ├── ReactiveFileDownloader
│   │       ├── StreamingCounterParser
│   │       ├── DualSinkProcessor
│   │       └── CompletedFileMover
│   └── TenantContext: systemType=4GA, datasource=PRIMARY
│
├── SystemType.5GA
│   ├── ContextAwareExecutor (20 threads)
│   ├── PerformanceManagement (instance 2)
│   │   └── NEW: Reactive pipeline (separate instance)
│   └── TenantContext: systemType=5GA, datasource=PRIMARY
│
├── SystemType.ONT
│   └── ... (keep old pipeline only, skip reactive)
│
└── SystemType.5G_CORE
    └── ...
```

## ⚠️ Quan trọng

1. **Context propagation**: ContextAwareExecutor tự động set TenantContext cho mọi task
2. **Thread safety**: Mỗi SystemType có thread pool riêng, không xung đột
3. **Datasource routing**: TenantContextHolder.getCurrentSystem() → routing đúng DB
4. **Old vs New**: Cả hai pipeline có thể chạy cùng lúc, hoặc chỉ enable reactive cho 5GA

## 🚀 Migration Strategy

### Phase 1: Testing (Week 1)
- [ ] Integrate reactive pipeline vào PerformanceManagement
- [ ] Enable CHỈ cho SystemType.5GA
- [ ] Old pipeline vẫn chạy song song
- [ ] Monitor performance metrics

### Phase 2: Validation (Week 2)
- [ ] Compare old vs new: throughput, latency, memory
- [ ] Nếu OK: Disable old pipeline cho 5GA
- [ ] Enable reactive cho 4GA

### Phase 3: Full Rollout (Week 3)
- [ ] Enable reactive cho all systems (except ONT if needed)
- [ ] Remove old pipeline code (optional)

## 📝 Code Example

File: `PerformanceManagement.java`

```java
public void initialize() {
    try {
        // ... existing code ...

        // NEW: Check if reactive pipeline is enabled for this system
        boolean useReactivePipeline = configManager.getCustomBoolean(
            systemType, "useReactivePipeline");

        if (useReactivePipeline) {
            log.info("🚀 Enabling REACTIVE pipeline for {}", systemType.getCode());
            initializeReactivePipeline();
        } else {
            log.info("📊 Using LEGACY pipeline for {}", systemType.getCode());
            // Old pipeline initialization (existing code)
        }

    } catch (Exception e) {
        log.error(e.getMessage());
    }
}
```

Configuration (application.yml):

```yaml
system:
  5ga:
    useReactivePipeline: true   # Enable new pipeline
    consumerThreads: 30          # More threads for parallel
  4ga:
    useReactivePipeline: false  # Keep old pipeline
    consumerThreads: 20
  ont:
    useReactivePipeline: false  # ONT uses special logic
```

## ✅ Summary

**Cách tốt nhất**: Integrate reactive pipeline vào PerformanceManagement, sử dụng ContextAwareExecutor để schedule tasks. Mỗi SystemType sẽ có reactive components riêng biệt với context đúng.

**Advantages**:
- ✅ Tách biệt context cho mỗi system
- ✅ Giữ nguyên kiến trúc multi-tenant
- ✅ Old và new pipeline có thể coexist
- ✅ Easy rollback nếu có vấn đề
- ✅ Configuration-driven (enable/disable per system)
