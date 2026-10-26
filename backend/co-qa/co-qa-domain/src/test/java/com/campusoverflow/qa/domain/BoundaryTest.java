package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** QA-T15：输入边界与守卫规则（错误码对应 6.3.3 / docs/错误码.md）。 */
class BoundaryTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void questionBodyBoundaries() {
        assertThatThrownBy(() -> MarkdownBody.ofQuestion("a".repeat(19)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-1002"));
        assertThat(MarkdownBody.ofQuestion("a".repeat(20)).value()).hasSize(20);
        assertThat(MarkdownBody.ofQuestion("a".repeat(30_000)).value()).hasSize(30_000);
        assertThatThrownBy(() -> MarkdownBody.ofQuestion("a".repeat(30_001)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void answerBodyBoundaries() {
        assertThatThrownBy(() -> MarkdownBody.ofAnswer("a".repeat(9)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-1002"));
        assertThat(MarkdownBody.ofAnswer("a".repeat(10)).value()).hasSize(10);
        assertThat(MarkdownBody.ofAnswer("a".repeat(30_000)).value()).hasSize(30_000);
        assertThatThrownBy(() -> MarkdownBody.ofAnswer("a".repeat(30_001)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void titleBoundaries() {
        assertThatThrownBy(() -> new Title("a".repeat(9)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-1001"));
        assertThat(new Title("a".repeat(10)).value()).hasSize(10);
        assertThat(new Title("a".repeat(150)).value()).hasSize(150);
        assertThatThrownBy(() -> new Title("a".repeat(151)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void tagCountBoundaries() {
        assertThatThrownBy(() -> Tags.of(List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-1005"));
        assertThatThrownBy(() -> Tags.of(List.of("a", "b", "c", "d", "e", "f")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(Tags.of(List.of("a")).names()).containsExactly("a");
        // 6 个标签中 1 个重复，去重后恰好 5 个，合法
        assertThat(Tags.of(List.of("java", "Java", "a", "b", "c", "d")).names()).hasSize(5);
    }

    @Test
    void tagNameBoundaries() {
        assertThatThrownBy(() -> new TagName(null))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-1004"));
        assertThatThrownBy(() -> new TagName("a".repeat(31)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new TagName("abc def"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(new TagName("#Java").value()).isEqualTo("java");
        assertThat(new TagName("C++").value()).isEqualTo("c++");
        assertThat(new TagName("a".repeat(30)).value()).hasSize(30);
    }

    @Test
    void commentBodyBoundaries() {
        assertThatThrownBy(() -> Comment.post(TargetType.ANSWER, 50, 10, 1, null, "", NOW))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-1003"));
        assertThatThrownBy(() -> Comment.post(TargetType.ANSWER, 50, 10, 1, null, "a".repeat(601), NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(Comment.post(TargetType.ANSWER, 50, 10, 1, null, "a", NOW).body()).hasSize(1);
        assertThat(Comment.post(TargetType.ANSWER, 50, 10, 1, null, "a".repeat(600), NOW).body()).hasSize(600);
    }

    @Test
    void voteValueBoundaries() {
        assertThatThrownBy(() -> Vote.cast(1, 3, TargetType.ANSWER, 50, 0, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code()).isEqualTo("QA-1016"));
        assertThatThrownBy(() -> Vote.cast(1, 3, TargetType.ANSWER, 50, -2, NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> Vote.cast(1, 3, TargetType.ANSWER, 50, 1, NOW)).doesNotThrowAnyException();
        assertThatCode(() -> Vote.cast(1, 3, TargetType.ANSWER, 50, -1, NOW)).doesNotThrowAnyException();
    }
}
