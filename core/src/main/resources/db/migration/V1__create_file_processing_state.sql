-- Create file_processing_state table for tracking pipeline state
-- This enables fault-tolerance and recovery

CREATE TABLE IF NOT EXISTS file_processing_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- File identification
    system_type VARCHAR(20) NOT NULL COMMENT '5GA, 4GA, ONT',
    ftp_server_key VARCHAR(100) NOT NULL COMMENT 'FTP server identifier',
    ftp_path VARCHAR(500) NOT NULL COMMENT 'Path on FTP server',
    file_name VARCHAR(255) NOT NULL COMMENT 'File name',
    file_size BIGINT COMMENT 'File size in bytes',

    -- Processing state
    state VARCHAR(20) NOT NULL COMMENT 'PENDING, DOWNLOADING, PARSING, PROCESSING, COMPLETED, MOVED, ERROR',
    mysql_processed BOOLEAN NOT NULL DEFAULT FALSE,
    clickhouse_processed BOOLEAN NOT NULL DEFAULT FALSE,

    -- Timestamps for each stage
    download_start_time DATETIME COMMENT 'When download started',
    download_end_time DATETIME COMMENT 'When download completed',
    parse_start_time DATETIME COMMENT 'When parsing started',
    parse_end_time DATETIME COMMENT 'When parsing completed',
    mysql_insert_time DATETIME COMMENT 'When MySQL insert completed',
    clickhouse_insert_time DATETIME COMMENT 'When ClickHouse send completed',
    file_moved_time DATETIME COMMENT 'When file moved to Done folder',

    -- Error handling
    error_message TEXT COMMENT 'Error message if failed',
    retry_count INT NOT NULL DEFAULT 0 COMMENT 'Number of retry attempts',
    max_retry INT NOT NULL DEFAULT 3 COMMENT 'Maximum retry attempts',

    -- Audit
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Constraints
    UNIQUE KEY uk_file (system_type, ftp_server_key, ftp_path, file_name),
    INDEX idx_state (state, system_type),
    INDEX idx_updated (updated_at),
    INDEX idx_download_time (download_start_time),
    INDEX idx_parse_time (parse_start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Track file processing state for fault-tolerance';
