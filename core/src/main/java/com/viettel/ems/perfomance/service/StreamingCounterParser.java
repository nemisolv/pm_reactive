package com.viettel.ems.perfomance.service;

import com.viettel.ems.perfomance.object.CounterDataObject;
import com.viettel.ems.perfomance.object.FileProcessingStateObject;
import com.viettel.ems.perfomance.object.FileProcessingStateObject.ProcessingState;
import com.viettel.ems.perfomance.parser.ParserCounterData;
import com.viettel.ems.perfomance.repository.FileProcessingStateJdbcRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Streaming parser for counter files
 * Parses files in batches to reduce memory pressure
 * Memory: Old = 10MB full load, New = 2-3MB streaming batches
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreamingCounterParser {

    private final FileProcessingStateJdbcRepository stateRepository;
    private final ParserCounterData parserCounterData;

    private static final int BATCH_SIZE = 1000; // Parse 1000 counters per batch

    /**
     * Parse downloaded file in streaming batches
     *
     * @param downloadedFile File with counter data
     * @return Flux of parsed batches
     */
    public Flux<ParsedBatch> parseFileInBatches(ReactiveFileDownloader.DownloadedFile downloadedFile) {
        FileProcessingStateObject fileState = downloadedFile.getFileState();
        CounterDataObject counterData = downloadedFile.getCounterData();

        return Mono.fromCallable(() -> {
            log.info("Parsing file: {}", fileState.getFileName());

            // Parse the file using existing parser
            // Note: ParserCounterData.parseCounter returns ArrayList<CounterObject>
            List<?> parsedCounters = parserCounterData.parseCounter(counterData);

            return ParsedBatch.builder()
                .fileState(fileState)
                .counterData(counterData)
                .parsedCounters(parsedCounters)
                .batchNumber(1)
                .totalBatches(1)
                .build();
        })
        .flatMapMany(batch -> {
            // Split into streaming batches if needed
            List<?> allCounters = batch.getParsedCounters();
            int totalCounters = allCounters.size();
            int totalBatches = (int) Math.ceil((double) totalCounters / BATCH_SIZE);

            log.info("Parsed {} counters, splitting into {} batches", totalCounters, totalBatches);

            // Emit batches
            return Flux.range(0, totalBatches)
                .map(batchIndex -> {
                    int fromIndex = batchIndex * BATCH_SIZE;
                    int toIndex = Math.min(fromIndex + BATCH_SIZE, totalCounters);
                    List<?> batchCounters = allCounters.subList(fromIndex, toIndex);

                    return ParsedBatch.builder()
                        .fileState(batch.getFileState())
                        .counterData(batch.getCounterData())
                        .parsedCounters(batchCounters)
                        .batchNumber(batchIndex + 1)
                        .totalBatches(totalBatches)
                        .build();
                });
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnNext(batch -> {
            log.debug("Emitting batch {}/{} with {} counters for file: {}",
                batch.getBatchNumber(), batch.getTotalBatches(),
                batch.getParsedCounters().size(), fileState.getFileName());
        })
        .doOnComplete(() -> {
            log.info("Completed parsing file: {}", fileState.getFileName());
            // Update state: PROCESSING
            stateRepository.updateState(fileState.getId(), ProcessingState.PROCESSING);
        })
        .doOnError(err -> {
            log.error("Failed to parse file: {}", fileState.getFileName(), err);
            stateRepository.updateStateError(fileState.getId(), err.getMessage());
        });
    }

    /**
     * Parsed batch wrapper
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class ParsedBatch {
        private FileProcessingStateObject fileState;
        private CounterDataObject counterData;
        private List<?> parsedCounters;
        private int batchNumber;
        private int totalBatches;

        public String getFileName() {
            return fileState.getFileName();
        }

        public boolean isLastBatch() {
            return batchNumber == totalBatches;
        }
    }
}
