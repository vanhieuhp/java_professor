package dev.hieunv.repository;

import dev.hieunv.domain.entity.CardInfoEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardInfoRepository extends CrudRepository<CardInfoEntity, String> {
}
