package za.co.securebank.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PagingUtils {

    public static final int MAX_PAGE_SIZE = 100;

    private PagingUtils() {
    }

    /** Creates a bounded pageable (page &gt;= 0, 1 &lt;= size &lt;= 100) sorted newest first. */
    public static Pageable newestFirst(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    public static Pageable plain(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
