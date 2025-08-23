package com.example.bankcards.service;

import com.example.bankcards.dto.PageDto;
import com.example.bankcards.dto.TransferDto;
import com.example.bankcards.dto.TransferRequest;

public interface TransferService {

    TransferDto initiate(Long currentUserId, TransferRequest request);

    TransferDto cancel(Long currentUserId, Long transferId);

    PageDto<TransferDto> listMy(Long userId, int page, int size);

    PageDto<TransferDto> listAll(int page, int size);
}
