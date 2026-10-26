# Gherkin 验收标准（需求建模工作坊产出 · Lab2）

> 维护者：PO（zhaojeak）｜ QA 复核：与测试类一一对应
> 约定：错误场景统一标注 <HTTP 状态> <错误码>（错误码约定见 docs/错误码.md 与指导书 3.7 节）；
> 每个 PBI 的验收标准即其 DoD 中的"可验证验收标准"，评审会按此逐条核查。

---

## S0 · 启动与基础设施

### PB-05 注册、登录并加入课程（Identity）

```gherkin
Feature: 注册登录与课程
  Scenario: 学生自助注册
    Given 我是一个新访客
    When 我提交用户名、密码（8-64位，含字母与数字）、昵称注册
    Then 返回 201，系统创建账号，角色为 STUDENT

  Scenario: 学生登录
    Given 我已注册账号
    When 我用正确凭据登录
    Then 返回会话 Cookie，GET /api/v1/auth/me 返回我的用户信息

  Scenario: 错误密码登录触发限流
    Given 我已注册账号
    When 我 1 分钟内连续失败 5 次登录
    Then 第 6 次返回 429 AUTH-429

  Scenario: 学生加入课程
    Given 教师已创建课程且我已登录
    When 我调用 POST /api/v1/courses/{id}/join
    Then 返回 200，我的课程列表出现该课程
```

---

## S1 · 问答核心

### PB-06 发布问题（Q&A）

```gherkin
Feature: 发布问题
  Scenario: 发布合法问题
    Given 我已登录
    When 我发布标题 ≥10 字、正文合法、带 2 个标签（如 #Java, java）的问题
    Then 返回 201，问题创建成功，标签被规范化为小写去重（[java]）

  Scenario: 标题过短
    Given 我已登录
    When 我发布标题为 9 个字符的问题
    Then 返回 422 QA-1001

  Scenario: 标签数量超限
    Given 我已登录
    When 我发布带 6 个标签的问题
    Then 返回 422 QA-1005

  Scenario: 关联不存在的课程
    Given 我已登录
    When 我发布关联到不存在课程的问题
    Then 返回 404 ID-4002 / QA-4001
```

### PB-07 / PB-08 问题列表与详情

```gherkin
Feature: 问题浏览
  Scenario: 分页浏览问题列表
    Given 系统存在若干问题
    When 我请求 GET /api/v1/questions?page=1&size=20&courseId=1
    Then 返回 {items,total,page,size} 结构，按创建时间倒序

  Scenario: 查看问题详情
    Given 问题存在且有回答与评论
    When 我请求 GET /api/v1/questions/{id}
    Then 返回渲染后的正文、回答列表、评论分组、我的投票与可用操作（permissions）
    And 回答排序为：已采纳优先、得分降序、时间升序

  Scenario: 问题不存在
    Given 问题不存在或已删除
    When 我请求 GET /api/v1/questions/{id}
    Then 返回 404 QA-4001
```

### PB-09 回答

```gherkin
Feature: 回答问题
  Scenario: 回答未删除的问题
    Given 问题已存在且未删除，我已登录
    When 我提交合法回答
    Then 返回 201，问题回答数 +1，详情页显示渲染后的回答

  Scenario: 回答已删除的问题
    Given 问题已被软删除
    When 我尝试回答
    Then 返回 422 QA-1020
```

### PB-10 采纳最佳答案

```gherkin
Feature: 采纳最佳答案
  Scenario: 提问者采纳
    Given 小明是问题提问者，小红提交了回答
    When 小明 PUT /api/v1/questions/{id}/accepted-answer
    Then 返回 204，问题标记为已采纳

  Scenario: 重复采纳
    Given 问题已有被采纳答案
    When 小明再次采纳另一回答
    Then 返回 409 QA-2002

  Scenario: 非提问者无权采纳
    Given 小红是普通学生，非提问者也非课程管理者
    When 小红尝试采纳
    Then 返回 403 QA-3001

  Scenario: 采纳不属于本题的回答
    Given 回答属于另一个问题
    When 提问者尝试采纳该回答
    Then 返回 422 QA-1010

  Scenario: 采纳已删除的回答
    Given 回答已被软删除
    When 提问者尝试采纳
    Then 返回 422 QA-1011
```

### PB-11 投票/改投/撤销

```gherkin
Feature: 投票
  Scenario: 点赞回答
    Given 小刚已登录，回答作者是他人
    When 小刚对回答投 +1
    Then 返回 200，回答得分 +1

  Scenario: 连续点赞两次无变化
    Given 小刚已对该回答投 +1
    When 小刚再次投 +1
    Then 返回 200，得分仍为 1（幂等）

  Scenario: 点赞后改为踩
    Given 小刚已对该回答投 +1
    When 小刚改投 -1
    Then 返回 200，得分变为 -1

  Scenario: 给自己的内容投票
    Given 小刚是问题作者
    When 小刚给自己的问题投 +1
    Then 返回 422 QA-1015（消息包含"自己"）

  Scenario: 非法投票值
    Given 小刚已登录
    When 小刚提交 value=2
    Then 返回 422 QA-1014
```

### PB-12 / PB-13 编辑与软删除

```gherkin
Feature: 编辑与软删除
  Scenario: 作者编辑自己的问题
    Given 我是问题作者
    When 我编辑标题/正文/标签
    Then 返回 200，问题更新，标签计数按新旧差集调整

  Scenario: 路人编辑他人内容
    Given 我不是作者也不是课程管理者
    When 我编辑他人问题
    Then 返回 403 QA-3002

  Scenario: 作者删除无回答的问题
    Given 我是问题作者且问题无回答
    When 我删除问题
    Then 返回 204，列表与详情不再出现

  Scenario: 作者删除已有回答的问题
    Given 我是问题作者且问题已有回答
    When 我删除问题
    Then 返回 422 QA-1013

  Scenario: 被采纳回答的作者删除
    Given 我是被采纳回答的作者
    When 我删除该回答
    Then 返回 422 QA-1012
```

### PB-14 教师认证（Should）

```gherkin
Feature: 教师认证
  Scenario: 课程教师认证优质回答
    Given 王老师是该课程教师
    When 王老师 POST /api/v1/answers/{id}/endorsement
    Then 返回 200，回答标记 endorsedBy=王老师

  Scenario: 重复认证
    Given 回答已被认证
    When 王老师再次认证
    Then 返回 409 QA-2003

  Scenario: 非管理者认证
    Given 我是普通学生
    When 我尝试认证回答
    Then 返回 403 QA-3004
```

### PB-15 评论与一层回复

```gherkin
Feature: 评论与回复
  Scenario: 评论问题
    Given 我已登录
    When 我提交 2-600 字符的评论
    Then 返回 201，评论出现在目标下

  Scenario: 回复评论
    Given 问题下已有顶层评论
    When 我回复该评论
    Then 返回 201，回复挂在父评论下

  Scenario: 回复二层
    Given 已有一条回复
    When 我再回复这条回复
    Then 返回 422 QA-1016

  Scenario: 回复挂到不同目标
    Given 父评论挂在问题 A 下
    When 我提交 targetType=ANSWER 且 parentId 指向该评论
    Then 返回 422 QA-1017
```

---

## S2 · 声誉与激励

### PB-20 开立声誉账户

```gherkin
Feature: 声誉账户
  Scenario: 学生注册开立账户
    Given 学生完成注册
    When 系统处理 UserRegisteredEvent
    Then 账户创建，reputation=0，availablePoints=0

  Scenario: 教师注册获得初始悬赏积分
    Given 教师账号被创建
    When 系统处理 UserRegisteredEvent
    Then 账户创建，availablePoints=1000

  Scenario: 重复事件幂等
    Given 账户已存在
    When UserRegisteredEvent 再次投递
    Then 不重复开户，账户状态不变
```

### PB-21 采纳计分

```gherkin
Feature: 采纳计分
  Scenario: 回答被采纳 +15
    Given 小红的回答被采纳
    When 系统处理 AnswerAcceptedEvent
    Then 小红声誉 +15，账本出现"回答被采纳 +15"，徽章"初露锋芒"授予

  Scenario: 自问自答不加分
    Given 小明采纳了自己的回答
    When 系统处理 AnswerAcceptedEvent
    Then 小明声誉不变（0 分）

  Scenario: 同一事件重放不重复加分
    Given 事件已被处理
    When AnswerAcceptedEvent 再次投递
    Then 声誉仍为 15，无重复流水
```

### PB-22 投票计分与每日上限

```gherkin
Feature: 投票计分
  Scenario: 被赞 +10
    Given 作者今日因投票获得 0 正向声誉
    When 收到 VoteCastEvent(+1)
    Then 作者声誉 +10

  Scenario: 被踩 −2
    Given 收到 VoteCastEvent(-1)
    Then 作者声誉 −2

  Scenario: 赞改踩按差值 −12
    Given 作者已因 +1 获得 +10
    When 收到 VoteCastEvent(1→-1)
    Then 作者声誉 −12（effect -2 - 10）

  Scenario: 每日正向上限 200
    Given 作者今日已获得 195 正向声誉
    When 收到 VoteCastEvent(+1)
    Then 只计入 5，声誉 +5（不超 200）
```

### PB-24 / PB-25 悬赏

```gherkin
Feature: 悬赏
  Scenario: 教师发起悬赏
    Given 王老师是课程管理者，可用积分 ≥50
    When 王老师发起 50 分 / 7 天悬赏
    Then 返回 201，可用积分 -50、冻结 +50，悬赏状态 OPEN

  Scenario: 积分不足
    Given 可用积分 30
    When 发起 50 分悬赏
    Then 返回 422 REP-1002

  Scenario: 悬赏分值越界
    Given 发起 9 分或 501 分悬赏
    Then 返回 422 REP-1003

  Scenario: 期限越界
    Given 发起 15 天悬赏
    Then 返回 422 REP-1004

  Scenario: 已有进行中悬赏
    Given 问题已有 OPEN 悬赏
    When 再次发起
    Then 返回 409 REP-2001

  Scenario: 采纳后悬赏结算
    Given 问题有 50 分悬赏，小红回答被采纳
    When 系统处理结算
    Then 小红声誉 +50（BOUNTY_AWARDED），发起人冻结归零

  Scenario: 自答赢得自己的悬赏
    Given 发起人采纳了自己的回答
    Then 不结算给本人，改为到期退款（closeAndRefund）

  Scenario: 到期退款
    Given 悬赏到期未采纳
    When expireDue 执行
    Then 悬赏关闭 EXPIRED，积分退回发起人可用余额，发布 BountyClosedEvent(refundedPoints)
```

### PB-26 / PB-27 排行榜与声誉档案

```gherkin
Feature: 排行榜与声誉档案
  Scenario: 查看排行榜
    Given 系统存在声誉数据
    When 我请求 GET /api/v1/leaderboard?period=WEEK
    Then 返回按周期聚合的名次列表，周/月/学期/总可切换

  Scenario: 查看他人声誉档案
    Given 我已登录
    When 我请求 GET /api/v1/users/{id}/reputation
    Then 返回声誉分与徽章，他人视角不暴露 availablePoints/frozenPoints

  Scenario: 查看他人流水被拒
    Given 我是普通用户
    When 我请求他人 GET /api/v1/users/{id}/reputation/ledger
    Then 返回 403 REP-3002
```

### PB-28 对账

```gherkin
Feature: 对账
  Scenario: 账户与流水一致
    Given 所有账户余额等于其流水之和
    When 管理员 GET /api/v1/admin/reputation/reconciliation
    Then 返回空列表

  Scenario: 发现不一致
    Given 某账户余额与流水之和不一致
    When 管理员请求对账
    Then 返回不一致账户列表，触发指标 co_reputation_mismatches
```

---

## S3 · 内容发现

### PB-31 搜索

```gherkin
Feature: 搜索
  Scenario: 中文关键词命中
    Given 系统存在含"红黑树"标签/正文的问题
    When 我 GET /api/v1/search/questions?q=红黑树
    Then 返回命中结果，词项 ≥2 字符（DIS-1001 已满足）

  Scenario: 单字关键词被拒
    Given 关键词为单字
    When 我搜索"树"
    Then 返回 422 DIS-1001

  Scenario: 筛选与排序
    Given 系统存在多个问题
    When 我按标签/未回答/悬赏筛选，并按相关度/最新/活跃/热度排序
    Then 返回符合筛选与排序的结果
```

### PB-32 搜索投影幂等

```gherkin
Feature: 搜索投影
  Scenario: 发布问题投影插入
    Given 问题已发布
    When 系统处理 QuestionPostedEvent
    Then dis_search_doc 插入对应投影，可被搜索命中

  Scenario: 事件重放幂等
    Given 事件已处理
    When QuestionPostedEvent 再次投递
    Then 投影不重复插入（ProcessedEventStore 去重）

  Scenario: 采纳标记
    Given 回答被采纳
    When 系统处理 AnswerAcceptedEvent
    Then 投影标记为"已采纳"
```

### PB-33 相关问题推荐（Should）

```gherkin
Feature: 相关问题
  Scenario: 标签相似推荐
    Given 问题 A 与 B 共享 2 个标签
    When 我请求 GET /api/v1/questions/{id}/related
    Then 返回按 Jaccard 相似度降序的相关问题及相似度

  Scenario: 无标签返回空
    Given 问题无标签
    When 我请求相关问题
    Then 返回空列表
```

### PB-34 / PB-36 通知

```gherkin
Feature: 通知
  Scenario: 回答触发通知
    Given 小红回答了小明的问题
    When 系统处理 AnswerSubmittedEvent
    Then 小明收到"你的问题有新回答"通知；自己回答自己不通知

  Scenario: 采纳触发通知
    Given 小红的回答被小明采纳
    When 系统处理 AnswerAcceptedEvent
    Then 小红收到"你的回答被采纳"通知

  Scenario: 通知列表与未读数
    Given 我有多条通知
    When 我 GET /api/v1/notifications 与 /unread-count
    Then 返回分页列表与未读数

  Scenario: 标记已读
    Given 我有未读通知
    When 我 POST /api/v1/notifications/{id}/read
    Then 通知变为已读；重复标记幂等

  Scenario: 标记他人通知
    Given 通知属于他人
    When 我尝试标记已读
    Then 返回 403 DIS-3001

  Scenario: 通知不存在
    Given 通知 ID 不存在
    When 我尝试标记已读
    Then 返回 404 DIS-4001
```

### PB-35 @提及（Should）

```gherkin
Feature: 提及
  Scenario: 评论中 @ 用户
    Given 评论正文含 "@小明 @小红"
    When 系统处理 CommentPostedEvent
    Then 小明、小红各收到一条提及提醒（去重保序）

  Scenario: 超过 5 人截断
    Given 评论 @ 了 7 个不同昵称
    When 系统解析
    Then 只提醒前 5 人

  Scenario: 昵称不足 2 字符不匹配
    Given 评论含 "a@b"
    When 系统解析
    Then 不产生提及
```

### PB-37 / PB-39 质量门禁

```gherkin
Feature: 质量门禁
  Scenario: 端到端链路全绿
    Given Docker 可用
    When 本地/CI 运行 mvn verify
    Then 提问→回答→采纳→声誉→搜索→通知整条链路测试通过

  Scenario: 领域覆盖率达标
    Given S3 后根 POM domain.coverage.minimum=0.70
    When 运行覆盖率门禁
    Then 三个领域模块行覆盖率 ≥ 70%

  Scenario: 投影幂等测试
    Given Discovery 应用层测试
    When 同一事件投递两次
    Then 只生效一次
```

---

## S4 · 发布

### PB-40 禁用账号（Should）

```gherkin
Feature: 禁用账号
  Scenario: 管理员禁用违规账号
    Given 我是管理员
    When 我 POST /api/v1/admin/users/{id}/disable
    Then 返回 200，该用户所有 Redis 会话被删除，立即下线

  Scenario: 非管理员禁用
    Given 我是普通用户
    When 我尝试禁用
    Then 返回 403
```

### PB-46 一键部署

```gherkin
Feature: 发布
  Scenario: docker compose 一键部署
    Given 环境已安装 Docker
    When 执行 docker compose -f deploy/docker-compose.yml up -d --build
    Then 后端、前端、MySQL、Redis 全部启动，可访问登录页

  Scenario: Release 发布
    Given main 分支已打 v1.0.0 标签
    When 创建 GitHub Release
    Then Release Notes 含新功能、已知问题、部署步骤
```

---

## 复核清单（QA 使用）

- [ ] 每个 Must PBI 有 Gherkin 场景，含错误场景与错误码
- [ ] 每条验收标准可对应到已有测试类或"自建"测试计划（见指导书附录 A）
- [ ] 幂等场景（重复投递）在 S2/S3 全覆盖：声誉、徽章、悬赏、排行榜、搜索投影、通知
- [ ] 边界值覆盖：标题 10/150、标签 1-5、悬赏 10-500 分、期限 1-14 天、每日上限 200
- [ ] 权限场景覆盖：403 错误码按角色（学生/教师/管理员/课程管理者）区分
