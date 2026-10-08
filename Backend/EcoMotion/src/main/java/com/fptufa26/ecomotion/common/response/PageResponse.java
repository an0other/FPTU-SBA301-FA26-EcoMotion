package com.fptufa26.ecomotion.common.response;

import java.util.List;
import java.util.function.Function;

/**
 * Offset/limit page envelope returned inside {@link ApiResponse#data()}.
 *
 * <p>Offset-based (not keyset) on purpose: the renter list jumps around by page number and the
 * fleet is small, so the simpler model wins. The client sends {@code offset} and {@code limit};
 * this carries back everything needed to render pagination without a second call.
 *
 * @param items    the slice for the requested page
 * @param total    total number of rows matching the filters (all pages)
 * @param offset   rows skipped, echoed from the request
 * @param limit    page size, echoed from the request
 * @param hasMore  {@code true} when rows exist after this slice
 */
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

    /**
     * Builds a page by slicing {@code allItems} in memory.
     *
     * <p>Filtering currently happens after mapping (keyword hits brand/model/plate/provider, price
     * compares the cheapest plan), so the full filtered list has to exist before it can be cut. The
     * slice is taken here rather than in SQL for that reason.
     */
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
