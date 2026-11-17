package ae.nest.crm.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable pagination envelope for list endpoints. */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, List<T> mapped) {
        return new PageResponse<>(
                mapped, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
