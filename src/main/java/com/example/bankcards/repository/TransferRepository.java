package com.example.bankcards.repository;

import com.example.bankcards.entity.Transfer;
import com.example.bankcards.entity.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Page<Transfer> findByStatusAndExpiresAtBefore(TransferStatus status, LocalDateTime before, Pageable pageable);
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
    Page<Transfer> findByInitiator_Id(Long initiatorId, Pageable pageable);
    boolean existsByFromCard_Id(Long cardId);
    boolean existsByToCard_Id(Long cardId);
    Optional<Transfer> findByInitiator_IdAndIdempotencyKey(Long initiatorId, String idempotencyKey);

}
