package com.viettel.ems.perfomance.service;

import com.viettel.ems.perfomance.object.FileProcessingStateObject;
import com.viettel.ems.perfomance.object.FileProcessingStateObject.ProcessingState;
import com.viettel.ems.perfomance.object.FtpServerObject;
import com.viettel.ems.perfomance.repository.FileProcessingStateJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.List;

/**
 * Moves completed files through the pipeline stages
 *
 * File Movement Flow:
 * 1. /Access/5G/Others/ (initial)
 * 2. /Access/5G/ClickHouse/ (after MySQL processed)
 * 3. /Access/5G/Done/ (after both MySQL + ClickHouse processed)
 *
 * Fault-tolerant: Retries on failure, tracks state in DB
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompletedFileMover {

    private final FileProcessingStateJdbcRepository stateRepository;
    private final GenericObjectPool<FTPClient> ftpClientPool;

    /**
     * Move files that have been processed by MySQL to ClickHouse folder
     * Runs every 30 seconds
     */
    @Scheduled(fixedRate = 30000)
    public void moveToClickHouseFolder() {
        List<FileProcessingStateObject> mysqlProcessedFiles =
            stateRepository.findByMysqlProcessedAndClickhouseProcessed(true, false, 50);

        if (mysqlProcessedFiles.isEmpty()) {
            return;
        }

        log.info("Found {} files to move to ClickHouse folder", mysqlProcessedFiles.size());

        Flux.fromIterable(mysqlProcessedFiles)
            .parallel(5) // Move 5 files in parallel
            .runOn(Schedulers.boundedElastic())
            .flatMap(this::moveToClickHouse)
            .sequential()
            .doOnNext(moved -> log.info("Moved to ClickHouse: {}", moved.getFileName()))
            .doOnError(err -> log.error("Error moving files to ClickHouse", err))
            .subscribe();
    }

    /**
     * Move files that have been processed by both MySQL and ClickHouse to Done folder
     * Runs every 60 seconds
     */
    @Scheduled(fixedRate = 60000)
    public void moveToDoneFolder() {
        List<FileProcessingStateObject> bothProcessedFiles =
            stateRepository.findByMysqlProcessedAndClickhouseProcessed(true, true, 50);

        if (bothProcessedFiles.isEmpty()) {
            return;
        }

        log.info("Found {} files to move to Done folder", bothProcessedFiles.size());

        Flux.fromIterable(bothProcessedFiles)
            .parallel(5) // Move 5 files in parallel
            .runOn(Schedulers.boundedElastic())
            .flatMap(this::moveToDone)
            .sequential()
            .doOnNext(moved -> log.info("Moved to Done: {}", moved.getFileName()))
            .doOnError(err -> log.error("Error moving files to Done", err))
            .subscribe();
    }

    /**
     * Move file from Others/ to ClickHouse/
     */
    private Mono<FileProcessingStateObject> moveToClickHouse(FileProcessingStateObject fileState) {
        return Mono.fromCallable(() -> {
            FTPClient ftpClient = null;
            try {
                ftpClient = ftpClientPool.borrowObject();

                String sourcePath = fileState.getFtpPath() + "/" + fileState.getFileName();
                String targetPath = fileState.getFtpPath().replace("/Others/", "/ClickHouse/") +
                    "/" + fileState.getFileName();

                // Ensure target directory exists
                createDirectoryIfNotExists(ftpClient, extractDirectory(targetPath));

                // Rename (move) file
                boolean success = ftpClient.rename(sourcePath, targetPath);
                if (!success) {
                    throw new IOException("FTP rename failed: " + ftpClient.getReplyString());
                }

                log.debug("Moved {} -> {}", sourcePath, targetPath);

                // Update state
                stateRepository.updateState(fileState.getId(), ProcessingState.COMPLETED);

                return fileState;

            } finally {
                if (ftpClient != null) {
                    try {
                        ftpClientPool.returnObject(ftpClient);
                    } catch (Exception e) {
                        log.error("Error returning FTP client to pool", e);
                    }
                }
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(err -> {
            log.error("Failed to move file to ClickHouse: {}", fileState.getFileName(), err);
            stateRepository.updateStateError(fileState.getId(), "Move to ClickHouse failed: " + err.getMessage());
            return Mono.empty();
        });
    }

    /**
     * Move file from ClickHouse/ to Done/
     */
    private Mono<FileProcessingStateObject> moveToDone(FileProcessingStateObject fileState) {
        return Mono.fromCallable(() -> {
            FTPClient ftpClient = null;
            try {
                ftpClient = ftpClientPool.borrowObject();

                String sourcePath = fileState.getFtpPath().replace("/Others/", "/ClickHouse/") +
                    "/" + fileState.getFileName();
                String targetPath = fileState.getFtpPath().replace("/Others/", "/Done/") +
                    "/" + fileState.getFileName();

                // Ensure target directory exists
                createDirectoryIfNotExists(ftpClient, extractDirectory(targetPath));

                // Rename (move) file
                boolean success = ftpClient.rename(sourcePath, targetPath);
                if (!success) {
                    throw new IOException("FTP rename failed: " + ftpClient.getReplyString());
                }

                log.debug("Moved {} -> {}", sourcePath, targetPath);

                // Update state: MOVED (final state)
                stateRepository.updateStateMoved(fileState.getId());

                return fileState;

            } finally {
                if (ftpClient != null) {
                    try {
                        ftpClientPool.returnObject(ftpClient);
                    } catch (Exception e) {
                        log.error("Error returning FTP client to pool", e);
                    }
                }
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(err -> {
            log.error("Failed to move file to Done: {}", fileState.getFileName(), err);
            stateRepository.updateStateError(fileState.getId(), "Move to Done failed: " + err.getMessage());
            return Mono.empty();
        });
    }

    /**
     * Create FTP directory if it doesn't exist
     */
    private void createDirectoryIfNotExists(FTPClient ftpClient, String directory) throws IOException {
        String[] dirs = directory.split("/");
        String currentPath = "";

        for (String dir : dirs) {
            if (dir.isEmpty()) continue;

            currentPath += "/" + dir;

            // Try to change to directory
            if (!ftpClient.changeWorkingDirectory(currentPath)) {
                // Directory doesn't exist, create it
                if (!ftpClient.makeDirectory(currentPath)) {
                    log.warn("Failed to create directory: {}", currentPath);
                } else {
                    log.debug("Created directory: {}", currentPath);
                }
            }
        }

        // Reset to root
        ftpClient.changeWorkingDirectory("/");
    }

    /**
     * Extract directory from full path
     */
    private String extractDirectory(String fullPath) {
        int lastSlash = fullPath.lastIndexOf('/');
        return lastSlash > 0 ? fullPath.substring(0, lastSlash) : "/";
    }

    /**
     * Manual trigger to move specific file (for recovery)
     */
    public Mono<Void> moveFileManually(Long fileId, String targetFolder) {
        return Mono.fromRunnable(() -> {
            FileProcessingStateObject fileState = stateRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

            if ("ClickHouse".equals(targetFolder)) {
                moveToClickHouse(fileState).block();
            } else if ("Done".equals(targetFolder)) {
                moveToDone(fileState).block();
            } else {
                throw new IllegalArgumentException("Invalid target folder: " + targetFolder);
            }
        });
    }
}
