package com.viettel.ems.perfomance.repository;

import com.viettel.ems.perfomance.common.ErrorCode;
import com.viettel.ems.perfomance.object.clickhouse.NewFormatCounterObject;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class KafkaMessageRepository {

    public void sendToClickhouse() {

    }

    public ErrorCode sendToClickhouse(NewFormatCounterObject counterObject, Map<String, List<Map<String, Object>>> dataPushClickhouses) {
        return ErrorCode.ERROR_UNKNOWN;
    }

    public boolean sendMessageToKafka(String producerNewFormatTopics, NewFormatCounterObject counterObject) {
        return true;
    }
}
