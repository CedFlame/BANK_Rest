package com.example.bankcards.mapper;

import com.example.bankcards.dto.PageDto;
import org.springframework.data.domain.Page;

import java.util.List;

public final class PageDtoMapper {
    private PageDtoMapper() {}
    public static <T, U> PageDto<U> toPageDto(Page<T> page, List<U> content) {
        return PageDto.<U>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
