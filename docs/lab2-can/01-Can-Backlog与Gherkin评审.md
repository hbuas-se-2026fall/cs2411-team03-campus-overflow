# Lab2・Can0401 交付物 ①：Product Backlog 与 Gherkin 评审（可实现性视角）

> 评审人：Can0401（Developer A，主责 co-identity /co-qa）
> 评审对象：
>
> `docs/backlog/product-backlog.md`
>
> 、
>
> `docs/backlog/acceptance-criteria.md`
>
> 、
>
> `docs/backlog/story-map.md`
>
> （PO 产出，feature/lab2-backlog 分支）
> 对照依据：实验指导书 V2 第 3.7、4.4、5、6.3 节；起始代码 
>
> `co-qa`
>
>  骨架与测试；
>
> `docs/错误码.md`
>
>  约定
> 日期：2026-10-26 ｜ 状态：评审记录定稿（第 3 节决策项待团队确认后生效）



***

## 1. 总体结论

Backlog 整体质量高：排序符合 "依赖优先 + 价值优先 + 风险前置"（Q\&A → Reputation → Discovery），故事点与指导书一致，S2/S3 幂等场景覆盖到位，PB-10 采纳的 5 个场景与 `QuestionTest.Accepting` 的 5 个用例一一对应。

从**可实现性**角度挑出 **5 个必须解决**的问题（第 2 节）和 **4 个待团队拍板**的决策（第 3 节）；补充 **17 个遗漏场景**的 Gherkin 草稿（第 4 节，可直接粘贴进 acceptance-criteria.md）。



***

## 2. 必须解决的问题（会阻碍实现或评审验收）



| #  | 位置                                     | 问题                                                                                                                                          | 影响                                                                                                                      | 建议                                                                                                  |
| -- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| A1 | acceptance-criteria.md                 | **PB-16（热门标签）、PB-17（QaQueryApi）、PB-18（行走骨架）、PB-19（前端闭环）没有任何 Gherkin 场景**                                                                    | 违反 DoR（4.4.1："有可验证的验收标准 Given/When/Then"）；PB-18 若漏做，发件箱会被监听器 TODO 异常全部堵死（指导书 2.5.1 注意事项），S1 验收的 "Q\&A 领域 + 应用测试全绿" 直接失败 | Sprint 0 收尾前补齐技术类验收（见 4.5 节草稿）；PB-18 至少验收一条：`mvn test` 后 outbox 无 `UnsupportedOperationException`           |
| A2 | acceptance-criteria.md + docs / 错误码.md | Gherkin 大量引用 \*\*"建议" 状态的错误码 \*\*（QA-1003/1010/1011/1012/1013/1016/1017、QA-2003、QA-3002/3003/3004、QA-4001/4002/4003），但这些码尚未写回 `docs/错误码.md` | 实现时无权威依据；不同人可能用不同码，PR 评审无统一口径（3.7 节明确要求 "由全组确认后写回"）                                                                     | Sprint 0 收尾时逐条确认并写回 docs/错误码.md，再开工 S1                                                            |
| A3 | 指导书 6.3.2 + PB-06/09 场景                | **MarkdownBody 正文长度下限未定**：问题建议 20–30000、回答 10–30000，且 "下限需兼容测试样例文本长度，由团队统一"；Gherkin 只测了标题 9 字，没测正文边界                                        | 实现与测试样例冲突时无法判定对错；QA-1002 语义模糊                                                                                           | 团队拍板下限值（建议问题 20、回答 10，若测试样例更短则按测试调整），写回错误码文档并在 Gherkin 补边界场景                                        |
| A4 | PB-06 第 4 个场景                          | **"404 ID-4002 / QA-4001" 一个场景标两个错误码**，验收无法判定                                                                                               | 双码歧义；QA-4001 是 "问题不存在"，与 "课程不存在" 语义冲突                                                                                   | 改为单一断言 **404 ID-4002**（依据：指导书 6.3.3"若指定 courseId，用 identity.findCourse 校验存在"；3.7 节 ID-4002 = 课程不存在） |
| A5 | PB-11 场景 + S1 幂等                       | 发件箱是**至少一次投递**（指导书 2.5.1），但 S1 Gherkin 只覆盖了 "同一值投票幂等"（VoteService old==value），**没有覆盖 "命令 / 事件重放不产生副作用" 的应用层断言**                             | S2/S3 的幂等是验收重点，S1 现在就应建立纪律；否则重放问题拖到 S2 才发现                                                                              | 补充应用层测试："同一投票命令提交两次，第二次不发 VoteCastEvent、得分不变"（见 4.4 草稿）                                             |



***

## 3. 待团队拍板的决策项（建议默认值）



| 决策                        | 背景                                                                           | 建议默认值                                                                                             | 需要谁确认                      |
| ------------------------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | -------------------------- |
| 回答正文长度下限                  | 指导书 6.3.2 未定                                                                 | 10 字符（若与测试样例冲突，以测试为准）                                                                             | 全员 + PO                    |
| 未登录（访客）能否访问列表 / 详情 / 热门标签 | 指导书 5.1 访客可浏览；`QuestionQueryService.detail(questionId, actorOrNull)` 参数已支持匿名 | 允许匿名 GET `/questions`、`/questions/{id}`、`/tags/popular`（SecurityConfig 放行），permissions/myVotes 为空 | PO + SM（确认 SecurityConfig） |
| 分页 size 边界                | Gherkin 未测 size=0 或超大值                                                       | size ∈ \[1,100]，越界 422 或按默认值处理（需查 PageRequest 实现）                                                 | PO                         |
| 课程教师角色能否被降级 / 移除          | `CourseMember.changeRole` 已保护 "课程教师不可降级"（ID-1013），但 Gherkin 无课程管理场景          | 维持现状，不新增场景                                                                                        | 无需                         |



***

## 4. 补充的 Gherkin 场景草稿（按 PBI 归位，可直接粘贴）

### 4.1 PB-07 / PB-08 问题浏览



```
Scenario: 访客查看列表与详情（未登录）
  Given 我未登录
  When 我 GET /api/v1/questions 与 /api/v1/questions/{id}
  Then 返回 200，permissions 为空、myVotes 为空（阅读不受限）

Scenario: 详情返回我的投票
  Given 我已对该回答投 +1
  When 我查看问题详情
  Then 回答的 myVote 为 +1

Scenario: Markdown 渲染与净化
  Given 问题正文含 <script>alert(1)</script> 与外部链接
  When 我查看详情
  Then 正文渲染为净化后的安全 HTML，无脚本可执行（OWASP Sanitizer）

Scenario: 列表同时间戳排序稳定
  Given 两个问题创建时间相同
  When 我请求列表
  Then 按 id 倒序排列（ORDER BY created_at DESC, id DESC）
```

### 4.2 PB-09 回答



```
Scenario: 回答正文长度越界
  Given 我已登录，问题未删除
  When 我提交超过 30000 字符或低于团队下限的回答
  Then 返回 422 QA-1002
```

### 4.3 PB-10 采纳



```
Scenario: 课程教师/助教代为采纳
  Given 王老师是该课程教师（课程管理者），问题已有回答
  When 王老师 PUT /api/v1/questions/{id}/accepted-answer
  Then 返回 204，问题标记为已采纳（对应 QuestionTest#courseTeacherCanAcceptOnBehalfOfAsker）

Scenario: 采纳已删除的问题
  Given 问题已被软删除
  When 提问者尝试采纳
  Then 返回 422 QA-1020（采纳前先 ensureNotDeleted）
```

### 4.4 PB-11 投票 / 改投 / 撤销



```
Scenario: 撤销投票
  Given 我已对该回答投 +1
  When 我提交 value=0
  Then 返回 200，得分回到原值，投票记录删除（delta = 0 - 1 = -1）

Scenario: 重复命令幂等
  Given 我已对该回答投 +1
  When 我再次提交 value=+1
  Then 得分仍为 1，且不产生新的 VoteCastEvent（应用层断言）

Scenario: 给已删除的内容投票
  Given 目标回答已被软删除
  When 我投票
  Then 返回 404 QA-4002
```

### 4.5 PB-12 编辑



```
Scenario: 编辑已删除的问题
  Given 问题已被软删除
  When 作者尝试编辑
  Then 返回 422 QA-1020

Scenario: 课程管理者编辑他人内容（正向）
  Given 我是课程教师，内容作者是学生
  When 我编辑该问题/回答
  Then 返回 200，内容更新（对应 QuestionTest#onlyAuthorOrManagerCanEdit 的正向分支）

Scenario: 编辑回答
  Given 我是回答作者
  When 我编辑回答正文
  Then 返回 200，updatedAt 更新（对应 AnswerTest#strangersCannotEditOrDelete 的正向分支）
```

### 4.6 PB-13 软删除



```
Scenario: 路人删除被拒
  Given 我不是作者也不是课程管理者
  When 我删除他人问题/回答
  Then 返回 403 QA-3003

Scenario: 课程管理者删除已有回答的问题
  Given 我是课程教师，问题已有回答
  When 我删除该问题
  Then 返回 204，列表与详情不再出现（对应 QuestionTest#askerCannotDeleteAnsweredQuestionButManagerCan 的管理者分支）

Scenario: 删除后回答数联动
  Given 问题下有 2 个回答
  When 课程管理者删除其中 1 个回答
  Then 问题回答数变为 1，列表计数同步
```

### 4.7 PB-15 评论与回复



```
Scenario: 评论长度边界
  Given 我已登录
  When 我提交 1 字符或 601 字符的评论
  Then 返回 422 QA-1003

Scenario: 评论已删除的问题
  Given 问题已被软删除
  When 我评论该问题
  Then 返回 422 QA-1020

Scenario: 评论不存在的目标
  Given 目标问题/回答不存在
  When 我提交评论
  Then 返回 404 QA-4003
```

### 4.8 技术类 PBI 补验收（PB-16 / PB-17 / PB-18 / PB-19）



```
Feature: 热门标签（PB-16）
  Scenario: 热门标签返回
    Given 系统存在标签使用数据
    When 我 GET /api/v1/tags/popular?limit=10
    Then 返回按使用次数降序的标签列表，limit ∈ [1,50]

Feature: QaQueryApi（PB-17）
  Scenario: 跨上下文查询问题引用
    Given 问题存在
    When 其他上下文调用 QaQueryApi.findQuestion(1)
    Then 返回 QuestionRef(id, authorId, courseId, title, acceptedAnswerId, deleted)；不存在返回 empty

Feature: 行走骨架（PB-18）
  Scenario: 发件箱不被阻塞
    Given Reputation/Discovery 监听器已改为空实现
    When 我发布一条问题并等待 OutboxRelay 投递
    Then outbox_event 无 UnsupportedOperationException，事件标记为已投递

Feature: 前端问答闭环（PB-19）
  Scenario: 前端编辑/删除/评论
    Given 我登录并打开问题详情页
    When 我编辑/删除自己的问题与回答、发表评论与回复
    Then 操作成功且按错误码显示友好提示（如 403 提示"无权操作"）
```



***

## 5. 一致性问题（轻微，建议顺手修正）



| #  | 位置          | 问题                        | 修正                                                                                                  |
| -- | ----------- | ------------------------- | --------------------------------------------------------------------------------------------------- |
| C1 | PB-07 场景    | "按创建时间倒序" 未提次要键           | 补 "同创建时间按 id 倒序"（与 `JdbcQaReadModel` 的 `ORDER BY created_at DESC, id DESC` 一致，已核验代码）                |
| C2 | PB-12/PB-13 | 合并为一个 Feature，但对应两个 Issue | 演示 / 验收按 Issue 分开核对                                                                              |
| C3 | PB-14 场景    | 角色写 "课程教师"，其余场景用 "课程管理者"  | 统一为 "课程教师 / 助教（课程管理者）"                                                                              |
| C4 | 全文          | 验收标准未逐条标注测试类              | 建议在每条 Gherkin 后标注 `<!-- 测试：QuestionTest#xxx -->`，DoD 核查 "新增规则有对应单元测试" 时可直接勾对（story.md 模板已有示例，正文应落实） |



***

## 6. 复核确认通过项（QA 视角）



* ✅ PB-06\~PB-15 的 Must PBI 均有场景且含错误码（PB-16\~19 除外，见 A1）

* ✅ 边界值覆盖较好：标题 9 字、6 个标签、悬赏 9/501 分、期限 15 天、每日上限 195→5

* ✅ PB-10 采纳 5 场景 ↔ `QuestionTest.Accepting` 5 用例一一对应；PB-11 ↔ `VoteAndCommentTest`/`AnswerAndVoteServiceTest` 对应

* ✅ S2/S3 幂等场景全覆盖（声誉 / 徽章 / 悬赏 / 排行榜 / 投影 / 通知）

* ✅ 权限 403 按角色区分（学生 / 教师 / 管理员 / 课程管理者）

* ✅ 故事点与指导书一致，无超 8 点需拆分的 PBI（PB-06\~13 合计 32 点）



