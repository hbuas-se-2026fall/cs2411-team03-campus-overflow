# Lab2 · Can0401 交付物 ③：PB-06~PB-13 任务拆分、认领与估点

> 拆分人：Can0401 ｜ 依据：指导书 6.3.1（参考 Sprint Backlog）、6.3.2/6.3.3（实现要点）、附录 A（TODO 映射）、实际骨架代码与测试类
> 团队规模：4 人（PO/QA、SM、Dev×2）。PB-06~13 合计 32 故事点（与指导书一致）
> 日期：2026-10-26 ｜ 状态：建议计划会评审后认领

---

## 1. 说明

- 任务粒度 ≤ 1 个工作日（≤ 8h）；**估点用斐波那契（PBI 级 1/2/3/5/8），工时用理想小时**（指导书 4.4.2）。
- 代码中 `TODO(S2)` = 指导书 Sprint 1（附录 A 说明），下表直接引用方法名。
- 附录 A 统计 Sprint 1 共 **36 个**待实现方法；本表任务覆盖其中 PB-06~13 对应的 **30 个**（其余 6 个：回答认证 `endorse`×2 → PB-14、`popularTags` → PB-16、评论×2 → PB-15、`QaQueryApiImpl.findQuestion` → PB-17，不在本表认领范围）。
- 验收标准 = 已有测试类全绿（DoD 第 1 项）+ 自建测试/集成测试（DoD 第 3 项）。
- PBI 故事点为 PO 已定值，本表复核确认一致；任务级 SP 供 GitHub Projects 燃尽图使用（可选）。

## 2. 任务拆分表（15 项，合计 58h）

| 任务 | 内容（落点） | PBI | 层/模块 | 验收标准（测试） | 工时 | 任务级SP |
|---|---|---|---|---|---|---|
| QA-T01 | `MarkdownBody.ofQuestion`（20–30000，团队确认下限）、`Tags.names`（去重后按序输出）、核验 `Title` 边界 | PB-06 | qa-domain | `QuestionTest#titleMustBeBetween10And150Chars`、`tagsAreNormalizedAndDeduplicated`、`requiresOneToFiveTags`、`rejectsIllegalTagCharacters` | 2h | 1 |
| QA-T02 | `Question.post` 工厂（含 `ensureNotDeleted`/`ensureOpenForAnswers` 私有助手、标签 1–5、courseId 可选） | PB-06 | qa-domain | `QuestionTest.Posting` 组全绿 | 2h | 1 |
| QA-T03 | `QuestionCommandService.post`：`identity.findCourse` 校验（404 ID-4002）→ 建标签 → 工厂 → `adjustUsage(+1)` → 保存 → 渲染 `toPlainText(2000)` → 发 `QuestionPostedEvent` → 缓存失效；自建 `QuestionCommandServiceTest` | PB-06 | qa-application | 自建单测 + 集成（含发件箱出表） | 6h | 3 |
| QA-T04 | `Question.edit` + `Answer.edit`（`ensureNotDeleted`、作者或管理者校验 403） | PB-12 | qa-domain | `QuestionTest#onlyAuthorOrManagerCanEdit`、`AnswerTest#strangersCannotEditOrDelete` | 3h | 2 |
| QA-T05 | `QuestionCommandService.edit`（标签差集 `adjustUsage(±1)`、`QuestionEditedEvent`、缓存失效）+ `AnswerCommandService.edit`；自建测试 | PB-12 | qa-application | 自建单测 + 集成 | 4h | 2 |
| QA-T06 | `Question.softDelete`（作者仅限无回答；管理者任意）+ `Answer.softDelete`（已采纳保护 422 QA-1012） | PB-13 | qa-domain | `QuestionTest#askerCannotDeleteAnsweredQuestionButManagerCan`、`AnswerTest#acceptedAnswerCannotBeDeletedByItsAuthor` | 3h | 2 |
| QA-T07 | `QuestionCommandService.delete` + `AnswerCommandService.delete`（`adjustAnswerCount(-1)`、`ContentDeletedEvent`、审计 `QUESTION_DELETED/ANSWER_DELETED`、缓存失效）；自建测试 | PB-13 | qa-application | 自建单测 + 集成 | 4h | 2 |
| QA-T08 | `Question.accept`（`ensureNotDeleted`→权限→未采纳→答案归属→答案未删除） | PB-10 | qa-domain | `QuestionTest.Accepting` 组 5 用例全绿 | 3h | 2 |
| QA-T09 | `AnswerCommandService.accept`（`canManageCourse` 教师代采纳、发 `AnswerAcceptedEvent`、审计 `ANSWER_ACCEPTED`、缓存失效）；自建测试 | PB-10 | qa-application | `AnswerAndVoteServiceTest#onlyAskerOrCourseManagerCanAccept` + 自建 | 4h | 2 |
| QA-T10 | `Answer.submit` + `ensureOpenForAnswers`；`AnswerCommandService.submit`（`adjustAnswerCount(+1)`、`AnswerSubmittedEvent`、缓存失效） | PB-09 | qa-domain + qa-application | `AnswerAndVoteServiceTest#submitAnswerPublishesEventAndBumpsCounter` + 自建 | 4h | 2 |
| QA-T11 | `Vote.cast`（`checkValue` ±1、不能投自己、取消=0）+ `Vote.change` | PB-11 | qa-domain | `VoteAndCommentTest#cannotVoteOnOwnContent`、`voteValueMustBePlusOrMinusOne` | 2h | 1 |
| QA-T12 | `VoteService.cast`（旧值==新值幂等不发事件、delta、`adjustScore`、`VoteCastEvent`）+ `resolve`（404 QA-4001/4002）；自建测试含"重复命令不发第二次事件" | PB-11 | qa-application | `AnswerAndVoteServiceTest#changingVoteProducesCorrectDelta`、`cannotVoteOnOwnContent` + 自建 | 5h | 3 |
| QA-T13 | `QuestionQueryService.list`：缓存→`readModel.list` 分页→批量作者→摘要 160 字→回填缓存；自建测试（分页/课程筛选/缓存命中） | PB-07 | qa-application | 自建单测（读模型已实现，排序 `created_at DESC, id DESC`） | 5h | 3 |
| QA-T14 | `QuestionQueryService.detail`：404→`viewCounter`→回答+评论→批量作者→`findMyVotes`→`PermissionsView`→排序；自建测试（匿名/登录/我的投票/排序） | PB-08 | qa-application | 自建单测 + 集成 | 7h | 5 |
| QA-T15 | 补充领域边界测试：标题 150/151、正文下限、1/6 标签、重复采纳、已删除内容编辑、二次认证、评论长度 2/600 | PB-06~13 | qa-domain test | 新增用例全绿（评审 A5/4.x 补充场景落地） | 4h | 2 |

> 与指导书 6.3.1 参考任务（T1-01~T1-10，53h）对齐：本表为 4 人团队适配版，将 T1-07 拆细（post 6h），查询侧按 list/detail 分开并增补自建测试工时（PB-07/08 合计 12h），总计 58h（多 5h 属自建测试缓冲）。

### 2.1 与附录 A（107 个 TODO 映射）的对应关系

| 附录 A 类 / 方法（Sprint 1） | 方法数 | 覆盖任务 | PBI |
|---|---|---|---|
| Question：post / edit / accept / softDelete / ensureOpenForAnswers / ensureNotDeleted | 6 | QA-T02、T04、T06、T08 | PB-06/10/12/13 |
| MarkdownBody：ofQuestion / ofAnswer | 2 | QA-T01 | PB-06/09 |
| Tags：names | 1 | QA-T01 | PB-06 |
| Answer：submit / edit / softDelete / ensureNotDeleted | 4 | QA-T04、T06、T10 | PB-09/12/13 |
| Vote：cast / change / checkValue | 3 | QA-T11 | PB-11 |
| QuestionCommandService：post / edit / delete / load | 4 | QA-T03、T05、T07 | PB-06/12/13 |
| AnswerCommandService：submit / edit / delete / accept / loadQuestion / loadAnswer | 6 | QA-T05、T07、T09、T10 | PB-09/10/12/13 |
| VoteService：cast / resolve | 2 | QA-T12 | PB-11 |
| QuestionQueryService：list / detail | 2 | QA-T13、T14 | PB-07/08 |
| **PB-06~13 范围合计** | **30** | **15 项任务（QA-T01~T15）** | — |
| （不在本表：endorse×2、popularTags、评论×2、findQuestion） | 6 | 其他成员范围 | PB-14~17 |

## 3. 认领方案（建议）

| 成员 | 角色 | 认领任务 | 覆盖 PBI | 工时 | 故事点 |
|---|---|---|---|---|---|
| **Can0401** | Dev（主责 co-qa） | QA-T01~T09（不含 T10~T12） | PB-06、PB-12、PB-13、PB-10 | 31h | 16 |
| zhaojeak | PO + QA | QA-T13、QA-T14 | PB-07、PB-08 | 12h | 8 |
| Sherpherd | SM | QA-T10 | PB-09 | 4h | 3 |
| Arthur20060209 | Dev（主责 co-discovery/frontend） | QA-T11、QA-T12 | PB-11 | 7h | 5 |
| 全员 | — | QA-T15（Can 牵头，每人 1h） | PB-06~13 | 4h | —（任务级 2，不计入 PBI 故事点） |

认领理由：
1. **Can 主责 co-qa**，认领"问题聚合完整闭环"（发布→采纳→编辑→删除 4 个 PBI），QA-T01~T09 方法间共享 `Question` 聚合与错误码，一个 Owner 少交棒、少冲突；S2 声誉上下文也由 co-qa 出事件，Can 后续顺理成章跟进。
2. **查询侧（PB-07/08）交给 PO/QA**：读模型与验收标准语义最接近，PO 写验收测试最顺手；且查询侧几乎不与命令侧改同一文件（`QuestionQueryService` 独享），分支冲突风险最低。
3. **PB-11 投票给 Arthur**：独立聚合，与前端（PB-19）联动小，7h 与他 PB-14~19 的排期不冲突；PB-09 回答与 PB-10 采纳共享 `Answer` 聚合，故回答留给 SM（4h 最小任务，SM 会议多）。
4. 若 SM 因站会/评审无暇，**备选**：QA-T10 交给 Can（35h，仍可行），SM 只挂名复核。

## 4. 容量核算与风险

- 容量：4 人 × 每周 8–10 理想小时 × 2 周 = 64–80h；按 80% 承诺 = **51–64h**。
- PB-06~13 承诺：**58h**（第 2 节合计），处于容量区间内。
- 全 Sprint 风险：Arthur 的 PB-14~19 另需约 20–25h，加本表 7h ≈ **27–32h/人**，S1 全员合计约 78h，**超过 4 人容量**。建议：
  1. Sprint 1 **只承诺 Must（43 点中的核心）+ 必要 Should**，PB-16（1 点）/PB-19（3 点）弹性后置；
  2. PB-18 行走骨架**第 1 天先做**（参考 T1-12，E 承担），否则发件箱被 TODO 监听器堵死；
  3. 重点跟进 3 个风险点：错误码写回、正文下限拍板、访客权限放行（评审文档 ① 的 A2/A3/A5）。

## 5. 落地建议（GitHub 执行细则）

1. **分支**：从 `origin/develop` 拉 `feature/<issue号>-<短描述>`，如 `feature/6-question-post`、`feature/13-soft-delete`；一次 PR 一个 Issue（SM 维护看板）。
2. **任务 Issue**：按 `.github/ISSUE_TEMPLATE/task.md` 建 15 条（或 8 条 PBI Issue 下挂子任务清单）；标题格式 `QA-T03 QuestionCommandService.post`，描述含验收测试与估点。
3. **提交规范**：`feat(qa): implement QuestionCommandService.post (#6)`；PR 写 `Closes #6`；`CODEOWNERS` 已配置 co-qa = `@Can0401 @zhaojeak`，两人互相 review。
4. **顺序建议**：PB-18 行走骨架（第 1 天先行）→ QA-T01/T02/T03（PB-06 打通发件箱）→ T08/T09（采纳）→ T04~T07（编辑/删除）→ T13/T14（查询）→ T10~T12 → T15 边界测试收尾。
5. **DoD 核对**（PR 合并前自查，对照附录 B.2 PR 模板自检清单）：本地 `mvn -f backend/pom.xml test -pl co-qa/co-qa-domain,co-qa/co-qa-application -am` 全绿 → `ArchitectureTest` 通过 → 业务规则位于领域层、领域层无框架依赖 → 新增规则有单元测试、未修改任何既有测试断言 → 异常类型与错误码符合 docs/错误码.md → 写操作有事务、事件在保存后发布、消费端幂等 → 无调试代码/密钥/无关格式化改动 → 6.3.5 自检命令（前端 typecheck+build）通过 → PR 有 review 通过。

---

## 6. 实现进度（Sprint 1 执行期回填 · 2026-10-26）

> 分支 `feature/can-lab2-qa`（基于 origin/main 基线），提交 `3cbf407`（13 文件，+426/-72）；已开 **PR #64**（https://github.com/zhaojeak/campus-overflow-3/pull/64 ，base=main，2026-10-26）。**红线遵守：未改任何既有测试断言、未动 reconstitute。**

| 任务 | 内容 | 状态 | 验证结果 |
|---|---|---|---|
| QA-T01 | MarkdownBody.ofQuestion/ofAnswer、Tags.names | ✅ 已实现 | 见下 |
| QA-T02 | Question.post + ensureOpenForAnswers/ensureNotDeleted | ✅ 已实现 | 见下 |
| QA-T03 | QuestionCommandService.post（课程校验 ID-4002、adjustUsage、事件、缓存、审计） | ✅ 已实现 | 见下 |
| QA-T04 | Question.edit + Answer.edit（QA-3002） | ✅ 已实现 | 见下 |
| QA-T05 | QuestionCommandService.edit（标签差集）+ AnswerCommandService.edit | ✅ 已实现 | 见下 |
| QA-T06 | Question.softDelete（QA-1009）+ Answer.softDelete（QA-1012/QA-3003） | ✅ 已实现 | 见下 |
| QA-T07 | QuestionCommandService.delete + AnswerCommandService.delete（ContentDeletedEvent、adjustAnswerCount） | ✅ 已实现 | 见下 |
| QA-T08 | Question.accept（QA-3001/QA-2002/归属校验） | ✅ 已实现 | 见下 |
| QA-T09 | AnswerCommandService.accept（教师代采纳、AnswerAcceptedEvent） | ✅ 已实现 | 见下 |
| QA-T10 | Answer.submit + AnswerCommandService.submit（AnswerSubmittedEvent、adjustAnswerCount） | ✅ 已实现 | 见下 |
| QA-T11 | Vote.cast/change/checkValue（QA-1015/QA-1016） | ✅ 已实现 | 见下 |
| QA-T12 | VoteService.cast（幂等）+ resolve（QA-4001/4002） | ✅ 已实现 | 见下 |
| QA-T13 | QuestionQueryService.list（缓存/分页/批量作者/摘要 160 字） | ✅ 已实现 | 见下 |
| QA-T14 | QuestionQueryService.detail（匿名/权限/我的投票/评论分组）+ QaQueryApiImpl.findQuestion | ✅ 已实现 | 见下 |
| QA-T15 | BoundaryTest 新增 7 个边界用例（正文/标题/标签/评论/投票守卫） | ✅ 已实现 | 见下 |

**验证结果（本机，JDK 21 + 内置 Maven，对应 6.3.5 自检命令）**：

1. `mvn -f backend/pom.xml test -pl co-qa/co-qa-domain,co-qa/co-qa-application -am` → **BUILD SUCCESS**：领域 **25** 用例（QuestionTest 12 + AnswerTest 3 + VoteAndCommentTest 3 + BoundaryTest 7）+ 应用 **4** 用例（AnswerAndVoteServiceTest），0 失败。
2. `mvn -f backend/pom.xml test -pl co-bootstrap -am -Dtest=ArchitectureTest` → **BUILD SUCCESS**（FF-1~FF-6 架构规则通过）。
3. 前端 typecheck+build 属 Arthur 的 PB-14~19 范围，本分支未触碰前端，未执行。

**实现期偏差（如实记录）**：

1. **Comment.post（QA-1007/QA-1008/QA-1003）**：评论属 PB-15（Arthur 范围），但属附录 A Sprint 1 领域方法，为使骨架可编译已一并实现；建议评审时与 Arthur 确认归属与 PR 拆分。
2. **错误码**：QA-1007（单层回复）、QA-1008（回复目标不一致）、QA-2004（重复认证）、QA-1016（投票值）为本次新增建议码，**待写回 docs/错误码.md**（对应评审 A2 待办）；其余全部取测试与基建坐实值。
3. **正文下限**：按评审 A3/D1 建议落地（问题 20、回答 10，QA-1002），等待团队正式拍板。
4. **投票幂等（评审 A5）**：同值重复提交返回 `scoreDelta=0` 且不发 `VoteCastEvent`，已实现并有 `changingVoteProducesCorrectDelta` 覆盖（旧 1→新 1 不重复计数）。
5. **认证/采纳权限**：`endorse` 只允许课程教师/助教（QA-3004）；采纳允许提问者或课程管理者（QA-3001）。
6. **CommentService 无审计端口**：骨架未注入 `AuditPort`，评论只发事件不写审计（其余写操作均有审计）。

## 7. 附录：GitHub Issue 草稿（15 条，可直接粘贴）

> 模板 `.github/ISSUE_TEMPLATE/task.md`；标题格式 `QA-Txx <方法>（PB-0x）`；每个 Issue 关联对应 PBI Issue（`Closes #<PBI>` 写在对应 PR）。

| # | 标题（建议） | 验收标准（测试） | SP/h |
|---|---|---|---|
| 1 | QA-T01 MarkdownBody.ofQuestion / Tags.names（PB-06） | QuestionTest#titleMustBeBetween10And150Chars / tagsAreNormalizedAndDeduplicated / requiresOneToFiveTags / rejectsIllegalTagCharacters + BoundaryTest | 1/2h |
| 2 | QA-T02 Question.post 工厂（PB-06） | QuestionTest.Posting 组全绿 | 1/2h |
| 3 | QA-T03 QuestionCommandService.post（PB-06） | 自建单测 + 发件箱出表（课程不存在 404 ID-4002） | 3/6h |
| 4 | QA-T04 Question.edit + Answer.edit（PB-12） | QuestionTest#onlyAuthorOrManagerCanEdit、AnswerTest#strangersCannotEditOrDelete | 2/3h |
| 5 | QA-T05 编辑命令服务（PB-12） | 自建单测（标签差集 ±1、编辑事件、缓存失效） | 2/4h |
| 6 | QA-T06 Question.softDelete + Answer.softDelete（PB-13） | QuestionTest#askerCannotDeleteAnsweredQuestionButManagerCan、AnswerTest#acceptedAnswerCannotBeDeletedByItsAuthor | 2/3h |
| 7 | QA-T07 删除命令服务（PB-13） | 自建单测（ContentDeletedEvent、adjustAnswerCount、审计） | 2/4h |
| 8 | QA-T08 Question.accept（PB-10） | QuestionTest.Accepting 5 用例全绿 | 2/3h |
| 9 | QA-T09 AnswerCommandService.accept（PB-10） | AnswerAndVoteServiceTest + 自建 | 2/4h |
| 10 | QA-T10 Answer.submit + 提交命令（PB-09） | AnswerAndVoteServiceTest#submitAnswerPublishesEventAndBumpsCounter + 自建 | 2/4h |
| 11 | QA-T11 Vote.cast/change/checkValue（PB-11） | VoteAndCommentTest 2 用例 + BoundaryTest | 1/2h |
| 12 | QA-T12 VoteService.cast/resolve（PB-11） | AnswerAndVoteServiceTest#changingVoteProducesCorrectDelta + 自建（幂等断言） | 3/5h |
| 13 | QA-T13 QuestionQueryService.list（PB-07） | 自建单测（分页/课程筛选/缓存命中） | 3/5h |
| 14 | QA-T14 QuestionQueryService.detail（PB-08） | 自建单测（匿名/登录/我的投票/评论分组） | 5/7h |
| 15 | QA-T15 领域边界测试（PB-06~13） | BoundaryTest 7 用例全绿 | 2/4h |

> 注：QA-T01~T09（Can 认领）与 T10~T15 的代码均已在提交 3cbf407 中实现并通过测试；Issue 草稿供团队按原认领方案归档与开 PR 时引用。
