package dev.hieunv.outboxpattern.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.result.UpdateResult;
import dev.hieunv.outboxpattern.constant.EventType;
import dev.hieunv.outboxpattern.constant.KafkaMessageCode;
import dev.hieunv.outboxpattern.constant.OutboxStatus;
import dev.hieunv.outboxpattern.dto.MailMessageRequest;
import dev.hieunv.outboxpattern.dto.kafka.KafkaMessageBuilder;
import dev.hieunv.outboxpattern.entity.MailMessageEntity;
import dev.hieunv.outboxpattern.entity.OutboxMailEntity;
import dev.hieunv.outboxpattern.repository.MailMessageRepository;
import dev.hieunv.outboxpattern.repository.OutboxMailRepository;
import dev.hieunv.outboxpattern.utils.DateUtils;
import dev.hieunv.outboxpattern.utils.JsonParserUtils;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private final OutboxMailRepository outboxMailRepository;
    private final MailMessageRepository mailMessageRepository;
    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.application.name}")
    private String serviceId;

    @Value("${kafka.mail.topic}")
    private String mailTopic;

    @Value("${kafka.mail.max-retry}")
    private int maxRetry;

    private final static int DEFAULT_BATCH_SIZE = 500;

    @Override
    public void sendMail(MailMessageRequest request) {
        MailMessageEntity mailMessage = MailMessageEntity.builder()
                .sender(request.getSender())
                .senderName(request.getSenderName())
                .recipients(request.getRecipients())
                .cc(request.getCc())
                .bcc(request.getBcc())
                .subject(request.getSubject())
                .body(request.getBody())
                .html(request.isHtml())
                .sent(false)
                .createdAt(DateUtils.timeNow())
                .updatedAt(DateUtils.timeNow())
                .build();

        mailMessageRepository.save(mailMessage);

        // Ghi outbox_event
        OutboxMailEntity event = OutboxMailEntity.builder()
                .mailId(mailMessage.getId())
                .subject(request.getSubject())
                .data(request.toString())
                .retryCount(0)
                .status(OutboxStatus.NEW.getCode())
                .errorMessage(null)
                .createdAt(DateUtils.timeNow())
                .updatedAt(DateUtils.timeNow())
                .build();

        outboxMailRepository.save(event);
        log.info("Mail sending...");
    }

    @Override
    @SneakyThrows
    public void processMail(MailMessageRequest mailEvent) {
        try {
            MailMessageEntity mail = mailMessageRepository.findById(mailEvent.getMailId()).orElse(null);
            if (Objects.isNull(mail)) {
                throw new Exception("Mail message not found");
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String senderName = StringUtils.hasText(mailEvent.getSenderName()) ? mailEvent.getSenderName() : mailEvent.getSender();

            helper.setFrom(mailEvent.getSender(), senderName);
            helper.setTo(mailEvent.getRecipients().toArray(new String[0]));

            if (!CollectionUtils.isEmpty(mailEvent.getCc())) {
                helper.setCc(mailEvent.getCc().toArray(new String[0]));
            }
            if (!CollectionUtils.isEmpty(mailEvent.getBcc())) {
                helper.setBcc(mailEvent.getBcc().toArray(new String[0]));
            }

            helper.setSubject(mailEvent.getSubject());
            helper.setText(mailEvent.getBody(), mailEvent.isHtml());
            message.setSentDate(new Date());

            mailSender.send(message);

            mail.setSent(true);
            mail.setUpdatedAt(DateUtils.timeNow());
            mailMessageRepository.save(mail);
            log.info("Email sent successfully to: {}", mailEvent.getRecipients());
        } catch (Exception e) {
            log.error("Failed to send email, mailId: {}, outboxMailId: {}", mailEvent.getMailId(), mailEvent.getOutboxEventId());
            log.error("Exception: {}", e.getMessage());
            OutboxMailEntity outboxMail = outboxMailRepository.findById(mailEvent.getOutboxEventId()).orElse(null);
            if (outboxMail == null) {
                log.warn("Mail event {} not found", mailEvent.getOutboxEventId());
                return;
            }
            outboxMail.setUpdatedAt(DateUtils.timeNow());
            outboxMail.setRetryCount(outboxMail.getRetryCount() + 1);
            outboxMail.setErrorMessage(e.getMessage());
            outboxMailRepository.save(outboxMail);
            throw new RuntimeException("Mail send failed: " + e.getMessage(), e);
        }

    }

    @Scheduled(fixedDelay = 1000)
    public void processOutbox() {
        Query query = new Query(Criteria.where("status").is(OutboxStatus.NEW.getCode()))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"))
                .limit(DEFAULT_BATCH_SIZE);

        List<OutboxMailEntity> mails = mongoTemplate.find(query, OutboxMailEntity.class);
        if (!mails.isEmpty()) {
            List<String> ids = mails.stream().map(OutboxMailEntity::getId).toList();

            Query updateQuery = new Query(
                    Criteria.where("_id").in(ids)
                            .and("status").is(OutboxStatus.NEW.getCode())
            );

            Update update = new Update()
                    .set("status", OutboxStatus.SUCCESS.getCode())
                    .set("updatedAt", DateUtils.timeNow());

            UpdateResult result = mongoTemplate.updateMulti(updateQuery, update, OutboxMailEntity.class);

            if (result.getModifiedCount() > 0) {
                List<String> lockedIds = ids.subList(0, (int) result.getModifiedCount());
                mails.stream()
                        .filter(m -> lockedIds.contains(m.getId()))
                        .forEach(this::sendMessageToKafka);
            }
        }
    }

    private void sendMessageToKafka(OutboxMailEntity mail) {
        try {
            MailMessageRequest payload = JsonParserUtils.mapToObject(mail.getData(), MailMessageRequest.class);
            payload.setOutboxEventId(mail.getId());
            payload.setMailId(mail.getMailId());

            var message = KafkaMessageBuilder.build(
                    serviceId,
                    EventType.EMAIL,
                    KafkaMessageCode.MAIL_MESSAGE.getCode(),
                    payload
            );
            kafkaTemplate.send(mailTopic, message);
            log.info("Produced a mail message to topic: {}, value: {}", mailTopic, payload);
        } catch (JsonProcessingException e) {
            log.error("Fail to parse mail request data: {}", mail.getData(), e);
        } catch (KafkaException e) {
            log.error("Failed to produce the message to topic: {}", mailTopic, e);
        }
    }

    @Override
    public void retryMailEvent(MailMessageRequest payload) {
        OutboxMailEntity outboxMail = outboxMailRepository.findById(payload.getOutboxEventId()).orElse(null);
        if (outboxMail == null) {
            log.warn("Mail event {} not found", payload.getOutboxEventId());
            return;
        }

        outboxMail.setUpdatedAt(DateUtils.timeNow());
        outboxMail.setStatus(OutboxStatus.FAILED.getCode());
        outboxMail.setRetryCount(maxRetry);
        if (outboxMail.getErrorMessage() == null) {
            outboxMail.setErrorMessage("Max retry exceeded");
        }
        outboxMailRepository.save(outboxMail);
    }
}
