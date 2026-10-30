package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.problem.util.TagNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagNormalizerTest {

    @Test
    @DisplayName("normalize: trims whitespace and drops blank tags")
    void normalize_trimsAndDropsBlank() {
        assertThat(TagNormalizer.normalize(Arrays.asList("  Array  ", "", "   ", "Math")))
                .containsExactly("Array", "Math");
    }

    @Test
    @DisplayName("normalize: null input yields empty list")
    void normalize_nullYieldsEmpty() {
        assertThat(TagNormalizer.normalize(null)).isEmpty();
        assertThat(TagNormalizer.normalize(List.of())).isEmpty();
    }

    @Test
    @DisplayName("normalize: dedupes case-insensitively but keeps first-seen display casing")
    void normalize_dedupesCaseInsensitively() {
        assertThat(TagNormalizer.normalize(List.of("Array", "array", "ARRAY", "Hash Table")))
                .containsExactly("Array", "Hash Table");
    }

    @Test
    @DisplayName("normalize: preserves original order of first appearance")
    void normalize_preservesOrder() {
        assertThat(TagNormalizer.normalize(List.of("Math", "Array", "Math")))
                .containsExactly("Math", "Array");
    }

    @Test
    @DisplayName("normalize: skips null elements")
    void normalize_skipsNulls() {
        assertThat(TagNormalizer.normalize(Arrays.asList("Array", null, "Math")))
                .containsExactly("Array", "Math");
    }

    @Test
    @DisplayName("normalize: rejects tags longer than the column limit")
    void normalize_rejectsTooLongTag() {
        String tooLong = "x".repeat(TagNormalizer.MAX_TAG_LENGTH + 1);

        assertThatThrownBy(() -> TagNormalizer.normalize(List.of(tooLong)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tag must not exceed 50 characters");
    }

    @Test
    @DisplayName("normalize: accepts a tag exactly at the column limit")
    void normalize_acceptsTagAtLimit() {
        String atLimit = "x".repeat(TagNormalizer.MAX_TAG_LENGTH);

        assertThat(TagNormalizer.normalize(List.of(atLimit))).containsExactly(atLimit);
    }
}
