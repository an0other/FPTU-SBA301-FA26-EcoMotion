package com.fptufa26.ecomotion.common.response;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> items,
        long total,
        int offset,
        int limit,
        boolean hasMore
) {

    public static <T> PageResponse<T> of(List<T> allItems, int offset, int limit) {
        return from(allItems, offset, limit, Function.identity());
    }

    public static <S, T> PageResponse<T> from(List<S> allItems, int offset, int limit,
                                              Function<S, T> mapper) {
        int size = allItems.size();
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);

        if (safeOffset >= size) {
            return new PageResponse<>(List.of(), size, safeOffset, safeLimit, false);
        }

        int end = Math.min(size, safeOffset + safeLimit);
        List<T> items = allItems.subList(safeOffset, end).stream().map(mapper).toList();
        return new PageResponse<>(items, size, safeOffset, safeLimit, end < size);
    }
}
