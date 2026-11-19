package com.campusoverflow.discovery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campusoverflow.discovery.domain.search.SearchDocument;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import com.campusoverflow.shared.event.ProcessedEventStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 搜索索引投影器：内存替身下的行为与幂等验证。 */
class SearchIndexProjectorTest {

    private static final Instant AT = Instant.parse("2026-11-12T02:00:00Z");

    private final RecordingSearchIndex index = new RecordingSearchIndex();
    private final SetProcessedEventStore processed = new SetProcessedEventStore();
    private final SearchIndexProjector projector = new SearchIndexProjector(index, processed);

    @Test
    @DisplayName("问题发布：插入搜索文档，字段一一对应")
    void questionPostedInsertsDocument() {
        QuestionPostedEvent event = new QuestionPostedEvent("evt-1", AT, 1L, 2L, 3L, "标题", "正文", List.of("java"));

        projector.on(event);

        assertEquals(1, index.inserted.size());
        SearchDocument document = index.inserted.get(0);
        assertEquals(1L, document.questionId());
        assertEquals(3L, document.courseId().longValue());
        assertEquals(2L, document.authorId());
        assertEquals("标题", document.title());
        assertEquals("正文", document.bodyText());
        assertEquals(List.of("java"), document.tags());
        assertEquals(AT, document.createdAt());
        assertTrue(processed.keys.contains("evt-1|" + SearchIndexProjector.CONSUMER));
    }

    @Test
    @DisplayName("问题发布：同一事件投递两次，insert 只调用一次")
    void duplicateQuestionPostedIsIdempotent() {
        QuestionPostedEvent event = new QuestionPostedEvent("evt-1", AT, 1L, 2L, 3L, "标题", "正文", List.of("java"));

        projector.on(event);
        projector.on(event);

        assertEquals(1, index.inserted.size());
        assertEquals(1, index.count("insert"));
        assertEquals(1, processed.keys.size());
    }

    @Test
    @DisplayName("问题编辑：覆盖标题、正文与标签")
    void questionEditedUpdatesText() {
        QuestionEditedEvent event = new QuestionEditedEvent("evt-2", AT, 1L, "新标题", "新正文", List.of("mysql"));

        projector.on(event);

        assertEquals(List.of("updateText:1:新标题:新正文:[mysql]:" + AT), index.calls);
    }

    @Test
    @DisplayName("回答提交：回答数 +1，重复投递仍只 +1")
    void answerSubmittedIncrementsAnswerCountOnce() {
        AnswerSubmittedEvent event = new AnswerSubmittedEvent("evt-3", AT, 10L, 1L, 2L, 3L, "标题");

        projector.on(event);
        projector.on(event);

        assertEquals(List.of("adjustAnswerCount:1:1"), index.calls);
    }

    @Test
    @DisplayName("内容删除：删除回答回答数 -1，删除问题标记删除")
    void contentDeletedHandlesAnswerAndQuestion() {
        projector.on(new ContentDeletedEvent("evt-4", AT, "ANSWER", 10L, 1L));
        projector.on(new ContentDeletedEvent("evt-5", AT, "QUESTION", 1L, 1L));
        projector.on(new ContentDeletedEvent("evt-6", AT, "COMMENT", 20L, 1L));

        assertEquals(List.of("adjustAnswerCount:1:-1", "markDeleted:1"), index.calls);
    }

    @Test
    @DisplayName("采纳：标记问题已采纳")
    void answerAcceptedMarksAccepted() {
        AnswerAcceptedEvent event = new AnswerAcceptedEvent("evt-7", AT, 10L, 1L, 2L, 3L, 3L, "标题");

        projector.on(event);

        assertEquals(List.of("markAccepted:1"), index.calls);
    }

    @Test
    @DisplayName("投票：只投影问题票数，增量取新旧值之差")
    void voteCastProjectsQuestionScoreDelta() {
        projector.on(new VoteCastEvent("evt-8", AT, "QUESTION", 1L, 2L, 3L, 1L, 0, 1));
        projector.on(new VoteCastEvent("evt-9", AT, "QUESTION", 1L, 2L, 3L, 1L, 1, -1));
        projector.on(new VoteCastEvent("evt-10", AT, "ANSWER", 10L, 2L, 3L, 1L, 0, 1));

        assertEquals(List.of("adjustScore:1:1", "adjustScore:1:-2"), index.calls);
    }

    @Test
    @DisplayName("悬赏：开启置 true，获奖或关闭置 false")
    void bountyEventsSwitchOpenFlag() {
        projector.on(new BountyOpenedEvent("evt-11", AT, 100L, 1L, 2L, 50, AT.plusSeconds(3600)));
        projector.on(new BountyAwardedEvent("evt-12", AT, 100L, 1L, 2L, 3L, 50));
        projector.on(new BountyClosedEvent("evt-13", AT, 100L, 1L, 2L, 50));

        assertEquals(List.of("setBountyOpen:1:true", "setBountyOpen:1:false", "setBountyOpen:1:false"), index.calls);
    }

    // ---------------------------------------------------------------- 内存替身

    /** 记录调用的搜索索引替身。 */
    static final class RecordingSearchIndex implements SearchIndex {

        final List<String> calls = new ArrayList<>();
        final List<SearchDocument> inserted = new ArrayList<>();

        int count(String prefix) {
            return (int) calls.stream().filter(call -> call.startsWith(prefix)).count();
        }

        @Override
        public void insert(SearchDocument document) {
            calls.add("insert");
            inserted.add(document);
        }

        @Override
        public void updateText(long questionId, String title, String bodyText, List<String> tags, Instant at) {
            calls.add("updateText:" + questionId + ":" + title + ":" + bodyText + ":" + tags + ":" + at);
        }

        @Override
        public void adjustAnswerCount(long questionId, int delta, Instant at) {
            calls.add("adjustAnswerCount:" + questionId + ":" + delta);
        }

        @Override
        public void adjustScore(long questionId, int delta) {
            calls.add("adjustScore:" + questionId + ":" + delta);
        }

        @Override
        public void markAccepted(long questionId, Instant at) {
            calls.add("markAccepted:" + questionId);
        }

        @Override
        public void setBountyOpen(long questionId, boolean open) {
            calls.add("setBountyOpen:" + questionId + ":" + open);
        }

        @Override
        public void markDeleted(long questionId) {
            calls.add("markDeleted:" + questionId);
        }

        @Override
        public List<String> findTags(long questionId) {
            return List.of();
        }

        @Override
        public List<RelatedCandidate> findCandidatesSharingTags(long questionId, List<String> tags, int limit) {
            return List.of();
        }
    }

    /** 以 eventId + "|" + consumer 为键的已处理事件内存实现。 */
    static final class SetProcessedEventStore implements ProcessedEventStore {

        final Set<String> keys = new HashSet<>();

        @Override
        public boolean markProcessed(String eventId, String consumer) {
            return keys.add(eventId + "|" + consumer);
        }
    }
}
