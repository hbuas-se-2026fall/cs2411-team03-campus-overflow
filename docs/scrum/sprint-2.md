# Sprint 2 记录（第 4–5 周）

> 本文件对应实验指导书 9.1 要求的 `docs/scrum/sprint-N.md`。内容按仓库可核验证据整理；时间为标签与提交时间，本记录为 Sprint 结束后的整理版。

## 1. Sprint 目标

交付声誉与激励：声誉账户与账本、采纳/投票计分、徽章、悬赏冻结与结算、排行榜、声誉档案与流水、每日对账。

## 2. Sprint Backlog

| Issue | 内容 | 状态 |
|---|---|---|
| #26 | 新用户自动开立声誉账户 | closed |
| #27 | 回答被采纳获得 15 声誉并记入账本 | closed |
| #28 | 被赞 +10、被踩 −2、改投/撤销按差值，每日正向投票所得不超过 200 | closed |
| #29 | 达成里程碑自动授予徽章 | closed |
| #30 | 课程管理者为问题发起悬赏并冻结积分 | closed |
| #31 | 采纳时自动结算悬赏，过期未采纳自动退回 | closed |
| #32 | 查看周/月/学期/总排行榜 | closed |
| #33 | 查看他人声誉档案与徽章，查看自己的积分与流水 | closed |
| #34 | 管理员查看每日对账结果 | closed |
| #35 | 教师对话框发起悬赏并显示倒计时，个人声誉主页 | closed |
| #36 | Reputation 应用层单元测试 | closed |
| #73 | T2-03 `Bounty.open/award/close/isDue/ensureOpen`（领域层） | closed（Can0401） |
| #74 | T2-07 `BountyService.open/latest/settleOnAccept/expireDue/closeAndRefund`（应用层） | closed（Can0401） |
| #77 | T2-05 `BadgeService.grant` + `ReputationEventHandlers` 注册/回答/认证事件 | closed |
| #78 | T2-09 `ReputationQueryService.profile/ledger` + `ReputationApiImpl.summary/reputations` | closed |

Sprint 2 里程碑：15 个 Issue 全部关闭。

## 3. 完成情况与证据

| 项目 | 证据 |
|---|---|
| 领域层测试 | `co-reputation-domain` 15 个测试全绿（`mvn verify -DskipITs -pl co-reputation/co-reputation-domain -am`，覆盖率门槛 `reputation.domain.coverage.minimum=0.70` 达标） |
| 应用层测试 | `ReputationApplicationTest` 21 个用例全绿（开户幂等、采纳 +15、每日上限、投票重放幂等、悬赏冻结与结算、到期退款、非管理者 403、重复悬赏 409、排行榜幂等、档案隐藏积分、流水 403 等） |
| 演示环境 | `demo` profile 启动成功：Flyway 依序应用 platform/identity/qa/reputation/discovery 五个迁移，`Started CampusOverflowApplication in 9.891 seconds`，`DemoDataSeeder` 初始化 6 个账号（admin01 / T2001 / TA3001 / 2023001–2023003，密码 `Campus2026`） |
| HTTP 实测 | `GET /api/v1/users/5/reputation` → 200（声誉 35，他人视角不返回可用/冻结积分，3 枚徽章）；`GET /api/v1/questions/2/bounty` → 200（50 分 OPEN） |
| 数据核对 | `rep_account` 6 行（admin 1000、教师 950 + 冻结 50、TA 500、学生 4 声誉 10、学生 5 声誉 35）；`rep_bounty` 1 行；`rep_ledger` 4 行；`rep_badge` 4 行 |
| 版本 | `v0.3.0`（2026-11-12）已打标签并创建 Release「Sprint 2 验收（声誉与激励）」 |

## 4. 对照 6.4.4 的 6 条验收标准

| # | 验收项 | 结论 |
|---|---|---|
| 1 | 教师初始 1000、学生初始 0 | 通过（领域规则 `initialPoints`：TEACHER 1000 / STUDENT 0，TA 500、ADMIN 1000 见 ADR-010；`rep_account` 实际数据一致） |
| 2 | 采纳 +15、写流水、发徽章；自答不变 | 通过（应用层测试覆盖，含账本只记一次的断言） |
| 3 | 被赞 +10、被踩 −2、赞改踩 −12；每日正向投票所得 ≤ 200 | 通过（领域测试覆盖 `voteEffect` 与 `applyDailyCap` 边界，含 195→5、200→0） |
| 4 | 教师 50 分 / 7 天悬赏，可用 950 冻结 50；采纳后 +50；逾期退回 | 通过（领域与应用层测试 + HTTP 实测悬赏数据） |
| 5 | 周/月/学期/总排行榜可切换；对账返回空列表；他人查流水 403 `REP-3002` | 部分通过：403 与对账差异有测试覆盖；排行榜 HTTP 因 Redis 未运行返回 500（`RedisConnectionFailureException`），未实机演示 |
| 6 | `mvn test` 全绿且 main 打 `v0.3.0` | 部分通过：本地 QA/Identity/Reputation/ArchUnit 全绿，标签与 Release 已打；GitHub Actions 四次 run 全部 failure（runner 未分配），CI 状态不可用 |

## 5. 回顾

**做得好**

- 领域规则全部收敛在 `ReputationRulebook` / `ReputationAccount` / `Bounty` / `BadgePolicy`，应用层 `ReputationEventHandlers` 只做事件到用例的编排，因此 21 个应用层用例可以在内存夹具上跑完。
- 事件侧补齐了消费幂等：账本用 `(event_id, user_id, reason)` 唯一键，徽章用 `(user_id, badge_code)` 唯一键，排行榜用 `processed_event` 标记——重复投递不会重复计分。
- 悬赏的积分冻结先于结算，退款路径（自答退款、逾期退款）都有对应用例，避免了"冻结后卡死"。

**待改进**

- CI 依旧是红的，Sprint 2 的"全绿"只能靠本地复现，验收现场无法出示流水线证据。
- PR #79、#80 由 API 直接合并，没有非作者 Approve；按 9.3 节，这属于绕过评审，协作证据项有被扣分的风险。
- 15 个 Issue 中只有 #73、#74 填了 assignee，其余 13 个没有认领记录。
- Sherpherd-W（SM）本 Sprint 无提交、无 PR；站会机制（#5）仍是 open。
- 本 Sprint 的 `docs/scrum/sprint-2.md` 与 `docs/ddd/event-catalog.md` 是在 Sprint 结束后补录的，属于过程记录滞后。

**行动项（带入 Sprint 3）**

1. 修复 CI 触发链路（先确认组织级 Actions 用量/计费，再决定是否改 self-hosted）。
2. 所有 PR 走"作者以外的评审人 Approve 后合并"，不再由单人直接合并。
3. 每个 Issue 开工即指派 assignee，结束补验证说明。
