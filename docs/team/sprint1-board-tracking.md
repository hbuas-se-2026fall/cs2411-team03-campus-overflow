# Sprint 1 看板状态追踪（SM 维护 · Lab2）

> 维护者：SM（Sherpherd）｜ 更新时机：每日站会后
> 目的：作为 Sprint 1（第 3 周，需求建模工作坊验收周）的看板与 Issue 状态追踪底稿；
> 对应指导书 4.1（SM 职责：维护看板与燃尽图）、4.3（Sprint Backlog 载体）、6.2.4（Sprint 0 验收）。

## 1. Sprint 1 范围与目标

| 项 | 内容 |
| --- | --- |
| Milestone | Sprint 1（GitHub Milestone #3） |
| Sprint Goal | 学生可以完成“提问→回答→投票→评论→采纳”的完整问答闭环（v0.2.0） |
| 范围 | PB-06 ~ PB-19，共 14 个 PBI（Issue #19 ~ #32） |
| 状态日期 | 2026-10-26（Lab2 验收周） |

## 2. Issue 状态追踪表（截至 2026-10-26）

| Issue | PBI | 用户故事（摘要） | 优先级 | SP | 看板列 | 负责人 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| #19 | PB-06 | 发布带标签、可关联课程的问题 | Must | 5 | Done | — | ✅ 已关闭（实现已合入） |
| #20 | PB-07 | 分页浏览（按课程筛选）问题列表 | Must | 3 | To Do | 待认领 | Open |
| #21 | PB-08 | 查看问题详情（正文/回答/评论/权限） | Must | 5 | To Do | 待认领 | Open |
| #22 | PB-09 | 回答未删除的问题 | Must | 3 | To Do | 待认领 | Open |
| #23 | PB-10 | 采纳一个最佳答案 | Must | 5 | To Do | 待认领 | Open |
| #24 | PB-11 | 投票 / 改投 / 撤销 | Must | 5 | To Do | 待认领 | Open |
| #25 | PB-12 | 编辑问题和回答 | Must | 3 | To Do | 待认领 | Open |
| #26 | PB-13 | 软删除问题和回答 | Must | 3 | To Do | 待认领 | Open |
| #27 | PB-14 | 教师/助教为优质回答认证 | Should | 2 | To Do | 待认领 | Open |
| #28 | PB-15 | 评论问题或回答（一层回复） | Must | 3 | To Do | 待认领 | Open |
| #29 | PB-16 | 查看热门标签 | Should | 1 | To Do | 待认领 | Open |
| #30 | PB-17 | 通过 QaQueryApi 查询问题引用 | Must | 1 | To Do | 待认领 | Open |
| #31 | PB-18 | 行走骨架（Reputation/Discovery 空监听） | Must | 1 | To Do | 待认领 | Open |
| #32 | PB-19 | 前端：编辑删除、评论回复、友好错误提示 | Should | 3 | In Review | Arthur20060209 | Open（PR #62 已合并，待关闭） |

> 说明：
> - “看板列”为 Projects「Sprint Backlog」看板应放置的列；当前看板列归属需 SM 在 GitHub Projects 页面手工拖拽对齐（SM 网页操作，非代码变更）。
> - PB-19 对应 PR #62 已 merged，Issue #32 可随 PR 关联关闭。

## 3. DoR 就绪检查（Sprint 1 全部 PBI）

| PBI | 用户故事格式 | 史诗关联 | 验收标准（Gherkin） | 估算 SP | 依赖识别 | DoR |
| --- | --- | --- | --- | --- | --- | --- |
| PB-06~PB-19（14 项） | ✅ 均为“作为…我希望…以便…” | ✅ backlog.csv 标注史诗 | ✅ 已合入 `docs/backlog/acceptance-criteria.md` S1 章节（含错误场景与错误码） | ✅ backlog.csv 已估 | ✅ 依赖链：Q&A→Reputation→Discovery | ✅ 全部就绪 |

> 验收标准覆盖核对：S1 章节包含 PB-06 发布问题、PB-07/08 列表与详情、PB-09 回答、PB-10 采纳、PB-11 投票、PB-12/13 编辑删除、PB-14 教师认证、PB-15 评论、PB-16 热门标签、PB-17 QaQueryApi、PB-18 行走骨架、PB-19 前端。

## 4. SM 障碍清除记录（Lab2）

| 日期 | 障碍 | 处理 | 状态 |
| --- | --- | --- | --- |
| 2026-10-26 | CI「架构约束检查」在干净环境失败（`-pl co-bootstrap` 缺 `-am` 兄弟模块依赖） | 修复 ci.yml：加 `-am` + `-Dsurefire.failIfNoSpecifiedTests=false`（PR #63，已合并）；main CI 恢复全绿 | ✅ 已清除 |
| 2026-10-26 | PR #62 后端 check 红（分支内旧 ci.yml，非代码问题） | 将 main 合入 feature/pb19-frontend 触发重跑，CI 全绿后 PR 已合并 | ✅ 已清除 |

## 5. 待 SM 在 GitHub 网页完成的动作

1. 打开 Projects「Sprint Backlog」看板，将 Issue #20~#31 拖入 **To Do**（或按开发认领移动到 In Progress）。
2. 将 Issue #32 移动到 **Done**（PR #62 已合并；如未自动关闭，手动关闭）。
3. 为 Sprint 1 目标 v0.2.0 在 main 打标签（合并后由 PO/SM 确认）。

---
*本文件为 SM（Sherpherd）在 Lab2 的看板与 Issue 状态追踪交付物，内容与 GitHub 实时状态核对生成。*
