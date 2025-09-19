package dev.hieunv.outboxpattern.repository;


import dev.hieunv.outboxpattern.entity.OutboxMailEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxMailRepository extends MongoRepository<OutboxMailEntity, String> {
}
