# 上下文映射（Context Map）

> 事件风暴工作坊产出 · Sprint 0 初版，S3 定稿
> 图例：箭头指向下游；橙色为异步集成事件（事务性发件箱，至少一次投递）
> 图形版：[context-map.png](context-map.png)（提交物用图，内容与本表一致）

| 上游 → 下游 | 关系模式 | 集成方式 | 说明 |
| --- | --- | --- | --- |
| Identity → Q&A / Reputation / Discovery | 开放主机服务 + 发布语言（OHS/PL） | 同步查询 IdentityApi；异步 UserRegisteredEvent | 下游只拿到 UserSummary（不含学号、邮箱等个人信息） |
| Q&A → Reputation | 客户/供应商（Customer/Supplier） | 异步：Answer*/VoteCast 事件；同步：QaQueryApi.findQuestion | Reputation 需要的事件字段（如 questionAuthorId）由 Q&A 在事件中提供 |
| Q&A → Discovery | 遵奉者（Conformist） | 异步：Question*/Answer*/Vote/Comment/ContentDeleted 事件 | Discovery 把事件折叠为自己的 SearchDocument 投影 |
| Reputation → Discovery | 遵奉者（Conformist） | 异步：Bounty*/BadgeAwarded 事件 | 用于"悬赏中"筛选与悬赏/徽章通知 |
| 全部上下文 | 共享内核（Shared Kernel） | Maven 依赖 co-shared-kernel | 修改共享内核须全组评审（影响所有上下文） |

## 要点

- 跨上下文**只允许**依赖对方 `*-api` 模块或订阅其事件（FF-2），禁止 import 其他上下文的 domain/application 类。
- 所有异步集成走 `co-shared-kernel` 的 `IntegrationEvent` + 事务性发件箱（ADR-002），消费者必须幂等。
- Identity 是唯一"完整参考实现"，其余三个上下文通过阅读 Identity 学习边界写法。
