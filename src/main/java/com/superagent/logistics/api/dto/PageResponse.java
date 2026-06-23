package com.superagent.logistics.api.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, pages);
    }

    public static int normalizePage(int page) {
        return Math.max(1, page);
    }

    public static int normalizeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
