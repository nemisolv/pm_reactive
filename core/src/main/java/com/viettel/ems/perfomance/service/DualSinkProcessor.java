package com.viettel.ems.perfomance.service;

import com.viettel.ems.perfomance.common.ErrorCode;
import com.viettel.ems.perfomance.object.CounterObject;
import com.viettel.ems.perfomance.object.FileProcessingStateObject;
import com.viettel.ems.perfomance.object.FileProcessingStateObject.ProcessingState;
import com.viettel.ems.perfomance.repository.CounterMySqlRepository;
import com.viettel.ems.perfomance.repository.FileProcessingStateJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dual-sink processor: Fork stream to both MySQL and ClickHouse
 *
 * Architecture:
 * - MySQL: Direct JDBC batch insert (blocking, but in thread pool)
 * - ClickHouse: Kafka producer (non-blocking)
 *
 * Both sinks process in parallel, file marked COMPLETED when both done
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DualSinkProcessor {

    private final FileProcessingStateJdbcRepository stateRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CounterMySqlRepository counterMySqlRepository;

    private static final String CLICKHOUSE_TOPIC = "pm-counter-data";

    /**
     * Process parsed batches and write to both MySQL and ClickHouse
     *
     * @param parsedBatches Stream of parsed counter batches
     * @return Mono<ProcessingResult>
     */
    public Mono<ProcessingResult> processDualSink(Flux<StreamingCounterParser.ParsedBatch> parsedBatches) {
        AtomicBoolean mysqlSuccess = new AtomicBoolean(false);
        AtomicBoolean clickhouseSuccess = new AtomicBoolean(false);

        return parsedBatches
            .flatMap(batch -> {
                // Fork the stream to both sinks in parallel
                Mono<Void> mysqlSink = writeBatchToMySQL(batch)
                    .doOnSuccess(v -> mysqlSuccess.set(true))
                    .subscribeOn(Schedulers.boundedElastic());

                Mono<Void> clickhouseSink = writeBatchToClickHouse(batch)
                    .doOnSuccess(v -> clickhouseSuccess.set(true))
                    .subscribeOn(Schedulers.parallel());

                // Wait for both to complete
                return Mono.when(mysqlSink, clickhouseSink)
                    .thenReturn(batch);
            })
            .then(Mono.defer(() -> {
                // After all batches processed, return result
                return Mono.just(ProcessingResult.builder()
                    .mysqlSuccess(mysqlSuccess.get())
                    .clickhouseSuccess(clickhouseSuccess.get())
                    .build());
            }))
            .doOnNext(result -> {
                log.info("Dual-sink processing result: MySQL={}, ClickHouse={}",
                    result.isMysqlSuccess(), result.isClickhouseSuccess());
            });
    }

    /**
     * Write batch to MySQL (blocking, but in thread pool)
     */
    private Mono<Void> writeBatchToMySQL(StreamingCounterParser.ParsedBatch batch) {
        return Mono.fromRunnable(() -> {
            try {
                FileProcessingStateObject fileState = batch.getFileState();
                List<?> counters = batch.getParsedCounters();

                log.debug("Writing batch {}/{} to MySQL: {} counters",
                    batch.getBatchNumber(), batch.getTotalBatches(), counters.size());

                // Use existing MySQL insert logic
                @SuppressWarnings("unchecked")
                ArrayList<CounterObject> counterList = (ArrayList<CounterObject>) counters;
                ErrorCode result = counterMySqlRepository.addCounter(counterList);

                if (result != ErrorCode.NO_ERROR && result != ErrorCode.ERROR_DUPLICATE_RECORD) {
                    throw new RuntimeException("MySQL insert failed with error: " + result);
                }

                // Update state when last batch is done
                if (batch.isLastBatch()) {
                    stateRepository.updateMysqlProcessed(fileState.getId());
                    log.info("MySQL processing completed for file: {}", fileState.getFileName());
                }

            } catch (Exception e) {
                log.error("Failed to write batch to MySQL", e);
                throw new RuntimeException("MySQL insert failed", e);
            }
        });
    }

    /**
     * Write batch to ClickHouse via Kafka (non-blocking)
     */
    private Mono<Void> writeBatchToClickHouse(StreamingCounterParser.ParsedBatch batch) {
        return Mono.fromRunnable(() -> {
            try {
                FileProcessingStateObject fileState = batch.getFileState();
                List<?> counters = batch.getParsedCounters();

                log.debug("Sending batch {}/{} to Kafka: {} counters",
                    batch.getBatchNumber(), batch.getTotalBatches(), counters.size());

                // Serialize and send to Kafka
                for (Object counter : counters) {
                    String key = fileState.getFileName();
                    String value = serializeCounter(counter);
                    kafkaTemplate.send(CLICKHOUSE_TOPIC, key, value);
                }

                // Update state when last batch is done
                if (batch.isLastBatch()) {
                    stateRepository.updateClickhouseProcessed(fileState.getId());
                    log.info("ClickHouse processing completed for file: {}", fileState.getFileName());
                }

            } catch (Exception e) {
                log.error("Failed to write batch to ClickHouse", e);
                throw new RuntimeException("ClickHouse send failed", e);
            }
        });
    }

    /**
     * Update file state when both sinks complete
     */
    public Mono<Void> updateFileStateOnCompletion(FileProcessingStateObject fileState, ProcessingResult result) {
        return Mono.fromRunnable(() -> {
            if (result.isMysqlSuccess() && result.isClickhouseSuccess()) {
                stateRepository.updateBothProcessed(fileState.getId());
                log.info("Both sinks completed for file: {}", fileState.getFileName());
            } else {
                String error = String.format("Partial failure: MySQL=%s, ClickHouse=%s",
                    result.isMysqlSuccess(), result.isClickhouseSuccess());
                stateRepository.updateStateError(fileState.getId(), error);
            }
        });
    }

    /**
     * Serialize counter object to JSON for Kafka
     */
    private String serializeCounter(Object counter) {
        // Simple toString for now - in production use Jackson or similar
        // TODO: Implement proper JSON serialization
        return counter.toString();
    }

    /**
     * Processing result
     */
    @lombok.Data
    @lombok.Builder
    public static class ProcessingResult {
        private boolean mysqlSuccess;
        private boolean clickhouseSuccess;

        public boolean isFullSuccess() {
            return mysqlSuccess && clickhouseSuccess;
        }
    }
}
