package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.campusoverflow.discovery.domain.search.SearchDocument;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * T3-05：搜索与相关问题推荐的用例编排（PB-39 / Issue #45）。
 * <p>三个端口都用内存 Stub：不依赖 Spring、不依赖数据库，
 * 只验证「作者批量解析 + 兜底」「候选粗筛上限」「按相似度降序截断」这三条应用层职责。</p>
 */
class DiscoveryQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T02:00:00Z");
    private static final String FALLBACK_NAME = "某同学";

    private final StubSearchPort search = new StubSearchPort();
    private final StubSearchIndex index = new StubSearchIndex();
    private final StubIdentity identity = new StubIdentity();
    private final DiscoveryQueryService service = new DiscoveryQueryService(search, index, identity);

    // ------------------------------------------------------------------ search

    @Test
    void searchKeepsPagingAndResolvesAuthorsWithFallback() {
        identity.users.put(3L, new UserSummary(3, "张三", Role.TEACHER, true, "计算机学院"));
        // 4 号作者在身份上下文里查不到，用来验证「某同学」兜底
        search.result = new PageResult<>(List.of(
                hit(101, 3, "红黑树的旋转怎么理解", List.of("java", "tree")),
                hit(102, 4, "进程与线程的区别", List.of("os"))), 2, 1, 20);

        PageResult<SearchResultView> result = service.search(new SearchQuery(
                null, null, null, null, false, false, new PageRequest(1, 20)));

        // 分页三件套由 PageResult.map 保留，不能手工重建
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.items()).hasSize(2);

        SearchResultView first = result.items().get(0);
        assertThat(first.id()).isEqualTo(101);
        assertThat(first.title()).isEqualTo("红黑树的旋转怎么理解");
        assertThat(first.excerpt()).isEqualTo("摘要 101");
        assertThat(first.tags()).containsExactly("java", "tree");
        assertThat(first.courseId()).isEqualTo(7L);
        assertThat(first.score()).isEqualTo(12);
        assertThat(first.answerCount()).isEqualTo(2);
        assertThat(first.accepted()).isTrue();
        assertThat(first.bountyOpen()).isTrue();
        assertThat(first.createdAt()).isEqualTo(NOW);
        assertThat(first.author().id()).isEqualTo(3L);
        assertThat(first.author().displayName()).isEqualTo("张三");
        assertThat(first.author().role()).isEqualTo("TEACHER");
        assertThat(first.author().verified()).isTrue();

        // 4 号查不到：仍然返回该条结果，作者降级为匿名学生
        SearchResultView second = result.items().get(1);
        assertThat(second.id()).isEqualTo(102);
        assertThat(second.title()).isEqualTo("进程与线程的区别");
        assertThat(second.author().id()).isEqualTo(4L);
        assertThat(second.author().displayName()).isEqualTo(FALLBACK_NAME);
        assertThat(second.author().role()).isEqualTo("STUDENT");
        assertThat(second.author().verified()).isFalse();
    }

    @Test
    void searchLooksUpAuthorsInOneBatchInsteadOfPerHit() {
        identity.users.put(3L, new UserSummary(3, "张三", Role.STUDENT, false, "计算机学院"));
        search.result = new PageResult<>(List.of(
                hit(101, 3, "同一作者的问题一", List.of("java")),
                hit(102, 3, "同一作者的问题二", List.of("java"))), 2, 1, 20);

        service.search(new SearchQuery(null, null, null, null, false, false, new PageRequest(1, 20)));

        // 只发生一次批量查询，且重复作者被去重 —— 这就是避免 N+1 的证据
        assertThat(identity.batches).hasSize(1);
        assertThat(identity.batches.get(0)).containsExactly(3L);
    }

    @Test
    void searchWithNoHitsReturnsEmptyPageWithoutAuthorLookup() {
        search.result = PageResult.empty(2, 10);

        PageResult<SearchResultView> result = service.search(new SearchQuery(
                null, null, null, null, false, false, new PageRequest(2, 10)));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.page()).isEqualTo(2);
        assertThat(identity.batches).singleElement().satisfies(ids -> assertThat(ids).isEmpty());
    }

    // ----------------------------------------------------------------- related

    @Test
    void relatedReturnsEmptyWhenQuestionHasNoTags() {
        index.tags = List.of();

        assertThat(service.related(1, 5)).isEmpty();
        // 没有标签就不该再发一次候选查询
        assertThat(index.candidateQueries).isEmpty();
    }

    @Test
    void relatedOrdersByJaccardDescendingAndTruncatesToLimit() {
        index.tags = List.of("java", "tree");
        index.candidates = List.of(
                candidate(201, "完全共享两个标签", 2, 2, 3, true),
                candidate(202, "只共享一个且标签很杂", 1, 5, 9, true),
                candidate(203, "只共享一个", 1, 2, 1, false));

        List<RelatedQuestionView> result = service.related(1, 2);

        // Jaccard：201 = 2/2 = 1.0；203 = 1/3 ≈ 0.333；202 = 1/6 ≈ 0.167
        assertThat(result).extracting(RelatedQuestionView::id).containsExactly(201L, 203L);
        assertThat(result.get(0).similarity()).isEqualTo(1.0);
        assertThat(result.get(1).similarity()).isCloseTo(1.0 / 3.0, within(1e-9));
        assertThat(result).allSatisfy(v -> assertThat(v.similarity()).isBetween(0.0, 1.0));
    }

    @Test
    void relatedPassesFixedCandidateCapToIndex() {
        index.tags = List.of("java", "tree");

        service.related(1, 5);

        assertThat(index.candidateQueries).singleElement().satisfies(q -> {
            assertThat(q.tags()).containsExactly("java", "tree");
            assertThat(q.limit()).isEqualTo(50);
        });
    }

    @Test
    void relatedKeepsCandidateFieldsAndReturnsEmptyForNonPositiveLimit() {
        index.tags = List.of("java");
        index.candidates = List.of(candidate(201, "候选", 1, 1, 0, false));

        assertThat(service.related(1, 0)).isEmpty();

        List<RelatedQuestionView> result = service.related(1, 3);
        assertThat(result).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(201L);
            assertThat(v.title()).isEqualTo("候选");
            assertThat(v.answerCount()).isEqualTo(5);
            assertThat(v.accepted()).isFalse();
        });
    }

    // ------------------------------------------------------------------ 造数据

    private SearchHit hit(long questionId, long authorId, String title, List<String> tags) {
        return new SearchHit(questionId, title, "摘要 " + questionId, tags, 7L, authorId, 12,
                2, true, true, NOW);
    }

    private SearchIndex.RelatedCandidate candidate(long questionId, String title, int sharedTags,
                                                   int tagCount, int score, boolean accepted) {
        return new SearchIndex.RelatedCandidate(questionId, title, sharedTags, tagCount, score, 5, accepted);
    }

    // ------------------------------------------------------------------ Stubs

    /** 搜索结果固定返回，不做任何过滤——过滤是基础设施适配器的职责。 */
    private static final class StubSearchPort implements SearchPort {

        private PageResult<SearchHit> result = PageResult.empty(1, 20);

        @Override
        public PageResult<SearchHit> search(SearchQuery query) {
            return result;
        }
    }

    /** 只实现查询侧的两个方法；写入方法留空（本用例不涉及）。 */
    private static final class StubSearchIndex implements SearchIndex {

        private List<String> tags = List.of();
        private List<RelatedCandidate> candidates = List.of();
        private final List<CandidateQuery> candidateQueries = new ArrayList<>();

        @Override
        public List<String> findTags(long questionId) {
            return tags;
        }

        @Override
        public List<RelatedCandidate> findCandidatesSharingTags(long questionId, List<String> tags, int limit) {
            candidateQueries.add(new CandidateQuery(List.copyOf(tags), limit));
            return candidates;
        }

        @Override
        public void insert(SearchDocument document) {
            throw new UnsupportedOperationException("本用例不写入索引");
        }

        @Override
        public void updateText(long questionId, String title, String bodyText, List<String> tags, Instant at) {
            throw new UnsupportedOperationException("本用例不写入索引");
        }

        @Override
        public void adjustAnswerCount(long questionId, int delta, Instant at) {
            throw new UnsupportedOperationException("本用例不写入索引");
        }

        @Override
        public void adjustScore(long questionId, int delta) {
            throw new UnsupportedOperationException("本用例不写入索引");
        }

        @Override
        public void markAccepted(long questionId, Instant at) {
            throw new UnsupportedOperationException("本用例不写入索引");
        }

        @Override
        public void setBountyOpen(long questionId, boolean open) {
            throw new UnsupportedOperationException("本用例不写入索引");
        }

        @Override
        public void markDeleted(long questionId) {
            throw new UnsupportedOperationException("本用例不写入索引");
        }

        record CandidateQuery(List<String> tags, int limit) {
        }
    }

    /** 记录每次批量查询的入参，用来断言「一次批量取作者」而不是循环单查。 */
    private static final class StubIdentity implements IdentityApi {

        private final Map<Long, UserSummary> users = new java.util.LinkedHashMap<>();
        private final List<List<Long>> batches = new ArrayList<>();

        @Override
        public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
            batches.add(List.copyOf(userIds));
            Map<Long, UserSummary> found = new java.util.LinkedHashMap<>();
            for (Long id : userIds) {
                Optional.ofNullable(users.get(id)).ifPresent(u -> found.put(id, u));
            }
            return found;
        }

        @Override
        public Optional<UserSummary> findUser(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Optional<UserSummary> findByDisplayName(String displayName) {
            return users.values().stream().filter(u -> u.displayName().equals(displayName)).findFirst();
        }

        @Override
        public Optional<CourseSummary> findCourse(long courseId) {
            return Optional.empty();
        }

        @Override
        public boolean canManageCourse(long userId, Long courseId) {
            return false;
        }
    }
}
