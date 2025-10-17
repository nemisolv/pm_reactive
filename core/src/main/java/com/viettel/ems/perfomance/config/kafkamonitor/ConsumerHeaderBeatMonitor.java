package com.viettel.ems.perfomance.config.kafkamonitor;

import com.viettel.ems.perfomance.parser.ParseCounterDataONT;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;


@Component
@RequiredArgsConstructor
@Slf4j
public class ConsumerHeaderBeatMonitor {
    @Value("${spring.kafka.consumer.idle-before-reconnect-ms}")
    private Integer idleBeforeReconnectMs;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    private ConcurrentHashMap<String, Long> lastHeartbeatMap = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, Boolean> isDone = new ConcurrentHashMap<>();

    public void updateHeartbeat(String listenerId) {
        lastHeartbeatMap.put(listenerId, System.currentTimeMillis());
        isDone.put(listenerId,true);
    }

    public void startProcessMessage(String listenerId) {
        isDone.put(listenerId, false);
    }

    public Long getLastHeaderBeat(String listenerId) {return lastHeartbeatMap.get(listenerId);}



    public void chekConsumerHealth() {
        new Thread(new Runnable()  {
            @SneakyThrows
            @Override
            public void run() {
                Thread.sleep(idleBeforeReconnectMs);
                log.error("consumer " + ParseCounterDataONT.ONT_MESSAGE_LISTENER + " is inactive.Restarting...");
                MessageListenerContainer listernContainer = kafkaListenerEndpointRegistry.getListenerContainer( ParseCounterDataONT.ONT_MESSAGE_LISTENER);

                if(listernContainer != null) {
                    try {
                        listernContainer.stop();
                    }catch(Exception ex) {
                        log.error("Stop consumer");
                    }
                    listernContainer.start();
                    updateHeartbeat(ParseCounterDataONT.ONT_MESSAGE_LISTENER);
                }
            }
        }).start();
    }


    public void stopContainerListenerWithConsumer(String listenerId, Consumer<?, ?> consumer) {
        try {
            consumer.close();
        }catch(Exception e) {
            log.error("stop consumer {}.{}", e.getMessage(), e.getStackTrace());
        }

        try {
            kafkaListenerEndpointRegistry.getListenerContainer(listenerId).stop();

        }catch(Exception e) {
            log.error("Stop container error: {}. {}", e.getMessage(), e.getStackTrace());
        }
    }


}