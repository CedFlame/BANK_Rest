package com.example.bankcards.repository;

import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.enums.CardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {
    boolean existsByPanHash(String panHash);

    Page<Card> findByUser_Id(Long userId, Pageable pageable);

    Page<Card> findByUser_IdAndStatus(Long userId, CardStatus status, Pageable pageable);

    Page<Card> findByStatus(CardStatus status, Pageable pageable);
}
