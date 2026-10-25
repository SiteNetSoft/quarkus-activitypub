package org.sitenetsoft.quarkus.activitypub.store;

import java.util.List;

/**
 * One page of a collection.
 *
 * @param items items on this page
 * @param page  1-based page number
 * @param size  requested page size
 * @param total items in the whole collection
 */
public record Page<T>(List<T> items, int page, int size, long total) {

    public Page {
        items = List.copyOf(items);
    }

    /** True when items exist after this page. */
    public boolean hasNext() {
        return (long) page * size < total;
    }

    /** The page of {@code all} for a 1-based {@code page}; empty items beyond the end. */
    public static <T> Page<T> of(List<T> all, int page, int size) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("page and size must be >= 1");
        }
        long from = (long) (page - 1) * size;
        List<T> items = from >= all.size() ? List.of() : all.subList((int) from, (int) Math.min(all.size(), from + size));
        return new Page<>(items, page, size, all.size());
    }
}
