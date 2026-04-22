package dev.hieunv.bankos.service.outbox;

import dev.hieunv.bankos.model.OutboxEvent;
import dev.hieunv.bankos.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
public class OutboxRelayService {
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor outboxEventProcessor;

    // Polls every 2 seconds — in production this would be Debezium CDC
    // or a dedicated relay process reading the outbox table
    @Scheduled(fixedDelay = 2000)
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingEvents();
        if (pending.isEmpty()) return;

        log.info("[Relay] Found {} pending events", pending.size());

        for (OutboxEvent event : pending) {
            outboxEventProcessor.processEvent(event);
        }
    }

}
