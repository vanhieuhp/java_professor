package dev.hieunv.outboxpattern.repository;

import dev.hieunv.outboxpattern.entity.MailMessageEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MailMessageRepository extends MongoRepository<MailMessageEntity, String> {
}
