# Sprint 0 记录（第 1 周）

> 本文件对应实验指导书 9.1 要求的 `docs/scrum/sprint-N.md`。内容按仓库可核验证据（Issue、PR、提交、标签、测试运行结果）整理；日期取自提交与标签时间，本记录为 Sprint 结束后的整理版。

## 1. Sprint 目标

搭起可运行、可协作、可持续集成的基础设施：受保护的主干与看板、一键启动环境、统一语言与上下文映射、渐进式 CI 门禁、注册登录与加入课程。

## 2. Sprint Backlog

| Issue | 内容 | 状态 |
|---|---|---|
| #7 | PO 创建的共享仓库、受保护分支模板与看板 | closed |
| #8 | 一键启动 MySQL/Redis/后端/前端并跑通测试 | closed |
| #9 | 事件风暴产出的统一语言与上下文映射 | closed |
| #10 | 渐进式 CI 门禁 | closed |
| #11 | 注册登录并在课程页面浏览、加入课程 | closed |

Sprint 0 里程碑：5 个 PBI 全部关闭，里程碑已归档（closed）。

同期团队事务（不占 PBI 配额）：

| Issue | 内容 | 状态 |
|---|---|---|
| #2 | 配置 main 分支保护规则 | closed（**实际未落地**，见第 4 节） |
| #3 | Git 协作首次演练：提交 hello-姓名.md | closed |
| #4 | 起草团队章程 v1.0 | closed |
| #1 | 跑通前后端本地环境（Docker + MySQL + Redis） | open（Redis 未跑通） |
| #5 | 建立每日站会机制并记录 | open |
| #6 | 阅读 Identity 参考代码与架构文档 | open |

## 3. 完成情况与证据

- 代码：共享内核（`IntegrationEvent`、`EventIds`、发件箱接口）+ Identity 领域与接口层；52 个提交，其中本 Sprint 为主要集中期（2026-10-22 起）。
- 测试：共享内核 2 个 + Identity 领域 9 个（`CourseTest` 3、`UserTest` 6），本地 `mvn test -pl co-identity/co-identity-domain -am` 全绿。
- 文档：`docs/ddd/context-map.md`、`docs/ddd/ubiquitous-language.md`、`docs/notes/architecture.md`、`docs/adr/001~010`。
- 版本：`v0.1.0`（2026-10-26）已打标签并创建 Release「Sprint 0 验收（基础设施就绪）」。

## 4. 对照 6.2.4 验收标准

| # | 验收项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 现场启动系统、注册→登录→加入课程、Swagger UI 可访问 | 部分通过 | 后端可启动（Flyway 5 个迁移 + 演示数据落库，启动 9.9 s）；登录会话依赖 Redis，本机 Redis 未运行，登录链路未实机走通 |
| 2 | 全员加入组织与团队并克隆仓库 | 无法核验 | 仓库内无成员加入记录；Issue 未登记 |
| 3 | main/develop 已受保护，PO 也不能绕过 | **未满足** | `/branches/main/protection`、`/branches/develop/protection` 均 404，仓库无任何 ruleset；15 个分支全部未保护 |
| 4 | 看板 48 个 PBI 已排序，Sprint 1 的 PBI 满足 DoR | 部分 | 48 个 PBI 已建为 Issue 并分入 5 个里程碑；看板排序与 DoR 检查无留痕 |
| 5 | 每人至少 1 个已合并 PR 和 1 次评审 | **未满足** | 8 个 PR 全部已合并，但 review 数为 0；Sherpherd-W 无任何 PR |
| 6 | CI 在 develop 上绿色（共享内核与 Identity 测试） | **未满足** | 4 次 workflow run 全部 failure，job 在 2 秒内结束、无 runner 分配、无日志 blob |
| 7 | `docs/ddd/`（上下文映射、统一语言、事件清单）、`docs/notes/architecture.md`、ADR-009 已合入 | 部分通过 | 前三项中事件清单当时缺失，已在本轮补为 `docs/ddd/event-catalog.md`；architecture.md 与 ADR-009 均已在 main |
| 8 | main 目录打 `v0.1.0` | 通过 | 标签与 Release 均在，指向 Sprint 0 收尾提交 |

## 5. 回顾

**做得好**

- 分层架构与共享内核先立起来，后续 Sprint 的领域/应用层拆分没有返工。
- 文档先行：ADR 001~010 覆盖了模块化单体、发件箱、服务端会话、账本等关键决策。
- 提交历史连贯，Sprint 0 的 5 个 PBI 全部关闭。

**待改进**

- **Issue #2 被关闭但分支保护从未配置**，属于"标记完成但未验收"的典型问题，本轮已补配置并对 #2 追加说明。
- CI 从第一次触发起就没有成功过，Sprint 验收依赖 CI 状态，这条一直没有被当成阻塞项处理。
- PR 流程事实上退化为"自己 push、自己 merge"，评审环节从 Sprint 0 起就是空的。
- 环境项 #1 一直 open（Redis 未跑通），直接影响所有需要会话的演示。

**行动项（带入 Sprint 1）**

1. 分支保护配置完成后再关闭 #2，并把规则写进 README 的协作约定。
2. 排查 Actions 失败原因（runner 未分配 → 组织级 Actions 配额/计费），必要时联系教师。
3. 每个 PR 至少指定 1 名非作者评审人。
