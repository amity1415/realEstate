package ae.nest.crm.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds a bounded Pageable so clients cannot request unbounded result sets. */
public final class PageSupport {

    public static final int MAX_SIZE = 200;

    private PageSupport() {}

    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 25 : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
