package com.example.bankcards.entity;

import com.example.bankcards.entity.enums.CardStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "cards")
@EqualsAndHashCode(of = "id")
@EntityListeners(AuditingEntityListener.class)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cards_id_seq")
    @SequenceGenerator(name = "cards_id_seq", sequenceName = "cards_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "pan_ciphertext", nullable = false, length = 512)
    private String panCiphertext;

    @Column(name = "pan_hash", nullable = false, length = 64, unique = true)
    private String panHash;

    @Column(name = "pan_last4", nullable = false, length = 4)
    private String panLast4;

    @Column(name = "expiry", nullable = false, length = 7)
    private YearMonth expiry;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status;

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}