# Sprint 4 记录（第 8 周）

> 对应实验指导书 9.1 要求的 `docs/scrum/sprint-N.md`。内容按仓库可核验证据（Issue、PR、CI 运行、标签与 Release）整理，时间为 PR 合并与标签的实际时间。
>
> 最后更新：2026-11-25

## 1. Sprint 目标

按 6.6 收尾：把系统做成"可交付"而不是"能演示"——一键部署可复现、运维能看见关键指标、前端体验补齐、管理员有账号管理入口，并产出可评审的 Release 与文档。

## 2. Sprint Backlog

8 个 PBI 全部关闭，里程碑 Sprint 4 已归档（closed）。

| Issue | 内容 | 优先级 | 实现 PR | 状态 |
|---|---|---|---|---|
| #52 | 用 `docker compose` 一键部署 v1.0 并阅读 Release Notes | must | #88、#93（修 jar 路径） | closed |
| #50 | Grafana 看到发件箱积压、死信、对账不一致等指标 | should | #90（面板 provisioning，ADR-012） | closed |
| #48 | 更好的前端体验（Markdown 渲染、代码高亮） | should | #89 | closed |
| #53 | 管理页面认证学生、创建教职工账号 | could | #91 | closed |
| #49 | 通知红点实时刷新 | could | #92（SSE） | closed |
| #51 | 问题列表与详情压测、验证缓存命中 | could | #95、#97（实测数据回填） | closed |
| #47 | 查看和修改个人资料 | could | #100 | closed |
| #46 | 管理员禁用违规账号并使其立即下线 | should | #100 | closed |

同期合入的支撑性改动（不占 PBI，但影响验收）：#94（ADR-011 一键部署门禁 + 用户手册）、#98（开启 percentile histogram，让详情页 P95 可观测）、#99（播种期暂停发件箱中继，消除启动竞态）。

## 3. 完成情况与证据

**部署**

`docker compose up` 一条命令拉起 MySQL、Redis、后端、前端，冒烟 job 在 CI 中实际执行并通过。部署说明与门禁规则见 ADR-011，使用手册见 `docs/`。

**可观测性**

Grafana 面板随 compose 启动自动 provisioning（ADR-012），覆盖发件箱积压、死信数、对账不一致、按端点 P95。P95 能出数依赖 #98 打开 percentile histogram —— 这是 Sprint 4 期间发现的真缺陷：此前详情页延迟在面板上恒为空。

**质量门禁**

领域覆盖率门槛 0.70（根 pom `domain.coverage.minimum`，reputation 模块单独一份），CI 五个阶段（编译 → 单测 → ArchUnit → 覆盖率 → 集成测试）全部保留，Sprint 4 未为通过而放宽任何阈值。

**版本与发布**

- `v1.0.0`（2026-11-19）—— 对应 Sprint 3/4 主体完成的提交，附 Release Notes 与部署说明；
- `v1.0.1`（2026-11-25）—— 修正 #98/#99/#100 三个问题后重发。

## 4. 对照 9.1 验收

| 维度 | 结论 | 依据 |
|---|---|---|
| 可运行软件 | 通过 | main 打标签，`docker compose` 一键启动，CI 冒烟 job 覆盖 |
| 提交历史 | **有风险** | 4 人合计 101 次提交，但分布不均：Sherpherd-W 12 次中 11 次集中在 11/16 那一周，Can0401 7 次集中在 Sprint 2–3，两人各有整周零提交 |
| 贡献者（每人有领域层+应用层 PR） | 部分 | Arthur20060209、Sherpherd-W、zhaojeak 均有领域/应用层 PR；Can0401 的领域层 PR 集中在 Sprint 2（Bounty 状态机） |
| PR/Review | 部分 | 28 个 PR 全部经 PR 合入；但早期 PR 以作者自合为主，Sprint 3 起改为非作者 1 人 Approve |
| Issue 与看板 | 通过 | 52 个 PBI 均有 Issue 与验收标准，关联里程碑；Sprint 4 的 PBI 在创建时未填 assignee，是本 Sprint 的执行缺口 |
| 分支规范 | **未满足** | 18 个已合并的 `feature/*`、`fix/*`、`docs/*` 分支未删除 |
| CI/CD | 通过 | 每次 PR 触发，红灯在 24 小时内修复（#95 的检查失败由 #97/#98 当日处理） |
| Scrum 工件 | 部分 | 里程碑描述齐全、本记录与 Sprint 0–3 记录齐全；燃尽图见 `docs/scrum/burndown.md` |

## 5. 回顾

**做得好**

- 部署从"Sprint 3 的脚本"变成"CI 会跑的门禁"，#93 修掉 jar 路径错误后才算真正一键可用——这条是被冒烟 job 逼出来的，不是靠人工验证。
- 压测有真实数据回填（#97），不是只写一份报告。
- 发现并修掉了两个"看不见的缺陷"：P95 恒空、启动竞态死锁。这类问题只有真跑起来才会暴露。

**待改进**

- **分支该删没删**（18 个），是流程纪律问题，不是能力问题。
- Sprint 4 的 PBI 创建时没有 assignee，导致"谁在做"只能靠 PR 作者反推，与 Sprint 3 起的"开工即填 assignee"约定不一致。
- Can0401 在 Sprint 4 无提交，团队分工在收尾阶段实际落到 3 人身上，最后一周的工作量集中度偏高。
- 文档类交付（领域模型、Sprint 记录、燃尽图、覆盖率报告归档）拖到最后统一补，没有随 Sprint 增量产出。

**行动项（遗留至项目收尾）**

1. 清理全部已合并分支，并在 README 的协作约定里写明"合并即删"。
2. `docs/reports/` 补归档覆盖率报告，CI 构件同时上传 JaCoCo HTML。
3. 补齐领域模型与燃尽图，使 `docs/ddd/`、`docs/scrum/` 满足 9.4 清单。
4. 答辩材料（`docs/final/`）与演示视频由团队在第 8 周评审会前完成。
