package com.viettel.ems.perfomance.service;

import com.viettel.ems.perfomance.object.CounterDataObject;
import com.viettel.ems.perfomance.object.FileProcessingStateObject;
import com.viettel.ems.perfomance.object.FileProcessingStateObject.ProcessingState;
import com.viettel.ems.perfomance.object.FtpServerObject;
import com.viettel.ems.perfomance.repository.FileProcessingStateJdbcRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Reactive file downloader with FTP connection pooling
 * Downloads files in parallel (non-blocking I/O)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReactiveFileDownloader {

    private final FileProcessingStateJdbcRepository stateRepository;
    private final GenericObjectPool<FTPClient> ftpClientPool;

    /**
     * Download PENDING files reactively
     * Runs every 10 seconds, processes up to 100 files in parallel
     */
    @Scheduled(fixedRate = 10000)
    public void downloadPendingFiles() {
        List<FileProcessingStateObject> pendingFiles =
            stateRepository.findByStateOrderByCreatedAt(ProcessingState.PENDING, 100);

        if (pendingFiles.isEmpty()) {
            return;
        }

        log.info("Found {} pending files to download", pendingFiles.size());

        Flux.fromIterable(pendingFiles)
            .parallel(10) // Download 10 files in parallel
            .runOn(Schedulers.boundedElastic())
            .flatMap(this::downloadFileAsync)
            .sequential()
            .doOnNext(downloaded -> log.info("Downloaded: {}", downloaded.getFileName()))
            .doOnError(err -> log.error("Download pipeline error", err))
            .subscribe();
    }

    /**
     * Download a single file asynchronously
     */
    public Mono<DownloadedFile> downloadFileAsync(FileProcessingStateObject fileState) {
        return Mono.fromCallable(() -> {
            // Update state: DOWNLOADING
            stateRepository.updateDownloadStartTime(fileState.getId());

            FTPClient ftpClient = null;
            try {
                ftpClient = ftpClientPool.borrowObject();

                String fullPath = fileState.getFtpPath() + "/" + fileState.getFileName();
                log.debug("Downloading: {}", fullPath);

                InputStream inputStream = ftpClient.retrieveFileStream(fullPath);
                if (inputStream == null) {
                    throw new IOException("Cannot open FTP stream: " + fullPath +
                        ", Reply: " + ftpClient.getReplyString());
                }

                // Read file based on type
                CounterDataObject counterData;
                String fileName = fileState.getFileName().toUpperCase();

                if (fileName.contains("_GNODEB_") && fileName.contains("RU")) {
                    // CSV format - read as lines
                    List<String> lines = new ArrayList<>();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            lines.add(line);
                        }
                    }
                    counterData = new CounterDataObject(
                        buildFtpServerObject(fileState),
                        fileState.getFtpPath(),
                        fileState.getFileName(),
                        null,
                        lines
                    );
                } else {
                    // Binary format - read as bytes
                    byte[] data = inputStream.readAllBytes();
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    counterData = new CounterDataObject(
                        buildFtpServerObject(fileState),
                        fileState.getFtpPath(),
                        fileState.getFileName(),
                        buffer,
                        null
                    );
                }

                ftpClient.completePendingCommand();

                log.info("Downloaded {} ({} bytes)", fileState.getFileName(),
                    fileState.getFileSize());

                // Update state: PARSING
                stateRepository.updateParseStartTime(fileState.getId());

                return DownloadedFile.builder()
                    .fileState(fileState)
                    .counterData(counterData)
                    .build();

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
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
            .filter(throwable -> throwable instanceof IOException))
        .onErrorResume(err -> {
            log.error("Failed to download file: {}", fileState.getFileName(), err);
            stateRepository.updateStateError(fileState.getId(), err.getMessage());
            return Mono.empty();
        });
    }

    private FtpServerObject buildFtpServerObject(FileProcessingStateObject fileState) {
        // Parse FTP server key back to FtpServerObject
        // Format: "host_port_user_pass"
        String[] parts = fileState.getFtpServerKey().split("_");
        if (parts.length >= 4) {
            return new FtpServerObject(
                parts[0],
                Integer.parseInt(parts[1]),
                parts[2],
                parts[3]
            );
        }
        // Fallback
        return new FtpServerObject("localhost", 21, "anonymous", "");
    }

    /**
     * Downloaded file wrapper
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class DownloadedFile {
        private FileProcessingStateObject fileState;
        private CounterDataObject counterData;

        public String getFileName() {
            return fileState.getFileName();
        }
    }
}
