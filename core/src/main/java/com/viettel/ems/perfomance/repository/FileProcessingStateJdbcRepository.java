package com.viettel.ems.perfomance.repository;

import com.viettel.ems.perfomance.object.FileProcessingStateObject;
import com.viettel.ems.perfomance.object.FileProcessingStateObject.ProcessingState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JDBC-based repository for high-performance file state tracking
 * Optimized for batch operations
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class FileProcessingStateJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * OPTIMIZED: Batch load all filenames for a path
     * Returns Set for O(1) lookup
     */
    public Set<String> findFileNamesByPathAndServer(String systemType, String ftpServerKey, String ftpPath) {
        String sql = "SELECT file_name FROM file_processing_state " +
                    "WHERE system_type = ? AND ftp_server_key = ? AND ftp_path = ?";

        List<String> fileNames = jdbcTemplate.query(sql,
            (rs, rowNum) -> rs.getString("file_name"),
            systemType, ftpServerKey, ftpPath
        );

        return new HashSet<>(fileNames);
    }

    /**
     * Batch insert new files
     */
    public int[] saveAll(List<FileProcessingStateObject> files) {
        String sql = "INSERT INTO file_processing_state " +
                    "(system_type, ftp_server_key, ftp_path, file_name, file_size, state, " +
                    " mysql_processed, clickhouse_processed, retry_count, max_retry, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                FileProcessingStateObject file = files.get(i);
                ps.setString(1, file.getSystemType());
                ps.setString(2, file.getFtpServerKey());
                ps.setString(3, file.getFtpPath());
                ps.setString(4, file.getFileName());
                ps.setLong(5, file.getFileSize() != null ? file.getFileSize() : 0);
                ps.setString(6, file.getState().name());
                ps.setBoolean(7, file.isMysqlProcessed());
                ps.setBoolean(8, file.isClickhouseProcessed());
                ps.setInt(9, file.getRetryCount());
                ps.setInt(10, file.getMaxRetry());
            }

            @Override
            public int getBatchSize() {
                return files.size();
            }
        });
    }

    /**
     * Find files by state with limit
     */
    public List<FileProcessingStateObject> findByStateOrderByCreatedAt(ProcessingState state, int limit) {
        String sql = "SELECT * FROM file_processing_state " +
                    "WHERE state = ? " +
                    "ORDER BY created_at ASC " +
                    "LIMIT ?";

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> FileProcessingStateObject.fromResultSet(rs),
            state.name(), limit
        );
    }

    /**
     * Find files by multiple states
     */
    public List<FileProcessingStateObject> findByStateIn(List<ProcessingState> states) {
        if (states.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(",", Collections.nCopies(states.size(), "?"));
        String sql = "SELECT * FROM file_processing_state WHERE state IN (" + placeholders + ")";

        Object[] params = states.stream().map(Enum::name).toArray();

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> FileProcessingStateObject.fromResultSet(rs),
            params
        );
    }

    /**
     * Reset files stuck in DOWNLOADING state
     */
    public int resetStuckDownloading(LocalDateTime threshold) {
        String sql = "UPDATE file_processing_state " +
                    "SET state = 'PENDING', retry_count = retry_count + 1, updated_at = NOW() " +
                    "WHERE state = 'DOWNLOADING' " +
                    "AND download_start_time < ? " +
                    "AND retry_count < max_retry";

        return jdbcTemplate.update(sql, Timestamp.valueOf(threshold));
    }

    /**
     * Reset files stuck in PARSING state
     */
    public int resetStuckParsing(LocalDateTime threshold) {
        String sql = "UPDATE file_processing_state " +
                    "SET state = 'PENDING', retry_count = retry_count + 1, updated_at = NOW() " +
                    "WHERE state = 'PARSING' " +
                    "AND parse_start_time < ? " +
                    "AND retry_count < max_retry";

        return jdbcTemplate.update(sql, Timestamp.valueOf(threshold));
    }

    /**
     * Update state by ID
     */
    public void updateState(Long id, ProcessingState state) {
        String sql = "UPDATE file_processing_state " +
                    "SET state = ?, updated_at = NOW() " +
                    "WHERE id = ?";

        jdbcTemplate.update(sql, state.name(), id);
    }

    /**
     * Update state with error
     */
    public void updateStateError(Long id, String errorMessage) {
        String sql = "UPDATE file_processing_state " +
                    "SET state = 'ERROR', error_message = ?, retry_count = retry_count + 1, updated_at = NOW() " +
                    "WHERE id = ?";

        jdbcTemplate.update(sql, errorMessage, id);
    }

    /**
     * Mark both MySQL and ClickHouse as processed
     */
    public void updateBothProcessed(Long id) {
        String sql = "UPDATE file_processing_state " +
                    "SET mysql_processed = TRUE, clickhouse_processed = TRUE, " +
                    "state = 'COMPLETED', updated_at = NOW() " +
                    "WHERE id = ?";

        jdbcTemplate.update(sql, id);
    }

    /**
     * Update state to MOVED
     */
    public void updateStateMoved(Long id) {
        String sql = "UPDATE file_processing_state " +
                    "SET state = 'MOVED', file_moved_time = NOW(), updated_at = NOW() " +
                    "WHERE id = ?";

        jdbcTemplate.update(sql, id);
    }

    /**
     * Find files that are completed but not moved yet
     */
    public List<FileProcessingStateObject> findByBothProcessedAndNotMoved() {
        String sql = "SELECT * FROM file_processing_state " +
                    "WHERE state = 'COMPLETED' " +
                    "AND mysql_processed = TRUE " +
                    "AND clickhouse_processed = TRUE";

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> FileProcessingStateObject.fromResultSet(rs)
        );
    }

    /**
     * Find files stuck in a specific state for too long
     */
    public List<FileProcessingStateObject> findStuckInState(ProcessingState state, LocalDateTime threshold) {
        String sql = "SELECT * FROM file_processing_state " +
                    "WHERE state = ? " +
                    "AND updated_at < ?";

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> FileProcessingStateObject.fromResultSet(rs),
            state.name(), Timestamp.valueOf(threshold)
        );
    }

    /**
     * Count files stuck for more than threshold
     */
    public long countStuckFiles(LocalDateTime threshold) {
        String sql = "SELECT COUNT(*) FROM file_processing_state " +
                    "WHERE updated_at < ? " +
                    "AND state NOT IN ('COMPLETED', 'MOVED')";

        Long count = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.valueOf(threshold));
        return count != null ? count : 0;
    }

    /**
     * Count files by state
     */
    public Map<ProcessingState, Long> countByState() {
        String sql = "SELECT state, COUNT(*) as count FROM file_processing_state GROUP BY state";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        Map<ProcessingState, Long> stateCounts = new HashMap<>();
        for (Map<String, Object> row : results) {
            String stateStr = (String) row.get("state");
            Long count = ((Number) row.get("count")).longValue();
            stateCounts.put(ProcessingState.valueOf(stateStr), count);
        }

        return stateCounts;
    }

    /**
     * Find partially processed files (MySQL done but not ClickHouse)
     */
    public List<FileProcessingStateObject> findPartialProcessed(LocalDateTime threshold) {
        String sql = "SELECT * FROM file_processing_state " +
                    "WHERE mysql_processed = TRUE " +
                    "AND clickhouse_processed = FALSE " +
                    "AND updated_at < ?";

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> FileProcessingStateObject.fromResultSet(rs),
            Timestamp.valueOf(threshold)
        );
    }

    /**
     * Update download start time
     */
    public void updateDownloadStartTime(Long id) {
        String sql = "UPDATE file_processing_state " +
                    "SET download_start_time = NOW(), state = 'DOWNLOADING', updated_at = NOW() " +
                    "WHERE id = ?";

        jdbcTemplate.update(sql, id);
    }

    /**
     * Update parse start time
     */
    public void updateParseStartTime(Long id) {
        String sql = "UPDATE file_processing_state " +
                    "SET parse_start_time = NOW(), state = 'PARSING', updated_at = NOW() " +
                    "WHERE id = ?";

        jdbcTemplate.update(sql, id);
    }
}
