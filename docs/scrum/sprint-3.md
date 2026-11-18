# Sprint 3 记录（第 6–7 周，进行中）

> 对应实验指导书 9.1 要求的 `docs/scrum/sprint-N.md`。本文件是 **Sprint 进行中的记录版本**，随任务完成持续更新，Sprint 结束并打 v0.9.0 后转为终版。
>
> 最后更新：2026-11-18

## 1. Sprint 目标

依据 6.5：用户可以用中文关键词搜到问题、看到相关问题推荐、收到各类通知与 @提醒；移除全部行走骨架，`mvn verify`（含 Testcontainers 端到端测试）在 CI 上全绿，发布候选版本 v0.9.0。

## 2. Sprint Backlog

9 个 Issue 已全部指派（本 Sprint 起执行"开工即填 assignee"）。

| Issue | 覆盖任务 | 内容 | 认领人 | 状态 |
|---|---|---|---|---|
| #37 | T3-05 | 搜索与筛选排序（`DiscoveryQueryService.search`） | Sherpherd-W | open |
| #38 | T3-04 | 搜索投影幂等更新（`SearchIndexProjector` 9 个 on + 去重守卫） | Can0401 | open |
| #39 | T3-05 | 相关问题推荐（`DiscoveryQueryService.related`） | Can0401（复核） | open |
| #40 | T3-03b、T3-06、T3-07 | 通知六类事件与投影（PB-34/36） | zhaojeak | 部分完成：六类投影已合并；列表/未读数/标记已读端点待 T3-07 |
| #41 | T3-03a、T3-11 | @ 提醒解析与评论高亮（PB-35/36） | Arthur20060209 | 解析器已合并，前端未做 |
| #42 | T3-07、T3-11 | 通知列表、未读数与已读 | Arthur20060209 | open |
| #43 | T3-09、T3-10 | 端到端全绿、移除行走骨架、覆盖率 0.70 | Sherpherd-W | open |
| #44 | T3-12 前置 | demo profile 演示数据补齐 | zhaojeak | open |
| #45 | T3-08、T3-09、T3-10 | PB-39 质量门禁（端到端全绿 + 覆盖率 0.70 + 投影幂等） | zhaojeak | 部分完成：投影幂等用例已合并（10 用例）；另两项待 T3-09/T3-10 |

说明：#37 与 #39 共用 `DiscoveryQueryService` 一个文件，`related` 方法由 Sherpherd-W 在同一 PR 内实现，#39 由 Can0401 复核后关闭。

说明：#40 与 #45 的 Gherkin（`docs/backlog/acceptance-criteria.md`）覆盖范围大于任务名——PB-34/36 含"通知列表与未读数""标记已读"两个场景，PB-39 含"端到端链路全绿""领域覆盖率 0.70"两个场景，因此两者均不能仅凭后端投影实现关闭。

工时口径：4 人组下 Sprint 3 合计 53 人时（T3-12 按 2×4 计）。截至本记录已完成 17 人时（T3-01、T3-02、T3-03、T3-06、T3-08），剩余 36 人时。

## 3. 完成情况与证据

| 项目 | 证据 |
|---|---|
| 已合并代码 | PR #82「实现 Sprint 3 通知与搜索推荐」，7 个文件 +429/−38，提交 `942554b`，合并提交 `a5d9d0c` |
| 领域层实现 | `SearchQuery.terms()`（`\s+` 切分、去重保序、最多 5 项）、`RelatedQuestionPolicy.jaccard/rank`（J 降序 → score 降序 → 已采纳优先）、`MentionParser.extract`（LinkedHashSet 去重、满 5 停）、`Notification.create/markRead`（标题截断 200 码点、越权 403 `DIS-3001`、重复标记幂等） |
| 应用层实现 | `NotificationProjector` 六个事件处理器 + `notify` + `name` + `questionTitle`：AnswerSubmitted→提问者（自答跳过）、Accepted/Endorsed→回答者（自己操作跳过）、CommentPosted→解析 @ 后 `findByDisplayName`、BountyAwarded→获奖者、BadgeAwarded→获得者；`exists(sourceEventId, recipientId)` 前置保证幂等，昵称查不到回退"某同学" |
| 测试 | `DiscoveryDomainTest` 5/5、`DiscoveryApplicationTest` 10/10（含 `DiscoveryTestFixtures` 内存仓库与 Stub Identity/Qa）、`ArchitectureTest` 1/1，均 BUILD SUCCESS |
| CI 恢复 | 仓库由私有转为公开后，GitHub 托管 runner 恢复分配。run #8（PR #82）：前端 9 步、后端 12 步全绿；run #9（main 推送）：后端、前端、容器镜像三 job 全绿（镜像 6 步） |
| 评审机制 | main / develop 分支保护 `required_approving_review_count=1`、`enforce_admins=true`；PR #82 由 Sherpherd-W 非作者 Approve 后合并，为仓库首个走完整评审的 PR |
| 新增错误码 | `DIS-1003`（通知标题为空，422），已登记至 `docs/错误码.md` |

**尚未完成**：`SearchIndexProjector`（T3-04）、`DiscoveryQueryService`（T3-05）、`NotificationService`（T3-07）、CI 门禁收紧与 ADR-009 状态（T3-09）、覆盖率门槛 0.70（T3-10）、前端四项（T3-11）、demo 走查（T3-12）。

## 4. 对照 6.5.3 验收标准

| # | 验收项 | 现状 | 结论 |
|---|---|---|---|
| 1 | 中英文关键词命中、标签/未回答/悬赏筛选与四种排序、单字关键词 422 `DIS-1001` | 领域侧 `terms` 与 `DIS-1001/1002` 校验已实现并有测试；`DiscoveryQueryService.search` 与前端筛选未完成 | 部分通过 |
| 2 | 详情页相关问题与相似度；删除的问题不再出现在搜索与推荐 | `jaccard/rank` 已实现并有测试；`SearchIndexProjector.markDeleted`、`related` 查询与前端侧栏未完成 | 部分通过 |
| 3 | 六类通知产生、未读数正确、只能标记自己的通知 | 投影六类与 `Notification` 越权规则已实现，10 个应用层用例覆盖；`NotificationService` 与通知中心前端未完成 | 部分通过 |
| 4 | CI 五个后端阶段 + 前端阶段全绿、镜像构建成功、领域覆盖率 ≥ 70%、main 打 `v0.9.0` | run #8 / #9 三 job 全绿、镜像构建成功；但 `SPRINT_MODULES` 未移除（`co-discovery` 不在 CI 测试范围）、集成测试仍 `continue-on-error`、覆盖率未覆盖 discovery、未打标签 | 未通过 |

## 5. 回顾（进行中）

**做得好**

- Discovery 的领域规则全部收敛在值对象与策略类（`SearchQuery`、`RelatedQuestionPolicy`、`MentionParser`、`Notification`），应用层只做事件到用例的编排，因此 10 个应用层用例可以在纯内存夹具上跑完，不依赖数据库与消息中间件。
- 事件消费幂等前置到仓储层（`exists(sourceEventId, recipientId)`），而不是只靠 `ProcessedEventStore`，避免同一事件对同一收件人重复通知。
- 首次落实"非作者 Approve 后合并"与"开工即指派"，Sprint 2 回顾打出的两个行动项在本 Sprint 得到执行。

**待改进**

- **CI 绿灯存在范围假象**：`ci.yml` 顶部仍保留 Sprint 2 的 `SPRINT_MODULES`（6 个模块，不含 `co-discovery`），PR #82 的绿色检查并未执行 Sprint 3 核心代码的任何测试；集成测试阶段仍挂着 `continue-on-error: true`。此项必须在 T3-09 中修正，否则 6.5.3 第 4 条无法成立。
- CI 长期红的原因是组织私有仓库 Actions 额度耗尽（runner 未被分配、0 步、2 秒失败），排查跨三个 Sprint 才定位；后续应在 Sprint 开始时即确认额度状态，而不是反复归因于代码。
- 上游任务拆分存在重叠：`DiscoveryDomainTest` 是跨 T3-01/02/03 的共享测试类，任一项不实现都会导致整类测试失败，实际只能由一人一次做完，原计划的三人分工在领域中不可执行。
- 本记录仍属赛后补录，并非每日站会产出。

**行动项**

1. T3-09 必须具体落为：删除 `SPRINT_MODULES`、阶段 2/4 改全量、删除集成测试的 `continue-on-error`，并确认 CI 日志中出现 `co-discovery` 模块名。
2. `CampusOverflowFlowIT` 转硬门禁后，需连续若干次 run 稳定全绿再打 v0.9.0；本地 Docker 不可用，端到端验证只能在 CI 上完成。
3. T3-12 演示走查为全员任务，不可由单人代做，需在 v0.9.0 之前完成并在本文件补录走查记录。
4. 打 `v0.9.0` 标签、创建 Release、关闭 Sprint 3 里程碑与 9 个 Issue，完成后将本文件转为终版。
