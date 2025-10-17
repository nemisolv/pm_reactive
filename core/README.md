#  REACTIVE PIPELINE 

1. **PerformanceManagementReactive.java**
    - Khởi tạo và schedule reactive components

2. **RedisOptimizedFileScanner.java**
    - Scan FTP với Redis Pipeline

3. **ReactiveFileDownloader.java**
    - Download 10 files parallel
    - FTP connection pooling

4. **StreamingCounterParser.java**
    - Parse in batches

5. **DualSinkProcessor.java**
    - Fork to MySQL + ClickHouse parallel

6. **CompletedFileMover.java**
    - Move files Others/ → ClickHouse/ → Done/

7. **FtpConnectionPoolConfig.java**
    - Apache Commons Pool2 for FTP

8. **FileProcessingStateJdbcRepository.java**
    - Pure JDBC (no JPA)
    - Batch operations

9. **FileProcessingStateObject.java**
    - JDBC entity with state machine


10. **SystemManager.java**
    - Added: Reactive dependencies injection
    - Instantiate PerformanceManagement without managing by Spring bean (manually initiate)


17. **V1__create_file_processing_state.sql** (NEW)
    - Database migration script

## 🏗️ Architecture

```
SystemManager
|
|
bootstrap multi-system: 5GA, 4GA, 5G CORE, ONT
├──  PerformanceManagement with its own state 
│   │
│   ├── RedisOptimizedFileScanner (scan FTP every 10s)
│   │   └── Redis Pipeline: 5000 files in 50-100ms
│   │
│   ├── ReactiveFileDownloader (every 5s)
│   │   └── Download 10 files parallel with FTP pool
│   │
│   ├── StreamingCounterParser
│   │   └── Parse in batches (100000 counters)
│   │
│   ├── DualSinkProcessor
│   │   ├── MySQL (blocking in thread pool)
│   │   └── ClickHouse ( convert to NewFormatObjectKafka -> publish to Kafka non-blocking)
│   │
│   └── CompletedFileMover (every 30s)
│       ├── Move to ClickHouse/ (ClickHouse_processed=true)
│       └── Move to Done/ (both (ClickHouse + MySQL) processed=true)
│
```

---

## 🔍 Monitoring

### Redis

```bash
docker exec -it pm-redis redis-cli

KEYS pm:files:*
SMEMBERS "pm:files:5GA:ftpserver:/path/"
SCARD "pm:files:5GA:ftpserver:/path/"
```

### MySQL

```sql
-- Count by state
SELECT state, COUNT(*) FROM file_processing_state
WHERE system_type = '5GA' GROUP BY state;

-- Currently processing
SELECT * FROM file_processing_state
WHERE state IN ('DOWNLOADING', 'PARSING', 'PROCESSING')
ORDER BY updated_at DESC LIMIT 20;

-- Performance stats
SELECT
    AVG(TIMESTAMPDIFF(SECOND, download_start_time, download_end_time)) as avg_download,
    AVG(TIMESTAMPDIFF(SECOND, parse_start_time, parse_end_time)) as avg_parse
FROM file_processing_state
WHERE system_type = '5GA';
```

### Logs

```bash
tail -f logs/application.log | grep "REACTIVE"
tail -f logs/application.log | grep "Processing.*files through reactive pipeline"
```

---

## 🛡️ Fault Tolerance

### Auto-recovery (every 5 minutes)

- DOWNLOADING > 10 min → reset to PENDING
- PARSING > 30 min → reset to PENDING
- Redis failure → fallback to MySQL
- FTP failure → retry 3 times with backoff

---

**🚀 Happy Reactive Processing! 🚀**