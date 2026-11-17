package ma.pfe.stock.common.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void computesTotalPagesAndMapsContent() {
        PageResult<Integer> page = PageResult.of(List.of(1, 2, 3), 0, 3, 7);

        assertThat(page.totalPages()).isEqualTo(3);
        PageResult<String> mapped = page.map(i -> "#" + i);
        assertThat(mapped.content()).containsExactly("#1", "#2", "#3");
        assertThat(mapped.totalElements()).isEqualTo(7);
    }

    @Test
    void emptyPageHasZeroPages() {
        assertThat(PageResult.of(List.of(), 0, 20, 0).totalPages()).isZero();
    }
}
