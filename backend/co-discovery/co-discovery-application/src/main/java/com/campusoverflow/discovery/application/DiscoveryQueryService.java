package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.search.RelatedQuestionPolicy;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.shared.page.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：搜索与筛选、相关问题推荐。 */
@Service
@Transactional(readOnly = true)
public class DiscoveryQueryService {

    /** 相关问题先粗筛的候选上限，再交给领域策略精排，避免把整表标签都拉进内存。 */
    private static final int RELATED_CANDIDATE_LIMIT = 50;

    /** 作者信息缺失时的兜底代称（与通知中心一致）。 */
    private static final String UNKNOWN_AUTHOR = "某同学";
    private static final String UNKNOWN_ROLE = "STUDENT";

    private final SearchPort search;
    private final SearchIndex index;
    private final IdentityApi identity;

    public DiscoveryQueryService(SearchPort search, SearchIndex index, IdentityApi identity) {
        this.search = search;
        this.index = index;
        this.identity = identity;
    }

    /**
     * 搜索：分页结果直接沿用 {@link SearchPort} 的 total/page/size；
     * 作者信息一次批量取回（{@link IdentityApi#findUsers}），避免逐条查询造成 N+1。
     */
    public PageResult<SearchResultView> search(SearchQuery query) {
        PageResult<SearchHit> hits = search.search(query);
        Map<Long, UserSummary> authors = identity.findUsers(
                hits.items().stream().map(SearchHit::authorId).distinct().toList());
        return hits.map(hit -> {
            UserSummary author = authors.get(hit.authorId());
            SearchResultView.Author view = new SearchResultView.Author(
                    hit.authorId(),
                    author == null ? UNKNOWN_AUTHOR : author.displayName(),
                    author == null ? UNKNOWN_ROLE : author.role().name(),
                    author != null && author.verified());
            return new SearchResultView(hit.questionId(), hit.title(), hit.snippet(), hit.tags(),
                    hit.courseId(), view, hit.score(), hit.answerCount(), hit.accepted(), hit.bountyOpen(),
                    hit.createdAt());
        });
    }

    /**
     * 相关问题推荐：取本问题的标签集 → 粗筛共享标签的候选（上限 {@value #RELATED_CANDIDATE_LIMIT}）
     * → 由 {@link RelatedQuestionPolicy} 按 Jaccard 相似度降序排序并截断到 limit。
     * 本问题没有标签时无从推荐，直接返回空列表，不产生一次无意义查询。
     */
    public List<RelatedQuestionView> related(long questionId, int limit) {
        List<String> tags = index.findTags(questionId);
        if (tags.isEmpty()) {
            return List.of();
        }
        List<SearchIndex.RelatedCandidate> candidates =
                index.findCandidatesSharingTags(questionId, tags, RELATED_CANDIDATE_LIMIT);
        return RelatedQuestionPolicy.rank(candidates, tags.size(), limit).stream()
                .map(candidate -> new RelatedQuestionView(candidate.questionId(), candidate.title(),
                        candidate.answerCount(), candidate.accepted(),
                        RelatedQuestionPolicy.jaccard(candidate.sharedTags(), tags.size(),
                                candidate.tagCount())))
                .toList();
    }
}
