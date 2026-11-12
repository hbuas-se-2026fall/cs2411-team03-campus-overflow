# Sprint 1 记录（第 2–3 周）

> 本文件对应实验指导书 9.1 要求的 `docs/scrum/sprint-N.md`。内容按仓库可核验证据整理；时间为标签与提交时间，本记录为 Sprint 结束后的整理版。

## 1. Sprint 目标

把问答核心跑通：问题发布与浏览、回答、采纳、投票、编辑与软删除、教师认证、评论与一层回复，并产出 Sprint 2 之前的需求基线（Backlog、用户故事地图、Gherkin 验收标准）。

## 2. Sprint Backlog

| Issue | 内容 | 状态 |
|---|---|---|
| #12 | 发布带标签、可关联课程的问题 | closed |
| #13 | 分页浏览、按课程筛选问题列表 | closed |
| #14 | 查看问题详情 | closed |
| #15 | 回答未删除的问题 | closed |
| #16 | 提问者或课程管理者采纳一个最佳答案 | closed |
| #17 | 对他人问题与回答投票、改投、撤销 | closed |
| #18 | 作者或课程管理者编辑问题与回答 | closed |
| #19 | 作者或课程管理者软删除问题与回答 | closed |
| #20 | 课程教师或助教为优质回答做教师认证 | closed |
| #21 | 评论问题或回答并支持一层回复 | closed |
| #22 | 查看热门标签 | closed |
| #23 | 其他上下文通过 `QaQueryApi` 查询问题引用 | closed |
| #24 | 行走骨架 | closed |
| #25 | 前端编辑删除、发表评论与回复、友好错误提示 | closed |
| #62 | 需求建模产出：Product Backlog、用户故事地图、Gherkin 验收标准 | closed |

Sprint 1 里程碑：15 个 Issue 全部关闭，里程碑已归档（closed）。

## 3. 完成情况与证据

代码与测试（本地实测，2026-11-12 复核）：

| 项目 | 命令 | 结果 |
|---|---|---|
| QA 领域层 | `mvn -f backend/pom.xml test -pl co-qa/co-qa-domain -am` | 25 个测试全绿（`AnswerTest` 3、`BoundaryTest` 7、`QuestionTest` 12、`VoteAndCommentTest` 3） |
| QA 应用层 | 同上（`co-qa-application`） | 4 个测试全绿（`AnswerAndVoteServiceTest`） |
| 架构约束 | `mvn -f backend/pom.xml test -pl co-bootstrap -Dtest=ArchitectureTest` | 1 个测试通过 |
| 前端 | `npm run typecheck && npm run build` | typecheck 通过；build 成功，43 个模块，`index.js` 186.51 kB |

需求交付物（`docs/backlog/`）：`product-backlog.md`（48 个 PBI）、`backlog.csv`（48 行）、`acceptance-criteria.md`（按 S0–S4 覆盖 Must PBI）、`story-map.md`（10 个用户活动 × 5 个发布批次）。

版本：`v0.2.0`（2026-10-26）已打标签并创建 Release「Sprint 1 验收（问答核心）」。

## 4. 对照 6.3.5 的 8 条 Gherkin

| # | 场景 | 实现与测试覆盖 | HTTP 端到端 |
|---|---|---|---|
| 1 | 发布标题 ≥10 字、2 个标签的问题 → 201，列表首条出现且标签小写去重 | `QuestionTest.Posting` | 未实机 |
| 2 | 小红提交回答 → 201，回答数 +1，详情页 Markdown 渲染并净化 | `AnswerTest` | 未实机 |
| 3 | 普通学生尝试采纳 → 403 `QA-3001` | `QuestionTest.Accepting`、`BoundaryTest` | 未实机 |
| 4 | 提问者采纳，再次采纳另一回答 → 首次 204，第二次 409 `QA-2002` | `QuestionTest.Accepting` | 未实机 |
| 5 | 对回答连续点赞两次再改踩 → 得分 1、1、−1，第二次点赞无变化 | `VoteAndCommentTest` | 未实机 |
| 6 | 作者给自己的问题点赞 → 422 `QA-1015` | `VoteAndCommentTest`、`BoundaryTest` | 未实机 |
| 7 | 回复已有评论，再回复这条回复 → 第一次成功，第二次 422 | `VoteAndCommentTest` | 未实机 |
| 8 | 教师认证回答、删除已有回答的问题 → 认证标记显示，删除成功且列表与详情不再出现 | `QuestionTest.EditingAndDeleting`、`AnswerTest` | 未实机 |

"实现与测试覆盖"一栏按测试类命名与用例分布对应；8 条场景的 HTTP 层演示均未在本机复现，原因是会话依赖 Redis 而本机 Redis 未运行（Issue #1 仍 open）。

## 5. 回顾

**做得好**

- 领域层把规则（采纳权限、重复采纳、自赞、嵌套评论深度、投票差值）都收敛在聚合根，应用层只做编排，因此 25 + 4 个测试可以在无数据库条件下跑完。
- 需求交付物齐备：Backlog 48 个 PBI、CSV 导入文件、按批次切的 Gherkin 验收标准、用户故事地图，后续 Sprint 直接引用。
- 前端在这一 Sprint 就接上了编辑/删除/评论与错误码提示，没有把前端留到最后。

**待改进**

- 8 条 Gherkin 只停留在单元测试层，没有一次完整的手工或自动化端到端演示，验收时无法当场复现。
- PR 仍然 0 评审：本 Sprint 的 PR（#69、#70、#71、#72）都是作者本人直接合并。
- Issue 认领没有落到 GitHub：15 个 Issue 只有状态变化，没有 assignee，过程协作证据不足。
- Sherpherd-W（SM）在本 Sprint 无代码提交与 PR；站会机制（#5）仍 open。

**行动项（带入 Sprint 2）**

1. 每个 PBI 关闭前补一条"如何验证"的评论，便于验收时复述。
2. PR 指定非作者评审人，评审意见至少一条实质内容。
3. 尝试用 `demo` profile + 本地 MySQL 完成一次真实登录演示，把 Redis 依赖登记为环境风险。
