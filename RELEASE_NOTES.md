# CampusOverflow v1.0.0 发布说明

**发布日期**：2026-11-19
**仓库**：`hbuas-se-2026fall/cs2411-team03-campus-overflow`
**对应里程碑**：Sprint 4（稳定化与发布）

---

## 一、这是什么

CampusOverflow 是一个校园技术问答社区：学生提问、回答、投票、采纳，教师认证优质回答，声望与徽章激励，全站中文全文检索，事件驱动的通知中心。

后端是 **Spring Boot 3 的模块化单体**，按四个限界上下文切分：

| 上下文 | 职责 | 模块前缀 |
| --- | --- | --- |
| Identity | 注册、登录、课程、角色、选课、教师认证 | `co-identity-*` |
| Q&A | 提问、回答、评论、投票、采纳、悬赏、标签 | `co-qa-*` |
| Reputation | 积分账本、徽章、排行榜、悬赏结算 | `co-reputation-*` |
| Discovery | 全文检索、相关问题、通知中心 | `co-discovery-*` |

跨上下文**只通过 `*-api` 发布语言通信**，不直接依赖对方实现；界限之间的状态变更靠**事务性发件箱**异步传递。

---

## 二、本版交付的功能

### 身份与课程
- 注册、登录、登出；服务端会话存 Redis，支持多端同时在线与强制失效
- 课程创建与成员管理；学生按课程提问，教师按课程认证回答
- 管理员角色：审核教师认证申请

### 问答核心
- 提问：标题 1–150 字、Markdown 正文、1–5 个标签；支持编辑与删除（软删除，ADR-006）
- 回答：Markdown 正文；支持编辑与删除；作者可自行管理
- 评论：问题与回答都能评论，支持**二级回复**与 `@昵称` 提醒（被提醒人收到通知）
- 投票：赞/踩，可撤销；同一用户对同一目标只有一票
- 采纳：提问者可采纳一条回答；采纳后触发声誉计分、悬赏结算、通知三类副作用
- 教师认证：教师对回答打认证标记，随回答展示认证人
- 悬赏：发起时为 10–500 积分、1–14 天有效期，页面有**秒级倒计时**；采纳后自动结算给回答者，超时置为 `EXPIRED`

### 声誉与激励
- 积分账本：每次变动落一条 `rep_ledger`，唯一键 `(event_id, user_id, reason)` 保证**幂等**
- 计分规则：回答被采纳 +15；投票按差值计分，**每日上限 200**
- 六种徽章：初次解答 `FIRST_ANSWER`、初露锋芒 `FIRST_ACCEPTED`（首次被采纳）、解惑达人 `HELPER_10`（≥10 次被采纳）、名师认可 `TEACHER_ENDORSED`、小有名气 `REPUTATION_100`（≥100 分）、德高望重 `REPUTATION_1000`（≥1000 分）
- 排行榜：按积分排序，个人主页展示积分、徽章与流水

### 检索与通知
- 中文全文检索：MySQL ngram（`ngram_token_size=2`），支持关键字、只看未解答、只看有悬赏等筛选；结果带回答数、是否已采纳、相似度
- 相关问题：详情页侧栏按相似度推荐 Top 5
- 通知中心：回答、采纳、认证、徽章、悬赏获奖、`@提醒` 六类通知；支持未读数与标记已读
- 搜索索引随事件异步投影（`dis_search_doc`），提问/回答/采纳后自动更新

### 安全
- 密码 BCrypt 加盐存储；会话 Cookie 支持 `Secure` 开关
- Markdown 渲染走 commonmark，输出经 **OWASP Java HTML Sanitizer** 二次净化，前端不做二次信任
- 字段级加密（`CO_FIELD_KEY`）保护敏感字段

---

## 三、部署方式

### 一键部署（推荐）

```bash
# 1) 准备环境变量（可选，不建也能用开发默认值）
cp deploy/.env.example deploy/.env
#    Windows: Copy-Item deploy\.env.example deploy\.env

# 2) 一键启动（首次会自动构建前后端镜像）
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

启动后：

| 地址 | 说明 |
| --- | --- |
| http://localhost | 前端页面 |
| http://localhost:8080/swagger-ui.html | 接口文档 |
| http://localhost:8080/actuator/health/readiness | 后端就绪探针 |
| http://localhost:3000 | Grafana（需加 `--profile observability`） |
| http://localhost:9090 | Prometheus（需加 `--profile observability`） |

带监控栈启动：

```bash
docker compose -f deploy/docker-compose.yml --profile observability up -d --build
```

### 演示账号

`SPRING_PROFILES=prod,demo` 时会自动写入演示数据，**密码统一为 `Campus2026`**：

| 账号 | 角色 | 说明 |
| --- | --- | --- |
| `admin01` | 管理员 | 后台管理 |
| `T2001` | 教师 | 王老师，课程 CS2001「数据结构」 |
| `TA3001` | 助教 | 李助教 |
| `2023001` | 学生 | 小明 |
| `2023002` | 学生 | 小红 |
| `2023003` | 学生 | 小刚 |

演示数据包含 3 个问题：红黑树、Spring `@Transactional` 同类调用失效、MySQL ngram 全文索引。第二个问题带悬赏且**已在演示数据里完成采纳结算**，因此登录后通知中心能同时看到回答、徽章、悬赏获奖三类通知。

> 真实部署请把 `SPRING_PROFILES` 改成 `prod`，不要加载演示数据。

### 本地开发（不用容器跑应用）

```bash
docker compose -f deploy/docker-compose.yml up -d mysql redis
cd backend && mvn spring-boot:run -pl co-bootstrap -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm run dev     # http://localhost:5173
```

---

## 四、质量数据

| 门禁 | 状态 |
| --- | --- |
| 单元测试（领域层 + 应用层，全量） | 通过 |
| 架构约束 ArchUnit（FF-1~FF-6） | 通过 |
| 领域层覆盖率（jacoco ≥ 0.70，四个上下文分别校验） | 通过 |
| 端到端集成测试（Testcontainers 起真实 MySQL 8.0.39 + Redis 7） | 通过 |
| 前端类型检查与构建 | 通过 |
| 容器镜像构建 | 通过 |
| 一键部署冒烟（compose 起全栈并探活） | 通过 |

本版的关键变化：**集成测试从"允许失败"变成硬门禁**。此前 `ci.yml` 对集成测试设了 `continue-on-error`，Docker API 版本不匹配导致的失败被掩盖了很久；Sprint 3 移除该开关并修正 `docker-java.properties`（`api.version=1.44`）后，流水线才真正能拦住问题。

端到端测试目前覆盖：`CampusOverflowFlowIT` 的 3 条主链路（提问→回答→采纳→声誉→搜索→通知），以及 `DemoDataSeederIT` 对演示数据、徽章、悬赏结算（`rep_bounty.status='AWARDED'`）与三类通知的断言。

---

## 五、已知问题

| 问题 | 影响 | 说明 |
| --- | --- | --- |
| 通知红点为 30 秒轮询，未改 SSE | 新通知最多延迟 30 秒显示 | PB-43，未纳入本版 |
| 管理员禁用账号未实现 | 违规账号只能手工处理 | PB-40，未纳入本版 |
| 个人资料页面只读 | 用户不能自助改昵称/头像 | PB-41，未纳入本版 |
| 管理页的教师认证审批仅接口可用 | 无图形界面 | PB-47，未纳入本版 |
| 未做压测与缓存命中统计 | 性能数据只有单机手工观察 | PB-45，未纳入本版 |
| Grafana 面板需手工导入 | 默认不带预置 dashboard | PB-44 只交付了 Prometheus 指标暴露 |
| 行走骨架的技术债未清 | `Reputation`/`Discovery` 早期空实现方法仍在 | #55，不影响功能，但代码里有死代码 |

上述均已在 Sprint 4 剩余的 Issue 中登记，可按容量追加。

---

## 六、回滚

```bash
# 回到上一版本代码
git checkout v0.9.0

# 重建容器（数据卷保留，不会丢数据）
docker compose -f deploy/docker-compose.yml up -d --build

# 如需彻底清理（含数据库数据，谨慎）
docker compose -f deploy/docker-compose.yml down -v
```

Flyway 迁移是单向的，`V*__.sql` 不回滚；若需回退到更早的数据库结构，请从备份恢复 `mysql-data` 卷。
