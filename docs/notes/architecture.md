# CampusOverflow 架构理解笔记（T0-05）

> 作者：zhaojeak（PO/QA）
> 日期：Sprint 0
> 用途：读完 Identity 参考实现与 docs/ddd/context-map.md 后的个人理解，作为团队对齐基线。

## 1. 总体形态

- **模块化单体（Modular Monolith）**：一个 Spring Boot 应用 `co-bootstrap`，按 DDD 限界上下文拆成 Maven 多模块，不引入微服务、不引消息总线。
- 每个上下文有独立的：`-api` / `-application` / `-domain` / `-infrastructure` / `-interfaces` 五层，包名严格隔离。
- 上下文之间**不直接跨模块引用对方的 domain**，只能通过 `co-qa-api` 这种对外暴露的查询接口（QaQueryApi）调用，对应 ADR-009 的"上下文映射"。

## 2. 限界上下文

| 上下文 | 模块 | 职责 |
|---|---|---|
| Identity | `co-identity` | 用户注册/登录、Session、学生/教师角色、课程成员 |
| QA | `co-qa` | 问题、回答、投票、采纳、评论、标签 |
| Reputation | `co-reputation` | 声誉账本、徽章、悬赏、排行榜 |
| Discovery | `co-discovery` | 搜索投影、相关问题推荐、通知 |
| Shared Kernel | `co-shared-kernel` | Actor、Money、错误码、公共基类 |

## 3. 关键技术决策（对应 ADR）

- **认证**：Spring Session + Redis + HttpOnly Cookie，**不用 JWT**（ADR-003）。登录后改 Session ID 防固定攻击。
- **数据一致性**：每上下文独立 Flyway 迁移目录（`db/migration/{context}`），表结构不共享；跨上下文读模型用投影，不用外键。
- **ORM**：JPA 但 `open-in-view=false`，事务边界只在 application 层；批量插入开 `batch_size=30`。
- **CI 门禁**：GitHub Actions 渐进式——Sprint 0 只跑 `mvn -DskipTests compile` + 架构约束检查；Sprint 1 起加 `co-qa` 单测覆盖率 ≥70%。

## 4. 请求生命周期

```
浏览器
  → Cookie(SESSION) + X-XSRF-TOKEN
  → Spring Security Filter（AuthenticationManager）
  → ActorAware（当前用户上下文）
  → Controller（interfaces 层）
  → ApplicationService（事务边界 + DTO）
  → Domain（聚合根 / 领域事件）
  → Infrastructure（JPA Repository）
```

写操作结束后发布领域事件（如 `QuestionPostedEvent`），由 Reputation/Discovery 的 `@EventListener` 异步更新投影——这就是"行走骨架"里那两个空监听器未来要填的位置。

## 5. 本地开发环境

- `docker compose up -d` 起 MySQL(3306) + Redis(6379)
- 启动 `co-bootstrap`，默认 8080
- 冒烟脚本：`http/auth.http`（csrf → register → login → me → courses → join）

## 6. 我作为 PO/QA 重点关注

- 每个 PBI 的验收标准能否在这个架构里落到对应上下文（不跨边界写业务）。
- 错误码统一在 `co-shared-kernel`，前端拿到的友好提示由 `co-qa` 前端层翻译。
- 跨上下文调用只走 `*-api` 模块的接口，禁止 import 对方 domain。
