package com.example.bankcards.service;

import com.example.bankcards.dto.CardCreateRequest;
import com.example.bankcards.dto.CardFilter;
import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.CardDto;

public interface CardService {
    CardDto createForUser(Long userId, CardCreateRequest req);
    PageDto<CardDto> listMy(Long userId, int page, int size, CardFilter filter);
    PageDto<CardDto> listAll(int page, int size, CardFilter filter);
    CardDto block(Long cardId);
    CardDto activate(Long cardId);
    void delete(Long cardId);
}
