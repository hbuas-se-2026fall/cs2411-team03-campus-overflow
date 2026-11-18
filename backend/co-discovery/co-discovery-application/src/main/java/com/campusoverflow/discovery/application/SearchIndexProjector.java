package com.campusoverflow.discovery.application;

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
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.ProcessedEventStore;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事件投影器：把上游事件折叠为搜索投影（dis_search_doc）。
 * 计数器类更新不是天然幂等的，因此每个事件先经 ProcessedEventStore 去重（与投影更新同一事务）。
 * <p>投影规则：发布插入文档；编辑覆盖文本与标签；新回答回答数 +1；删除问题标记删除、
 * 删除回答回答数 -1；采纳标记已采纳；问题投票按新旧值之差调整得分；悬赏开启/结算切换悬赏标记。</p>
 */
@Component
public class SearchIndexProjector {

    static final String CONSUMER = "discovery.search-index";

    /** 内容删除事件的问题目标类型。 */
    private static final String TARGET_QUESTION = "QUESTION";

    /** 内容删除与投票事件中的回答目标类型。 */
    private static final String TARGET_ANSWER = "ANSWER";

    private final SearchIndex index;
    private final ProcessedEventStore processed;

    public SearchIndexProjector(SearchIndex index, ProcessedEventStore processed) {
        this.index = index;
        this.processed = processed;
    }

    /** 幂等守卫：同一事件对同一消费者只处理一次。 */
    private boolean firstTime(IntegrationEvent e) {
        return processed.markProcessed(e.eventId(), CONSUMER);
    }

    /** 问题发布：插入搜索文档。 */
    @EventListener
    @Transactional
    public void on(QuestionPostedEvent e) {
        if (!firstTime(e)) {
            return;
        }
        index.insert(new SearchDocument(e.questionId(), e.courseId(), e.authorId(), e.title(), e.plainText(),
                e.tags(), e.occurredAt()));
    }

    /** 问题编辑：覆盖标题、正文与标签。 */
    @EventListener
    @Transactional
    public void on(QuestionEditedEvent e) {
        if (!firstTime(e)) {
            return;
        }
        index.updateText(e.questionId(), e.title(), e.plainText(), e.tags(), e.occurredAt());
    }

    /** 新回答：回答数 +1。 */
    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        if (!firstTime(e)) {
            return;
        }
        index.adjustAnswerCount(e.questionId(), 1, e.occurredAt());
    }

    /** 内容删除：问题标记删除，回答回答数 -1，其它目标类型忽略。 */
    @EventListener
    @Transactional
    public void on(ContentDeletedEvent e) {
        if (!firstTime(e)) {
            return;
        }
        if (TARGET_QUESTION.equals(e.targetType())) {
            index.markDeleted(e.questionId());
        } else if (TARGET_ANSWER.equals(e.targetType())) {
            index.adjustAnswerCount(e.questionId(), -1, e.occurredAt());
        }
    }

    /** 采纳：标记问题已有采纳答案。 */
    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        if (!firstTime(e)) {
            return;
        }
        index.markAccepted(e.questionId(), e.occurredAt());
    }

    /** 投票：只投影问题票数，增量取新旧值之差（撤销与改投都能算对）；回答票不投影。 */
    @EventListener
    @Transactional
    public void on(VoteCastEvent e) {
        if (!firstTime(e)) {
            return;
        }
        if (!TARGET_QUESTION.equals(e.targetType())) {
            return;
        }
        index.adjustScore(e.questionId(), e.newValue() - e.oldValue());
    }

    /** 悬赏开启：标记悬赏进行中。 */
    @EventListener
    @Transactional
    public void on(BountyOpenedEvent e) {
        if (!firstTime(e)) {
            return;
        }
        index.setBountyOpen(e.questionId(), true);
    }

    /** 悬赏结算：标记悬赏结束。 */
    @EventListener
    @Transactional
    public void on(BountyAwardedEvent e) {
        if (!firstTime(e)) {
            return;
        }
        index.setBountyOpen(e.questionId(), false);
    }

    /** 悬赏关闭：标记悬赏结束。 */
    @EventListener
    @Transactional
    public void on(BountyClosedEvent e) {
        if (!firstTime(e)) {
            return;
        }
        index.setBountyOpen(e.questionId(), false);
    }
}
