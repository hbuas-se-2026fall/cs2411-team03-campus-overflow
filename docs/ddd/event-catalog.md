# 集成事件清单（Event Catalog）

本文件对应实验指导书 6.2.4 交付项「docs/ddd/（上下文映射、统一语言、事件清单）」。内容按代码实际实现整理，与代码不一致时以代码为准。
核对基线：`v0.3.0`（提交 `2c6823d`）。

## 1. 事件约定

| 约定 | 落地方式 | 代码位置 |
|---|---|---|
| 事件形态 | 不可变 `record`，只含基本类型与字符串，不暴露领域对象 | `co-shared-kernel/src/main/java/com/campusoverflow/shared/event/IntegrationEvent.java` |
| 标识 | `eventId`（UUID v4）+ `occurredAt` | `EventIds.next()` |
| 投递 | 事务性发件箱：与领域写入同事务落库，异步投递 | `OutboxIntegrationEventPublisher`、`outbox` 表（`uk_outbox_event_id`） |
| 投递语义 | 至少一次，因此消费端必须幂等（ADR-002） | — |
| 消费去重 | `processed_event(event_id, consumer)` 联合主键 | `JdbcProcessedEventStore` |
| 命名 | 过去式动词短语 + `Event` | — |

## 2. 事件总览

| # | 事件 | 发布上下文 | 订阅者 | 触发时机 | 消费幂等依据 |
|---|---|---|---|---|---|
| 1 | `UserRegisteredEvent` | identity | reputation（开户） | 注册成功 | `rep_account` 主键 `user_id` |
| 2 | `QuestionPostedEvent` | qa | discovery（搜索投影） | 问题发布成功 | `dis_search_doc` 主键 `question_id` |
| 3 | `QuestionEditedEvent` | qa | discovery（搜索投影） | 问题编辑成功 | `dis_search_doc` 主键 `question_id` |
| 4 | `AnswerSubmittedEvent` | qa | reputation（计分）、discovery（搜索投影、通知） | 回答提交成功 | `rep_ledger` UK `(event_id,user_id,reason)`；`dis_notification` UK `(source_event_id,recipient_id)` |
| 5 | `AnswerAcceptedEvent` | qa | reputation（计分 + 徽章 + 悬赏结算）、discovery（搜索投影、通知） | 采纳成功 | 同上 |
| 6 | `AnswerEndorsedEvent` | qa | reputation（教师认证计分）、discovery（通知） | 教师认证成功 | `rep_ledger` UK |
| 7 | `VoteCastEvent` | qa | reputation（投票计分）、discovery（搜索投影） | 投票/改投/撤销成功 | `rep_ledger` UK |
| 8 | `CommentPostedEvent` | qa | discovery（通知） | 评论或回复成功 | `dis_notification` UK |
| 9 | `ContentDeletedEvent` | qa | discovery（搜索投影） | 软删除成功 | `dis_search_doc` 主键 |
| 10 | `ReputationChangedEvent` | reputation | reputation（排行榜） | 声誉值变更后 | `processed_event(event_id,'reputation.leaderboard')` |
| 11 | `BadgeAwardedEvent` | reputation | discovery（通知） | 徽章授予 | `rep_badge` UK `(user_id,badge_code)`；`dis_notification` UK |
| 12 | `BountyOpenedEvent` | reputation | discovery（搜索投影） | 悬赏发起、积分冻结成功 | `dis_search_doc` 主键 |
| 13 | `BountyAwardedEvent` | reputation | discovery（搜索投影、通知） | 采纳后悬赏结算成功 | `dis_notification` UK |
| 14 | `BountyClosedEvent` | reputation | discovery（搜索投影） | 悬赏关闭或逾期退回 | `dis_search_doc` 主键 |

## 3. 事件定义

```java
public record UserRegisteredEvent(String eventId, Instant occurredAt, long userId,
        String displayName, Role role)

public record QuestionPostedEvent(String eventId, Instant occurredAt, long questionId, long authorId,
        Long courseId, String title, String plainText, List<String> tags)

public record QuestionEditedEvent(String eventId, Instant occurredAt, long questionId, String title,
        String plainText, List<String> tags)

public record AnswerSubmittedEvent(String eventId, Instant occurredAt, long answerId, long questionId,
        long answerAuthorId, long questionAuthorId, String questionTitle)

public record AnswerAcceptedEvent(String eventId, Instant occurredAt, long answerId, long questionId,
        long answerAuthorId, long questionAuthorId, long acceptedById, String questionTitle)

public record AnswerEndorsedEvent(String eventId, Instant occurredAt, long answerId, long questionId,
        long answerAuthorId, long endorserId, String questionTitle)

public record VoteCastEvent(String eventId, Instant occurredAt, String targetType, long targetId,
        long targetAuthorId, long voterId, long questionId, int oldValue, int newValue)

public record CommentPostedEvent(String eventId, Instant occurredAt, long commentId, long questionId,
        long authorId, String body)

public record ContentDeletedEvent(String eventId, Instant occurredAt, String targetType, long targetId,
        long questionId)

public record ReputationChangedEvent(String eventId, Instant occurredAt, long userId, int delta,
        int reputation, String reason)

public record BadgeAwardedEvent(String eventId, Instant occurredAt, long userId, String badgeCode,
        String badgeName)

public record BountyOpenedEvent(String eventId, Instant occurredAt, long bountyId, long questionId,
        long sponsorId, int points, Instant expiresAt)

public record BountyAwardedEvent(String eventId, Instant occurredAt, long bountyId, long questionId,
        long sponsorId, long winnerId, int points)

public record BountyClosedEvent(String eventId, Instant occurredAt, long bountyId, long questionId,
        long sponsorId, int refundedPoints)
```

## 4. 消费者登记

| 消费者 | 实现类 | 订阅事件 | 幂等手段 |
|---|---|---|---|
| `reputation.ledger` | `ReputationEventHandlers` | UserRegistered、AnswerSubmitted、AnswerAccepted、AnswerEndorsed、VoteCast | `rep_ledger` 唯一键 + 徽章唯一键 |
| `reputation.leaderboard` | `LeaderboardService` | ReputationChanged | `processed_event` 标记 |
| `discovery.search-index` | `SearchIndexProjector` | QuestionPosted、QuestionEdited、AnswerSubmitted、AnswerAccepted、VoteCast、ContentDeleted、BountyOpened、BountyAwarded、BountyClosed | 投影表主键 |
| `discovery.notification` | `NotificationProjector` | AnswerSubmitted、AnswerAccepted、AnswerEndorsed、CommentPosted、BountyAwarded、BadgeAwarded | `dis_notification` 唯一键 |

## 5. 说明

- 所有事件均已有至少一个订阅者，不存在只发布未消费的事件。
- `VoteCastEvent` 的 `questionId` 在回答投票场景中指向该回答所属问题，供搜索投影定位文档。
- 新增事件时必须同时在此表登记发布上下文、订阅者与幂等手段；缺少任一项视为事件设计未完成。
