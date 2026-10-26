package com.campusoverflow.qa.application.query;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QaReadModel;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.application.port.ViewCounter;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 问答查询用例（读路径）。 */
@Service
@Transactional(readOnly = true)
public class QuestionQueryService {

    private static final int EXCERPT_LENGTH = 160;

    private final QaReadModel readModel;
    private final IdentityApi identity;
    private final ContentRenderer renderer;
    private final ViewCounter viewCounter;
    private final QuestionListCache cache;

    public QuestionQueryService(QaReadModel readModel, IdentityApi identity, ContentRenderer renderer,
                                ViewCounter viewCounter, QuestionListCache cache) {
        this.readModel = readModel;
        this.identity = identity;
        this.renderer = renderer;
        this.viewCounter = viewCounter;
        this.cache = cache;
    }

    public PageResult<QuestionSummaryView> list(Long courseId, PageRequest page) {
        return cache.get(courseId, page).orElseGet(() -> {
            PageResult<QuestionRow> rows = readModel.listQuestions(courseId, page);
            Map<Long, UserSummary> authors = identity.findUsers(
                    rows.items().stream().map(QuestionRow::authorId).distinct().toList());
            List<QuestionSummaryView> views = rows.items().stream()
                    .map(r -> new QuestionSummaryView(r.id(), r.title(),
                            renderer.toPlainText(r.body(), EXCERPT_LENGTH), r.tags(), r.courseId(),
                            AuthorView.of(r.authorId(), authors.get(r.authorId())), r.score(), r.answerCount(),
                            r.viewCount(), r.acceptedAnswerId() != null, r.createdAt()))
                    .toList();
            PageResult<QuestionSummaryView> result = new PageResult<>(views, rows.total(), rows.page(), rows.size());
            cache.put(courseId, page, result);
            return result;
        });
    }

    public QuestionDetailView detail(long questionId, Actor actorOrNull) {
        QuestionRow question = readModel.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
        List<AnswerRow> answerRows = readModel.findAnswers(questionId);
        List<CommentRow> commentRows = readModel.findComments(questionId);
        List<Long> authorIds = new ArrayList<>();
        authorIds.add(question.authorId());
        answerRows.forEach(a -> authorIds.add(a.authorId()));
        commentRows.forEach(c -> authorIds.add(c.authorId()));
        Map<Long, UserSummary> authors = identity.findUsers(authorIds.stream().distinct().toList());
        CourseRefView course = question.courseId() == null ? null
                : identity.findCourse(question.courseId())
                        .map(c -> new CourseRefView(c.id(), c.code(), c.name()))
                        .orElse(null);
        Map<String, Integer> myVotes = actorOrNull == null ? Map.of()
                : readModel.findMyVotes(actorOrNull.userId(), questionId,
                        answerRows.stream().map(AnswerRow::id).toList());
        boolean manages = actorOrNull != null && identity.canManageCourse(actorOrNull.userId(), question.courseId());
        int myVote = actorOrNull == null ? 0 : myVotes.getOrDefault("QUESTION:" + questionId, 0);

        List<CommentView> questionComments = toCommentViews(commentRows.stream()
                .filter(c -> "QUESTION".equals(c.targetType())).toList(), authors);
        List<AnswerView> answers = answerRows.stream().map(a -> {
            boolean accepted = question.acceptedAnswerId() != null && question.acceptedAnswerId() == a.id();
            AuthorView endorsedBy = a.endorsedBy() == null ? null
                    : AuthorView.of(a.endorsedBy(), authors.get(a.endorsedBy()));
            int answerMyVote = actorOrNull == null ? 0 : myVotes.getOrDefault("ANSWER:" + a.id(), 0);
            boolean canEdit = actorOrNull != null && (actorOrNull.is(a.authorId()) || manages);
            List<CommentView> answerComments = toCommentViews(commentRows.stream()
                    .filter(c -> "ANSWER".equals(c.targetType()) && c.targetId() == a.id()).toList(), authors);
            return new AnswerView(a.id(), a.body(), renderer.toSafeHtml(a.body()),
                    AuthorView.of(a.authorId(), authors.get(a.authorId())), a.score(), accepted, endorsedBy,
                    answerMyVote, canEdit, a.createdAt(), a.updatedAt(), answerComments);
        }).toList();

        viewCounter.increment(questionId);
        return new QuestionDetailView(question.id(), question.title(), question.body(),
                renderer.toSafeHtml(question.body()), question.tags(), course,
                AuthorView.of(question.authorId(), authors.get(question.authorId())), question.score(),
                question.answerCount(), question.viewCount(), question.acceptedAnswerId(), myVote,
                question.createdAt(), question.updatedAt(), permissions(actorOrNull, question, manages),
                questionComments, answers);
    }

    public List<TagView> popularTags(int limit) {
        return readModel.popularTags(Math.min(Math.max(limit, 1), 50));
    }

    private List<CommentView> toCommentViews(List<CommentRow> rows, Map<Long, UserSummary> authors) {
        return rows.stream()
                .map(c -> new CommentView(c.id(), AuthorView.of(c.authorId(), authors.get(c.authorId())),
                        c.parentId(), c.body(), c.createdAt()))
                .toList();
    }

    private PermissionsView permissions(Actor actor, QuestionRow question, boolean manages) {
        if (actor == null) {
            return PermissionsView.NONE;
        }
        boolean isAuthor = actor.is(question.authorId());
        return new PermissionsView(isAuthor || manages,
                manages || (isAuthor && question.answerCount() == 0),
                isAuthor || manages, manages);
    }
}
