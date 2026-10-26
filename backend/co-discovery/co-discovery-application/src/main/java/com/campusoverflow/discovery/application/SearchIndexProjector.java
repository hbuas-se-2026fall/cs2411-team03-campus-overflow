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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事件投影器：把上游事件折叠为搜索投影（dis_search_doc）。
 * 计数器类更新不是天然幂等的，因此每个事件先经 ProcessedEventStore 去重（与投影更新同一事务）。
 * <p>行走骨架（PB-18，指导书 6.3.4）：Sprint 1 暂为空实现（只打 DEBUG 日志），避免发件箱被阻塞；
 * 实现/移除见 tech-debt Issue #67（Sprint 3 按 T3-09 完成）。</p>
 */
@Component
public class SearchIndexProjector {

    static final String CONSUMER = "discovery.search-index";

    private static final Logger log = LoggerFactory.getLogger(SearchIndexProjector.class);

    private final SearchIndex index;
    private final ProcessedEventStore processed;

    public SearchIndexProjector(SearchIndex index, ProcessedEventStore processed) {
        this.index = index;
        this.processed = processed;
    }

    private boolean firstTime(IntegrationEvent e) {
        // 行走骨架：监听器为空实现，本方法暂不可达；实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
        return false;
    }

    @EventListener
    @Transactional
    public void on(QuestionPostedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(QuestionEditedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(ContentDeletedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(VoteCastEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(BountyOpenedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(BountyAwardedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }

    @EventListener
    @Transactional
    public void on(BountyClosedEvent e) {
        // 行走骨架：空实现，事件被标记为已投递。实现见 Issue #67。
        log.debug("[walking-skeleton] Discovery 搜索索引尚未实现，忽略事件 {}", e.eventId()); // TODO(S3) 见 Issue #67
    }
}
