package com.classpulse.common.response;

import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
public class PageMeta {

    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageMeta(int page, int size, long totalElements, int totalPages) {
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public static PageMeta from(Page<?> page) {
        return new PageMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
