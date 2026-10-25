# Product Backlog（需求建模工作坊产出 · Lab2）

> 版本：1.0 ｜ 维护者：PO（zhaojeak）｜ 同步：GitHub Projects 看板 + 48 个 Issue
> 排序原则（指导书 5.3）：依赖优先（Q&A → Reputation → Discovery）+ 价值优先（MVP 闭环）+ 风险前置（CI/发件箱阻塞）。
> 故事点采用斐波那契（1/2/3/5/8），以 PB-11（投票 = 5 点）为基准故事。

## 用户角色（指导书 5.1）

| 角色 | 描述 | 关键诉求 |
| --- | --- | --- |
| 访客 | 未登录用户 | 浏览问题、搜索、查看排行榜 |
| 学生（STUDENT） | 自助注册，经管理员认证后显示认证徽章 | 提问、回答、投票、评论、获得声誉与徽章 |
| 助教（TA）/ 教师（TEACHER） | 管理员创建；课程内为教师或助教时即"课程管理者" | 代为采纳、教师认证、发起悬赏、删除不当内容 |
| 管理员（ADMIN） | 平台运维 | 认证用户、创建教职工、调整角色、查看对账、禁用账号 |

## 史诗（指导书 5.2）

| 编号 | 史诗 | 范围 |
| --- | --- | --- |
| E0 | 协作与交付基础设施 | 仓库、分支保护、看板、CI 门禁、行走骨架 |
| E1 | 身份与课程（Identity） | 注册登录、课程、管理功能与扩展 |
| E2 | 课程问答（Q&A） | 问题、回答、采纳、投票、评论、认证、删除、查询 |
| E3 | 声誉与徽章（Reputation） | 账户、账本、计分规则、徽章、排行榜、对账 |
| E4 | 悬赏（Reputation） | 发起、冻结、结算、到期退款 |
| E5 | 内容发现（Discovery） | 搜索投影、全文检索、相关推荐、通知与提及 |
| E6 | 质量与发布 | 测试、覆盖率、集成测试、可观测性、发布 |
| E7 | 前端体验 | 各故事的界面补全与体验优化 |

## 完整 Product Backlog（已排序，48 项）

| # | ID | 用户故事 | 史诗 | 优先级 | SP | Sprint | 实现落点 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | PB-01 | 作为团队，我们需要由 PO 创建的共享 GitHub 仓库、受保护分支、模板与看板，以便所有代码经评审后集成 | E0 | Must | 3 | S0 | PO 个人仓库设置、.github/ |
| 2 | PB-02 | 作为开发者，我需要一键启动 MySQL/Redis/后端/前端并跑通测试，以便在一致的环境开发 | E0 | Must | 2 | S0 | deploy/docker-compose.yml |
| 3 | PB-03 | 作为团队，我们需要事件风暴产出的统一语言与上下文映射，以便沟通和编码一致 | E0 | Must | 3 | S0 | docs/ddd/ |
| 4 | PB-04 | 作为团队，我们需要渐进式 CI 门禁，以便每个 Sprint 结束时流水线都是绿的 | E0 | Must | 2 | S0 | .github/workflows/ci.yml、ADR-009 |
| 5 | PB-05 | 作为学生，我可以注册、登录并在课程页面浏览和加入课程，以便在课程问答区提问 | E1 | Must | 3 | S0 | Identity（已实现）+ 前端课程页 |
| 6 | PB-06 | 作为学生，我可以发布带标签、可关联课程的问题，以便向同学和老师求助 | E2 | Must | 5 | S1 | Question.post、值对象、QuestionCommandService.post |
| 7 | PB-07 | 作为用户，我可以分页浏览（按课程筛选）问题列表，以便发现待解决的问题 | E2 | Must | 3 | S1 | QuestionQueryService.list |
| 8 | PB-08 | 作为用户，我可以查看问题详情（渲染后的正文、回答、评论、我的投票、可用操作），以便获取帮助 | E2 | Must | 5 | S1 | QuestionQueryService.detail |
| 9 | PB-09 | 作为学生，我可以回答未删除的问题，以便帮助提问者 | E2 | Must | 3 | S1 | Answer.submit、AnswerCommandService.submit |
| 10 | PB-10 | 作为提问者或课程管理者，我可以采纳一个最佳答案，以便标记问题已解决 | E2 | Must | 5 | S1 | Question.accept、AnswerCommandService.accept |
| 11 | PB-11 | 作为用户，我可以对他人的问题/回答投票、改投或撤销，以便评价内容质量 | E2 | Must | 5 | S1 | Vote、VoteService |
| 12 | PB-12 | 作为作者或课程管理者，我可以编辑问题和回答，以便修正内容 | E2 | Must | 3 | S1 | edit 方法 |
| 13 | PB-13 | 作为作者或课程管理者，我可以软删除问题和回答，以便清理不当内容且可追责 | E2 | Must | 3 | S1 | softDelete、delete 用例 |
| 14 | PB-14 | 作为课程教师/助教，我可以为优质回答做"教师认证"，以便给学生权威指引 | E2 | Should | 2 | S1 | Answer.endorse、AnswerCommandService.endorse |
| 15 | PB-15 | 作为用户，我可以评论问题或回答并进行一层回复，以便追问与澄清 | E2 | Must | 3 | S1 | Comment.post、CommentService |
| 16 | PB-16 | 作为用户，我可以看到热门标签，以便按主题浏览 | E2 | Should | 1 | S1 | popularTags、Tags.names |
| 17 | PB-17 | 作为其他上下文，我可以通过 QaQueryApi 查询问题引用，以便悬赏与通知使用 | E2 | Must | 1 | S1 | QaQueryApiImpl.findQuestion |
| 18 | PB-18 | 作为团队，我们需要行走骨架（未实现监听器暂为空实现），以便发件箱不被阻塞 | E0 | Must | 1 | S1 | Reputation/Discovery 的 @EventListener |
| 19 | PB-19 | 作为用户，我可以在前端编辑/删除内容、发表评论与回复，并看到友好的错误提示 | E7 | Should | 3 | S1 | frontend/src/pages/* |
| 20 | PB-20 | 作为新用户，系统为我开立声誉账户（教师获得初始悬赏积分），以便参与激励体系 | E3 | Must | 3 | S2 | ReputationAccount.open、initialPoints、on(UserRegistered) |
| 21 | PB-21 | 作为回答者，回答被采纳时我获得 +15 声誉（自问自答不加分）并记入账本 | E3 | Must | 5 | S2 | on(AnswerAccepted)、applyReputation |
| 22 | PB-22 | 作为作者，被赞 +10、被踩 −2，改投/撤销按差值计算，且每日正向投票所得 ≤ 200 | E3 | Must | 5 | S2 | voteEffect/voteDelta/applyDailyCap、on(VoteCast) |
| 23 | PB-23 | 作为用户，我在达成里程碑时自动获得徽章 | E3 | Should | 3 | S2 | BadgePolicy、BadgeService.grant、其余事件处理 |
| 24 | PB-24 | 作为课程管理者，我可以为问题发起悬赏（10–500 分，1–14 天）并冻结积分 | E4 | Must | 5 | S2 | Bounty.open、freezePoints、BountyService.open/latest |
| 25 | PB-25 | 作为回答者，问题采纳时我自动赢得悬赏；作为发起人，到期未采纳时积分自动退回 | E4 | Must | 5 | S2 | award/close/isDue、settleOnAccept/expireDue |
| 26 | PB-26 | 作为用户，我可以查看周/月/学期/总排行榜 | E3 | Should | 5 | S2 | AcademicTerm、LeaderboardService |
| 27 | PB-27 | 作为用户，我可以查看他人声誉档案与徽章，并查看自己的积分与声誉流水 | E3 | Must | 3 | S2 | ReputationQueryService、ReputationApiImpl |
| 28 | PB-28 | 作为管理员，我可以查看每日对账结果，以便发现声誉异常（FF-8） | E3 | Should | 2 | S2 | ReconciliationService |
| 29 | PB-29 | 作为教师，我可以在对话框中发起悬赏并看到倒计时；作为用户，我有个人声誉主页 | E7 | Should | 3 | S2 | 前端悬赏对话框、声誉页 |
| 30 | PB-30 | 作为团队，我们需要 Reputation 应用层单元测试（手写 Fake），以便安全重构 | E6 | Must | 3 | S2 | co-reputation-application/src/test |
| 31 | PB-31 | 作为用户，我可以用中英文关键词搜索并按标签/未回答/悬赏筛选、按相关度/最新/活跃/热度排序 | E5 | Must | 5 | S3 | SearchQuery.terms、DiscoveryQueryService.search |
| 32 | PB-32 | 作为系统，搜索投影随问答与悬赏事件幂等更新，以便搜索结果及时准确 | E5 | Must | 5 | S3 | SearchIndexProjector |
| 33 | PB-33 | 作为读者，我在问题详情页看到相关问题推荐 | E5 | Should | 3 | S3 | RelatedQuestionPolicy、related |
| 34 | PB-34 | 作为用户，我会收到新回答、被采纳、被认证、赢得悬赏、获得徽章等通知 | E5 | Must | 5 | S3 | Notification.create、NotificationProjector |
| 35 | PB-35 | 作为用户，评论中被 @ 时我会收到提醒（每条最多 5 人） | E5 | Should | 3 | S3 | MentionParser、on(CommentPosted) |
| 36 | PB-36 | 作为用户，我可以查看通知列表、未读数，并标记单条或全部已读 | E5 | Must | 3 | S3 | NotificationService、markRead |
| 37 | PB-37 | 作为团队，端到端集成测试全绿且领域覆盖率 ≥ 70%，以便发布候选版本 | E6 | Must | 3 | S3 | CampusOverflowFlowIT、JaCoCo |
| 38 | PB-38 | 作为演示者，demo profile 能加载完整演示数据 | E6 | Should | 1 | S3 | DemoDataSeeder |
| 39 | PB-39 | 作为团队，我们需要 Discovery 应用层单元测试（验证投影幂等） | E6 | Must | 3 | S3 | co-discovery-application/src/test |
| 40 | PB-40 | 作为管理员，我可以禁用违规账号并使其立即下线（ADR-003） | E1 | Should | 5 | S4 | User.disable（已有）+ 服务/接口/会话清理 |
| 41 | PB-41 | 作为用户，我可以查看和修改个人资料（学院、班级） | E1 | Could | 3 | S4 | Identity 扩展 |
| 42 | PB-42 | 作为用户，我获得更好的前端体验：分页/骨架屏/错误重试、Markdown 实时预览 | E7 | Should | 3 | S4 | 前端 |
| 43 | PB-43 | 作为用户，通知红点实时刷新（SSE 替代 30 秒轮询） | E7 | Could | 5 | S4 | 前端 + 新接口 |
| 44 | PB-44 | 作为运维者，我可以在 Grafana 看到发件箱积压、死信、对账不一致等指标 | E6 | Should | 2 | S4 | Prometheus/Grafana |
| 45 | PB-45 | 作为团队，我们对问题列表做压测并验证缓存命中 | E6 | Could | 3 | S4 | 压测脚本 |
| 46 | PB-46 | 作为干系人，我可以用 docker compose 一键部署 v1.0 并阅读 Release Notes | E6 | Must | 2 | S4 | GitHub Release、镜像 |
| 47 | PB-47 | 作为管理员，我有管理页面：认证学生、创建教职工账号 | E1 | Could | 3 | S4 | 前端 + 已有接口 |
| 48 | PB-48 | 作为教师，我可以审批学生的选课申请 | E1 | Won't（本期） | 8 | — | Identity 扩展（留作后续） |

## 各 Sprint 计划故事点

| Sprint | 计划点 | 说明 |
| --- | --- | --- |
| S0 | 13 | PB-01~05 |
| S1 | 43 | PB-06~19 |
| S2 | 42 | PB-20~30 |
| S3 | 31 | PB-31~39 |
| S4 | 26 | PB-40~47（Must/Should = 12，Could 按容量选做） |

## 依赖关系摘要

- **事件链路**：Q&A 是所有事件的源头 → Reputation 的悬赏依赖 QaQueryApi → Discovery 同时订阅 Q&A 与 Reputation 的事件。因此天然顺序为 Q&A → Reputation → Discovery。
- **PB-18 行走骨架**：S1 建立（Reputation/Discovery 监听器空实现），S2 移除 Reputation 部分，S3 全部移除，虚线依赖。
- **PB-04（CI 门禁）**：范围随 Sprint 逐步扩大（S0 共享内核+Identity → S1 +Q&A → S2 +Reputation → S3 全量）。
- **PB-37（端到端）**：依赖 S1~S3 全部业务事件链路打通，是 S3 的集成验收项。

## 导入 GitHub 的方法（PO 执行）

1. 将 `backlog.csv` 与 `.github/ISSUE_TEMPLATE/story-body.md` 放在同一目录。
2. 安装 GitHub CLI（`winget install GitHub.cli`）并 `gh auth login`。
3. 逐 Sprint 导入（示例，S1）：

```bash
while IFS=, read -r id title labels points; do
  gh issue create --repo zhaojeak/campus-overflow-3 --title "[$id] $title" \
    --label "type:story,$labels" --milestone "Sprint 1" \
    --body-file .github/ISSUE_TEMPLATE/story-body.md
done < backlog.csv
```

4. 导入后在 Projects 表格视图按 Rank 排序，设置 Story Points 与 Sprint 自定义字段。
