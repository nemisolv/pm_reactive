package com.viettel.ems.perfomance.object;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Object to track file processing state through the pipeline
 * Enables fault-tolerance and recovery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileProcessingStateObject {

    private Long id;
    private String systemType;
    private String ftpServerKey;
    private String ftpPath;
    private String fileName;
    private Long fileSize;
    private ProcessingState state;
    private boolean mysqlProcessed;
    private boolean clickhouseProcessed;
    private LocalDateTime downloadStartTime;
    private LocalDateTime downloadEndTime;
    private LocalDateTime parseStartTime;
    private LocalDateTime parseEndTime;
    private LocalDateTime mysqlInsertTime;
    private LocalDateTime clickhouseInsertTime;
    private LocalDateTime fileMovedTime;
    private String errorMessage;
    private int retryCount;
    private int maxRetry;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum ProcessingState {
        PENDING,
        DOWNLOADING,
        PARSING,
        PROCESSING,
        COMPLETED,
        MOVED,
        ERROR
    }

    public static FileProcessingStateObject fromResultSet(ResultSet rs) throws SQLException {
        return FileProcessingStateObject.builder()
            .id(rs.getLong("id"))
            .systemType(rs.getString("system_type"))
            .ftpServerKey(rs.getString("ftp_server_key"))
            .ftpPath(rs.getString("ftp_path"))
            .fileName(rs.getString("file_name"))
            .fileSize(rs.getLong("file_size"))
            .state(ProcessingState.valueOf(rs.getString("state")))
            .mysqlProcessed(rs.getBoolean("mysql_processed"))
            .clickhouseProcessed(rs.getBoolean("clickhouse_processed"))
            .downloadStartTime(rs.getTimestamp("download_start_time") != null ?
                rs.getTimestamp("download_start_time").toLocalDateTime() : null)
            .downloadEndTime(rs.getTimestamp("download_end_time") != null ?
                rs.getTimestamp("download_end_time").toLocalDateTime() : null)
            .parseStartTime(rs.getTimestamp("parse_start_time") != null ?
                rs.getTimestamp("parse_start_time").toLocalDateTime() : null)
            .parseEndTime(rs.getTimestamp("parse_end_time") != null ?
                rs.getTimestamp("parse_end_time").toLocalDateTime() : null)
            .mysqlInsertTime(rs.getTimestamp("mysql_insert_time") != null ?
                rs.getTimestamp("mysql_insert_time").toLocalDateTime() : null)
            .clickhouseInsertTime(rs.getTimestamp("clickhouse_insert_time") != null ?
                rs.getTimestamp("clickhouse_insert_time").toLocalDateTime() : null)
            .fileMovedTime(rs.getTimestamp("file_moved_time") != null ?
                rs.getTimestamp("file_moved_time").toLocalDateTime() : null)
            .errorMessage(rs.getString("error_message"))
            .retryCount(rs.getInt("retry_count"))
            .maxRetry(rs.getInt("max_retry"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
            .build();
    }
}
