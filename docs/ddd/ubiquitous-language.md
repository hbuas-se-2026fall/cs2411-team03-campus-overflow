# 统一语言（Ubiquitous Language）词汇表

> 事件风暴工作坊产出 · Sprint 0 初版
> 代码中的类名、方法名、错误信息、Issue 标题、站会发言都要使用同一套词汇。

| 术语 | 代码名 | 定义 / 规则 |
| --- | --- | --- |
| 问题 | Question | 学生发布的求助；标题 10–150 字符、正文 Markdown、1–5 个标签，可关联课程 |
| 回答 | Answer | 对问题的解答；独立聚合，经 questionId 引用问题 |
| 采纳 | accept | 提问者或课程管理者选定最佳答案；每题最多一次且不可更改 |
| 教师认证 | endorse | 课程教师/助教为优质回答打的标记（endorsedBy），每个回答只能认证一次 |
| 投票 | Vote | 对问题/回答 +1 或 −1；每人每内容一票；改投、撤销（值为 0）都可以 |
| 评论 / 回复 | Comment | 挂在问题或回答下，最多一层回复，回复必须与父评论在同一目标上 |
| 标签 | TagName / Tags | 统一小写、去掉前导 #，支持中文与 # + . - _；每题去重后 1–5 个 |
| 软删除 | softDelete / deletedAt | 标记删除而非物理删除；已删除内容不可再回答/编辑/采纳 |
| 课程管理者 | actorManagesCourse | 管理员，或该课程教师/助教（由 IdentityApi.canManageCourse 判定） |
| 声誉 | reputation | 社区信誉分，只能由账本流水改变；采纳 +15、被赞 +10、被踩 −2 |
| 账本流水 | LedgerEntry | 只追加的声誉变动记录；(eventId, userId, reason) 唯一 |
| 每日上限 | DAILY_VOTE_CAP | 每人每个 UTC 自然日因被投票获得的正向声誉最多 200 |
| 悬赏 | Bounty | 课程管理者用悬赏积分为问题加码；OPEN → AWARDED / EXPIRED |
| 悬赏积分 | availablePoints / frozenPoints | 与声誉不同的"货币"；发起悬赏时冻结，结算时转出或退回 |
| 徽章 | BadgeType | 初来乍到、初露锋芒、解惑达人、名师认可、小有名气、德高望重 |
| 排行榜周期 | LeaderboardPeriod | WEEK / MONTH / TERM / ALL；学期：2–7 月春季，8 月–次年 1 月秋季 |
| 对账 | ReconciliationService | 检查每个账户的声誉是否等于其流水之和（FF-8） |
| 搜索投影 | SearchDocument / dis_search_doc | Discovery 自己的问题模型，只关心"如何被找到" |
| 相关问题 | RelatedQuestionPolicy | 按标签集合 Jaccard 相似度推荐；平局时得分高、已采纳者优先 |
| 提及 | MentionParser | 评论中的 @昵称，去重，每条最多提醒 5 人 |
| 通知 | Notification | 站内消息；(sourceEventId, recipientId) 唯一，只有接收者能标记已读 |
| 集成事件 | IntegrationEvent | 跨上下文传播的不可变 record，带全局唯一 eventId |
| 发布语言 | *-api 模块 | 上下文对外暴露的稳定契约（查询接口 + DTO + 事件） |
| 操作者 | Actor | 当前操作主体（userId、displayName、role），由接口层显式传入 |

## 补充约定（团队在 Sprint 0 确认后写入 docs/错误码.md）

- 代码中新增术语必须同步更新本表；站会、Issue 标题、PR 标题一律使用表中术语，避免"改答案 / 打赏 / 点赞"等口语混用。
- 新增错误码场景按 3.7 节约定补充后写回 docs/错误码.md（本表与错误码文档都属于统一语言的组成部分）。
