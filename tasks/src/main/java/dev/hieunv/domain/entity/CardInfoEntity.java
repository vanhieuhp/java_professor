package dev.hieunv.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.redis.core.RedisHash;

@Getter
@Setter
@ToString(exclude = "cardDetails")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash
@Entity
@Table(name = "card_info")
public class CardInfoEntity {

    @Id
    private String id;

    @Column(name = "card_details")
    private String cardDetails;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;
}
