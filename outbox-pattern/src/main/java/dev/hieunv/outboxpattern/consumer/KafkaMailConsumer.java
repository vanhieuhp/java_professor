package dev.hieunv.outboxpattern.consumer;

import dev.hieunv.outboxpattern.dto.MailMessageKafka;
import dev.hieunv.outboxpattern.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class KafkaMailConsumer {

    private final MailService mailService;

    /*
    *   1. Message comes → listenMailEvent.
        2. If success → done.
        3. If Exception → retries (with backoff, up to max attempts).
        4. If still failing after retries → message lands in DLT.
        5. retryMailEvent method handles the failed message (with exception metadata).
    * */
    @RetryableTopic(
            attempts = "${kafka.mail.max-retry}",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true",
            include = {Exception.class}
    )
    @KafkaListener(
            topics = "${kafka.mail.topic}",
            groupId = "${kafka.mail.group-id}",
            properties = {"spring.json.value.default.type=com.epay.service.notify.dto.MailMessageKafka"},
            concurrency = "3",
            containerFactory = "jsonMessageKafkaListenerContainerFactory"
    )
    public void listenMailEvent(@Payload MailMessageKafka message) {
        log.info("Received a mail message - code: {} - meta: {} - payload: {}", message.getMessageCode(), message.getMeta(), message.getPayload());
        mailService.processMail(message.getPayload());
    }

    @DltHandler
    public void retryMailEvent(@Payload MailMessageKafka message) {
        log.error("Failed to process mail message - code: {} - meta: {} - mailId: {}",
                message.getMessageCode(),
                message.getMeta(),
                message.getPayload().getMailId());
        mailService.retryMailEvent(message.getPayload());
    }
}
