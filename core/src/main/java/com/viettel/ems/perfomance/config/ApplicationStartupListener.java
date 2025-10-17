package com.viettel.ems.perfomance.config;


import com.viettel.ems.perfomance.config.kafkamonitor.ConsumerHeaderBeatMonitor;
import com.viettel.ems.perfomance.parser.ParseCounterDataONT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public class ApplicationStartupListener {
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final ConsumerHeaderBeatMonitor consumerHeaderBeatMonitor;
    private final ConfigManager configManager;

    @EventListener
    public void onApplicationEvent(ApplicationEvent event) {
        boolean isDeployOnt = configManager.isDeployed(SystemType.SYSTEM_ONT);
        if(!isDeployOnt) {
//            kafkaListenerEndpointRegistry.getListenerContainer(PerformanceManagement.PERFORMANCE_CONSUMER_TOPIC_ID).start();
            return;
        }

        try {
            log.info("ONT_MESSAGE_LISTENER STarting .......");
            MessageListenerContainer messageListnerContainer = kafkaListenerEndpointRegistry.getListenerContainer(ParseCounterDataONT.ONT_MESSAGE_LISTENER);
            if(!messageListnerContainer.isRunning()) {
                messageListnerContainer.start();
                consumerHeaderBeatMonitor.updateHeartbeat(ParseCounterDataONT.ONT_MESSAGE_LISTENER);
            }
        }catch (Exception e) {
            log.error("start listener error {}, {}", e.getMessage(), e.getStackTrace());
        }



    }

}