# 领域模型（Domain Model）

> DDD 设计文档第 4 件交付物（实验指导书 9.4）。与 `context-map.md`（上下文关系）、`ubiquitous-language.md`（术语）、`event-catalog.md`（集成事件）、`aggregate-inventory.md`（聚合与不变量）配套使用。
> 内容按代码实际实现整理，与代码不一致时以代码为准。核对基线：`main` @ `53ffd18`（PR #101 合并后）。

## 1. 模型的组织方式

四个限界上下文各自维护一套领域模型，跨上下文**只通过 `*-api` 发布语言或集成事件**交换数据，任何上下文都不持有另一个上下文的实体对象。

分层约定（ADR-008）：领域对象是纯 Java 类（无 JPA 注解），持久化实体在 `*-infrastructure` 的 `persistence` 包，两者由 Adapter 手工映射。因此下文的字段是**业务语义字段**，与数据库列名不一定同名。

| 元素 | 约定 | 例子 |
|---|---|---|
| 聚合根 | 有标识、守不变量、对外只暴露行为方法 | `Question`、`ReputationAccount` |
| 实体 | 有标识但生命周期依附于聚合根 | `CourseMember` |
| 值对象 | 无标识、不可变、构造即校验 | `Title`、`Tags`、`MarkdownBody` |
| 领域服务 | 无状态，编排多个聚合或跨聚合规则 | `ReputationRulebook`、`BadgePolicy` |
| 领域事件 | 表达"已经发生的事实"，过去式命名 | `AnswerAcceptedEvent` |

## 2. 总览

| 上下文 | 聚合根 | 值对象 | 领域服务 | 发布的集成事件 |
|---|---|---|---|---|
| Identity | `User`、`Course` | `CourseRole`、`UserStatus`、`PasswordPolicy` | — | `UserRegisteredEvent` |
| Q&A | `Question`、`Answer`、`Vote`、`Comment` | `Title`、`MarkdownBody`、`Tags`、`TagName`、`TargetType` | — | `QuestionPosted/Edited`、`AnswerSubmitted/Endorsed`、`AnswerAccepted`、`VoteCast`、`CommentPosted`、`ContentDeleted` |
| Reputation | `ReputationAccount`、`Bounty`、`BadgeAward`、`LedgerEntry` | `ReputationReason`、`BadgeType`、`BountyStatus`、`AcademicTerm` | `ReputationRulebook`、`BadgePolicy` | `ReputationChanged`、`BadgeAwarded`、`BountyOpened/Awarded/Closed` |
| Discovery | `Notification` | `SearchQuery`、`SearchSort`、`NotificationType` | `MentionParser`、`RelatedQuestionPolicy` | —（终端消费者） |

## 3. Identity 上下文

### 3.1 `User`（聚合根）

`identity/domain/User.java`

| 字段 | 类型 | 语义 |
|---|---|---|
| `id` | `Long` | 用户标识，注册后分配 |
| `username` | `String` | 学号/工号，个人信息，不对外展示，全局唯一 |
| `displayName` | `String` | 公开昵称，用于 @ 提及，全局唯一 |
| `email` | `String` | 邮箱，落库为 AES-GCM 密文 + HMAC 摘要（摘要列做唯一性判断） |
| `passwordHash` | `String` | 密码哈希，明文永不落库 |
| `role` | `Role` | `STUDENT` / `TEACHER` / `ASSISTANT` / `ADMIN` |
| `status` | `UserStatus` | `ACTIVE` / `DISABLED` |
| `verified` | `boolean` | 是否已通过教师/教职工认证 |
| `college` / `className` | `String` | 学院、班级，可空 |
| `createdAt` | `Instant` | 注册时间 |

行为：`register`、`adminCreated`、`verify`、`changeRole`、`disable`、`updateProfile`。校验点与错误码见 `aggregate-inventory.md` A1。

### 3.2 `Course`（聚合根）+ `CourseMember`（实体）

`Course`：`id`、`code`（入库大写）、`name`、`term`、`teacherId`、`createdAt`。
`CourseMember`：`courseId`、`userId`、`role`（`CourseRole`）、`joinedAt`。

成员依附于课程，不能脱离课程独立存在；课程教师自身的成员角色不可被改派（`ID-1013`）。

## 4. Q&A 上下文

### 4.1 `Question`（聚合根）

`qa/domain/Question.java`

| 字段 | 类型 | 语义 |
|---|---|---|
| `id` | `Long` | 问题标识 |
| `authorId` | `long` | 提问者，逻辑引用 `id_user`，不建外键 |
| `courseId` | `Long` | 关联课程，可空（可提公共问题） |
| `title` | `Title` | 值对象，10–150 位 |
| `body` | `MarkdownBody` | 值对象，Markdown 原文 20–30000 位 |
| `tags` | `Tags` | 值对象，1–5 个 `TagName` |
| `acceptedAnswerId` | `Long` | 已采纳回答，可空；一旦设置不可再改（`QA-2002`） |
| `score` / `answerCount` / `viewCount` | `int` | 反规范化计数，由原子 UPDATE 维护 |
| `createdAt` / `updatedAt` | `Instant` | — |
| `deletedAt` | `Instant` | 软删除标记（ADR-006），非空即不可再编辑、采纳、投票 |

行为：`post`、`edit`、`accept`、`delete`、`requireAlive`、`ensureOpenForAnswers`。

### 4.2 `Answer`（聚合根）

`id`、`questionId`、`authorId`、`body`（`MarkdownBody`，10–30000）、`score`、`endorsedBy`（教师认证人，可空）、`createdAt`、`updatedAt`、`deletedAt`。
行为：`submit`、`edit`、`endorse`、`delete`。被采纳的回答不可删除（`QA-1012`），认证只能一次（`QA-2004`）。

### 4.3 `Vote`（聚合根）

`id`、`voterId`、`targetType`（`QUESTION` / `ANSWER`）、`targetId`、`value`（`+1` / `-1`）、`updatedAt`。
行为：`cast`、`change`。改投复用同一条记录并返回旧值，撤销即删除记录；`(voter_id,target_type,target_id)` 唯一约束兜底一票制。

### 4.4 `Comment`（聚合根）

`id`、`targetType`、`targetId`、`questionId`（便于按问题聚合展示）、`authorId`、`parentId`（可空）、`body`（1–600）、`createdAt`。
只支持一层回复：父评论自身不能再有 `parentId`，且回复的目标类型与目标必须与父评论一致（`QA-1007`/`QA-1008`）。

### 4.5 值对象

| 值对象 | 约束 | 说明 |
|---|---|---|
| `Title` | 10–150 位，去首尾空白 | `QA-1001` |
| `MarkdownBody` | 问题 20–30000、回答 10–30000 | 只存原文，渲染在前端 |
| `Tags` | 1–5 个，内部去重 | `QA-1005` |
| `TagName` | 小写化、去前导 `#`、正则 `^[\p{L}\p{N}][\p{L}\p{N}#+.\-_]{0,29}$` | 支持中文、`c++`、`c#` |
| `TargetType` | `QUESTION` / `ANSWER` | 投票与评论共用 |

## 5. Reputation 上下文

### 5.1 `ReputationAccount`（聚合根）

`userId`（主键）、`reputation`、`availablePoints`、`frozenPoints`、`version`。
三条量互相独立：声誉是累计得分，可用积分可被悬赏冻结，冻结积分在结算前不可动用。开户按角色发放初始积分（ADR-010）。**账户声誉恒等于该用户账本 delta 之和**，这是每日对账的判据。

### 5.2 `LedgerEntry`（只追加账本）

`id`、`userId`、`eventId`、`reason`（`ReputationReason`）、`delta`、`refType`、`refId`、`createdAt`。
只追加不修改（ADR-004）；`(event_id, user_id, reason)` 唯一，使事件重复投递天然幂等。

### 5.3 `Bounty`（聚合根）

`id`、`questionId`、`sponsorId`、`points`、`status`（`OPEN` / `AWARDED` / `CLOSED` / `EXPIRED`）、`createdAt`、`expiresAt`、`winnerId`、`answerId`、`closedAt`、`version`。
状态机：`open` →（采纳）`award` → `AWARDED`；`open` →（到期/手动）`close` → `CLOSED`/`EXPIRED`。发起人不能中标（`REP-1005`），已结束的悬赏不可再结算（`REP-2002`）。

### 5.4 `BadgeAward`

`userId`、`badgeCode`、`awardedAt`；`(user_id, badge_code)` 唯一。授予条件由 `BadgePolicy` 统一判定，应用层不重复写规则。

### 5.5 领域服务

| 服务 | 职责 | 关键规则 |
|---|---|---|
| `ReputationRulebook` | 把行为换算成声誉增减，并施加每日上限 | 采纳 +15；被赞 +10 / 被踩 −2；改投按差值；每日正向投票所得不超过 200 |
| `BadgePolicy` | 判断某用户是否达成徽章条件 | 按累计采纳数、回答数等阈值 |

## 6. Discovery 上下文

### 6.1 `Notification`（聚合根）

`id`、`recipientId`、`type`（`NotificationType` 六类）、`title`、`link`（可空，跳转目标）、`sourceEventId`、`isRead`、`createdAt`。
`(source_event_id, recipient_id)` 唯一；只能本人标记已读（`DIS-3001`）。

### 6.2 `SearchDocument`（读模型投影，不是聚合）

`questionId`（主键）、`courseId`、`authorId`、`title`、`bodyText`（Markdown 转纯文本、截断 2000 字）、`tagCount`、`score`、`answerCount`、`accepted`、`bountyOpen`、`deleted`、`createdAt`、`lastActivityAt`。
由事件折叠而来，可随时重建，不承载业务规则；检索走 MySQL ngram 全文索引（ADR-005）。

### 6.3 值对象与策略

| 对象 | 说明 |
|---|---|
| `SearchQuery` | 关键词（每个 ≥2 字符、整体 ≤50 字符，`DIS-1001`/`DIS-1002`），可带课程、标签、排序、只看未回答、只看悬赏 |
| `SearchSort` | 相关度 / 最新 / 活跃 / 得分 |
| `MentionParser` | 从正文解析 `@昵称`，产出被提醒人（昵称唯一，见 `id_user` 唯一键） |
| `RelatedQuestionPolicy` | 相关问题推荐打分与截断 |

## 7. 模型层面的三条硬约束

1. **跨上下文只用 ID**：`qa_question.author_id`、`rep_ledger.user_id` 都是逻辑引用，不建跨库外键，也不 import 对方实体（ArchUnit 规则 `no-cross-context-domain-access` 兜底）。
2. **不变量只由聚合根守**：应用层做编排，但"能否采纳""能否结算"这类判断必须在聚合内抛错，错误码见 `aggregate-inventory.md` 第 3 节。
3. **一致性靠事件，不靠分布式事务**：跨聚合的数据（如"采纳后同时改问题、记分数、结算悬赏"）通过 `AnswerAcceptedEvent` 串起，消费端幂等。
