# 聚合识别清单（Aggregate Inventory · Lab3）

> 事件风暴工作坊产出 · 维护者：PO（zhaojeak）
> 内容按**代码实际实现**整理，与代码不一致时以代码为准。核对基线：`main` @ `53ffd18`。
> 配套文件：`context-map.md`（上下文映射）、`event-catalog.md`（集成事件清单）、`ubiquitous-language.md`（统一语言）。

## 1. 识别方法

一次事件风暴之后，按三个问题把便利贴收敛成聚合：

1. **谁在变**：同一事务内必须一起成功或一起失败的状态，划进同一个聚合；
2. **谁守规则**：一条业务规则只由一个聚合根把关，跨聚合的一致性交给领域事件 + 最终一致；
3. **谁的边界最小**：能独立演进、独立加锁、独立建表，就独立成聚合；只是被别人引用的数据用 ID 引用，不共享对象。

## 2. 聚合总览

| # | 上下文 | 聚合名 | 根实体 | 聚合内其他对象 | 领域模块 | 持久化表 | 发布事件 |
|---|---|---|---|---|---|---|---|
| A1 | identity | 用户 User | `User` | 无（角色、状态为内置值对象） | `co-identity-domain` | `id_user`（UK `username`、`display_name`、`email_hash`） | `UserRegisteredEvent` |
| A2 | identity | 课程 Course | `Course` | `CourseMember` | `co-identity-domain` | `id_course`、`id_course_member`（UK `course_id,user_id`） | — |
| A3 | qa | 问题 Question | `Question` | `Title`、`MarkdownBody`、`Tags`（含 `TagName`） | `co-qa-domain` | `qa_question`、`qa_question_tag`、`qa_tag` | `QuestionPostedEvent`、`QuestionEditedEvent`、`AnswerAcceptedEvent`、`ContentDeletedEvent` |
| A4 | qa | 回答 Answer | `Answer` | `MarkdownBody` | `co-qa-domain` | `qa_answer` | `AnswerSubmittedEvent`、`AnswerEndorsedEvent`、`ContentDeletedEvent` |
| A5 | qa | 投票 Vote | `Vote` | 无 | `co-qa-domain` | `qa_vote`（UK `uk_vote(voter_id,target_type,target_id)`） | `VoteCastEvent` |
| A6 | qa | 评论 Comment | `Comment` | 无（`TargetType` 为共享枚举） | `co-qa-domain` | `qa_comment` | `CommentPostedEvent` |
| A7 | reputation | 声誉账户 ReputationAccount | `ReputationAccount` | 无 | `co-reputation-domain` | `rep_account` | `ReputationChangedEvent` |
| A8 | reputation | 声誉账本 LedgerEntry | `LedgerEntry` | 无（只追加记录） | `co-reputation-domain` | `rep_ledger`（UK `event_id,user_id,reason`） | —（由 A7 汇总后发布） |
| A9 | reputation | 悬赏 Bounty | `Bounty` | 无 | `co-reputation-domain` | `rep_bounty` | `BountyOpenedEvent`、`BountyAwardedEvent`、`BountyClosedEvent` |
| A10 | reputation | 徽章授予 BadgeAward | `BadgeAward` | `BadgeType`、`BadgePolicy` | `co-reputation-domain` | `rep_badge`（UK `uk_badge(user_id,badge_code)`） | `BadgeAwardedEvent` |
| A11 | discovery | 通知 Notification | `Notification` | 无 | `co-discovery-domain` | `dis_notification`（UK `uk_notification_event(source_event_id,recipient_id)`） | —（终端消费者） |

### 不是聚合的三类

| 类型 | 对象 | 为什么不是聚合 |
|---|---|---|
| 读模型投影 | `SearchDocument`（discovery） | 由事件折叠而来，只读、可重建、无业务规则，属于 CQRS 的 Query 侧 |
| 值对象 | `Title`、`MarkdownBody`、`Tags`、`TagName`、`SearchQuery`、`CourseRole`、`BountyStatus`、`TargetType` | 没有标识、不可变、只做自身校验，依附于某个聚合根 |
| 策略/服务 | `ReputationRulebook`、`BadgePolicy`、`RelatedQuestionPolicy`、`MentionParser` | 无状态，提供规则计算，不持有需要持久化的状态 |

## 3. 关键不变量（聚合根把关）

错误码即代码中抛出的业务错误码，可直接用于定位校验点。

### A1 用户 `User`（`co-identity-domain/.../User.java`）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 学号/工号必须是 4–20 位字母或数字 | `ID-1001` | `User.validateUsername` |
| 昵称 2–20 位，且不含 `@` 与空格 | `ID-1002` | `User.checkDisplayName` |
| 邮箱格式必须合法 | `ID-1003` | `User.checkEmail` |
| 密码 8–64 位，且必须同时含字母与数字 | `ID-1004` | `PasswordPolicy` |
| 学生账号只能由注册流程创建，不能由管理员建号 | `ID-1005` | `User.adminCreated` |
| 任何人不能修改自己的角色 | `ID-1006` | `User.changeRole` |
| 管理员不能禁用自己的账号 | `ID-1007` | `User.disable` |
| 仅管理员可建号/改角色/禁用/认证 | `ID-3001` | `User.requireAdmin` |
| 只能修改本人资料 | `ID-3002` | `User.updateProfile` |
| 已认证用户不能重复认证 | `ID-2006` | `User.verify` |
| 学号、昵称、邮箱摘要三者全局唯一 | UK `uk_user_username`、`uk_user_display_name`、`uk_user_email_hash` | `id_user` 表 |

### A2 课程 `Course`（`Course.java`、`CourseMember.java`）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 仅教师或管理员可以创建课程 | `ID-3002` | `Course.create` |
| 课程代码 2–20 位字母、数字或连字符，入库统一大写 | `ID-1010` | `Course.create` |
| 课程名称 2–50 位、学期 2–20 位 | `ID-1011`、`ID-1012` | `Guard.requireText` |
| 课程教师的成员角色不可被改派 | `ID-1013` | `CourseMember.changeRole` |

### A3 问题 `Question`（`Question.java` + 值对象）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 标题 10–150 位（去首尾空白后） | `QA-1001` | `Title` |
| 正文 20–30000 位 | `QA-1002` | `MarkdownBody.question` |
| 每个问题 1–5 个标签，标签名格式合法 | `QA-1004`、`QA-1005` | `TagName`、`Tags` |
| 只有提问者或课程管理者（教师/助教/管理员）能编辑、删除 | `QA-3001`、`QA-3002`、`QA-3003` | `Question.edit`、`Question.delete` |
| 已有回答的问题不能由提问者删除 | `QA-1009` | `Question.delete` |
| 一个问题只能采纳一次 | `QA-2002` | `Question.accept` |
| 被采纳的回答必须属于该问题，且未被删除 | `QA-4001`、`QA-1020` | `Question.accept` |
| 仅提问者或课程管理者能采纳 | `QA-3001` | `Question.accept` |
| 软删除后不能再被编辑、采纳、投票 | `QA-1020` | `Question.requireAlive` |

### A4 回答 `Answer`（`Answer.java`）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 正文 10–30000 位 | `QA-1002` | `MarkdownBody.answer` |
| 只有作者或课程管理者能编辑、删除 | `QA-3002`、`QA-3003` | `Answer.edit`、`Answer.delete` |
| 已被采纳的回答不能删除 | `QA-1012` | `Answer.delete` |
| 仅课程教师/助教可以认证回答，且只能认证一次 | `QA-3004`、`QA-2004` | `Answer.endorse` |

### A5 投票 `Vote`（`Vote.java`）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 不能给自己的内容投票 | `QA-1015` | `Vote.cast` |
| 投票值只能是 `+1` 或 `-1`；改投复用同一记录 | `QA-1016` | `Vote.change` |
| 同一用户对同一目标仅一票 | 数据库唯一约束 `uk_vote` | `qa_vote` 表 |

### A6 评论 `Comment`（`Comment.java`）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 正文 1–600 位 | `QA-1003` | `Guard.requireText` |
| 只支持一层回复，回复的父子目标必须一致 | `QA-1007`、`QA-1008` | `Comment.reply` |

### A7 声誉账户 `ReputationAccount`（`ReputationAccount.java`）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 声誉值与可用、冻结积分三条量互相独立，冻结只能来自可用 | `REP-1002` | `freezePoints` |
| 消耗/退回冻结积分不得超过冻结余额，且金额必须为正 | `REP-1006` | `spendFrozen`、`refundFrozen`、`requirePositive` |
| 开户初始积分不得为负（按角色发放，见 ADR-010） | `REP-1006` | `open` |
| 账户声誉值恒等于该用户账本 delta 之和（对账以此为准） | — | `rep_account.reputation` 字段注释 + 对账接口 |

### A8 声誉账本 `LedgerEntry`（`LedgerEntry.java`）

| 不变量 | 错误码/约束 | 实现位置 |
|---|---|---|
| 只追加，不修改不删除（ADR-004） | — | 仓储仅提供 `insert` |
| 同一事件对同一用户同一原因只记一次，重复投递天然幂等 | UK `uk_ledger_event(event_id, user_id, reason)` | `rep_ledger` 表 |

### A9 悬赏 `Bounty`（`Bounty.java`）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 仅课程管理者（教师/助教/管理员）可以发起悬赏 | `REP-3001` | `Bounty.open` |
| 悬赏积分必须在允许区间内且不超过发起人可用积分 | `REP-1003`、`REP-1004` | `Bounty.open` |
| 问题已删除或已采纳时不能发起悬赏 | `REP-1001` | `Bounty.open` |
| 发起人不能赢得自己的悬赏 | `REP-1005` | `Bounty.award` |
| 已结束（结算/关闭/逾期）的悬赏不能再结算 | `REP-2002` | `Bounty.requireOpen` |

### A10 徽章授予 `BadgeAward`（`BadgeAward.java`、`BadgePolicy.java`）

| 不变量 | 错误码/约束 | 实现位置 |
|---|---|---|
| 同一用户同一徽章只授予一次 | UK `uk_badge(user_id, badge_code)` | `rep_badge` 表 |
| 授予条件由 `BadgePolicy` 统一判定，不得在应用层重复写规则 | — | `BadgePolicy` |

### A11 通知 `Notification`（`Notification.java`）

| 不变量 | 错误码 | 实现位置 |
|---|---|---|
| 通知标题不能为空 | `DIS-1003` | `Notification.create` |
| 只能把自己的通知标记为已读 | `DIS-3001` | `Notification.markRead` |
| 同一来源事件对同一接收人只生成一条通知 | UK `uk_notification_event(source_event_id, recipient_id)` | `dis_notification` 表 |

## 4. 聚合与事件风暴的关系

| 事件风暴热点 | 收敛到的聚合 | 说明 |
|---|---|---|
| 注册/登录/认证/禁用 | A1 用户、A2 课程 | 身份与课程成员关系同属 identity，事务边界一致 |
| 发问 → 回答 → 投票 → 采纳 | A3 问题、A4 回答、A5 投票 | 采纳会同时改问题与回答，跨聚合用 `AnswerAcceptedEvent` 保证最终一致 |
| 评论与 @ | A6 评论、A11 通知 | 评论不直接写通知表，通知由 discovery 订阅事件生成 |
| 声誉计分/徽章/排行榜 | A7 账户、A8 账本、A10 徽章 | 账本是计分事实来源，账户是聚合视图，对账以账本为准 |
| 悬赏冻结与结算 | A9 悬赏、A7 账户 | 冻结与结算是同一事务内的两个聚合操作，靠领域服务编排 |
| 搜索与推荐 | `SearchDocument` 投影 | 不是聚合，事件折叠出的读模型 |

## 5. 维护约定

- 新增聚合必须同时更新本清单、`event-catalog.md` 与 `ubiquitous-language.md`，并在 PR 描述中说明事务边界。
- 跨聚合只允许引用 ID 或订阅事件，禁止直接持有对方实体（ArchUnit 规则 `no-cross-context-domain-access` 兜底）。
- 聚合根之外的类若需要持久化，先判断是否应拆出新聚合，不得在聚合根里塞第二套状态。
