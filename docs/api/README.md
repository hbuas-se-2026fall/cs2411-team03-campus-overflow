# Postman Collection（Lab4 提交物）

`campus-overflow.postman_collection.json` 覆盖 CampusOverflow v1.0 对外全部 REST 接口，可直接导入 Postman / Newman 运行。

## 导入与运行

1. 启动后端：`mvn -pl co-bootstrap -am spring-boot:run`，或在 `deploy/` 下 `docker compose up -d`；
2. Postman → Import → 选择本目录的 `campus-overflow.postman_collection.json`；
3. 确认集合变量 `baseUrl` 指向实际地址（默认 `http://localhost:8080`）；
4. 按目录顺序执行：**00 准备 → 01 认证 → 02 问题 → …**。

也可用命令行跑：

```bash
newman run docs/api/campus-overflow.postman_collection.json --reporters cli
```

## 认证机制（执行前必读）

后端使用**服务端会话 Cookie（SESSION）+ CSRF 双提交 Cookie**，不是 Bearer Token：

- `00 准备 / 获取 CSRF Token` 会拿到 `XSRF-TOKEN` Cookie；
- 集合级 pre-request 脚本自动把该 Cookie 回填到请求头 `X-XSRF-TOKEN`；
- 登录成功后 Postman 的 Cookie Jar 会保存 `SESSION`，后续请求自动携带。

因此**不要跳过 00 和登录**，否则写请求会返回 `403 AUTH-403-CSRF`。

## 集合变量

| 变量 | 含义 | 由谁写入 |
|---|---|---|
| `baseUrl` | 服务地址 | 手动 |
| `password` | 测试账号密码（8–64 位，含字母与数字） | 手动 |
| `csrf` | CSRF token 备份 | 00 准备 |
| `regUsername` / `regDisplay` / `regEmail` | 时间戳生成的唯一注册身份 | 01 注册（pre-request） |
| `meId` | 当前登录用户 id | 01 登录 |
| `questionId` | 本次发布的问题 id | 02 发布问题 |
| `answerId` | 本次提交的回答 id | 03 提交回答 |

## 核心成功用例

**`02 问题 / 发布问题（Lab4 成功用例 · 201）`**：请求体为真实中文提问（标题 10–150 字、正文 ≥20 字、1–5 个标签），断言 `201 Created` 且响应体 `id` 为数字，并把 id 写入变量供后续请求复用。这是实验指导书 Lab4 要求的那条成功用例。

## 预期中的非 2xx

集合对以下三类情况放宽了断言，因为它们在**设计上**就会返回非 2xx，属正常校验而非缺陷：

| 场景 | 返回 | 原因 |
|---|---|---|
| 同一账号给自己的问题/回答投票 | `403` | 不变量 `QA-1015` 不能给自己内容投票；换第二个账号登录后重跑会 `200` |
| 学生账号发起悬赏 / 教师认证回答 | `403` | 需课程管理者（教师/助教/管理员）身份，见 `REP-3001`、`QA-3004` |
| 管理员接口用学生账号访问 | `403` | 需 `ADMIN` 角色，见 `ID-3001` |

## 覆盖清单

| 分组 | 接口 |
|---|---|
| 00 准备 | `GET /api/v1/auth/csrf` |
| 01 认证 | `POST /register`、`POST /login`、`GET /me`、`PATCH /me` |
| 02 问题 | `POST /questions`★、`GET /questions`、`GET /questions/{id}`、`PUT /questions/{id}` |
| 03 回答与采纳 | `POST /questions/{id}/answers`、`PUT /questions/{id}/accepted-answer`、`POST /answers/{id}/endorsement` |
| 04 评价 | `PUT /votes`（回答票、问题票）、`POST /comments` |
| 05 声誉 | `GET /users/{id}/reputation`、`GET /users/{id}/reputation/ledger`、`GET /leaderboard` |
| 06 悬赏 | `GET /questions/{id}/bounty`、`POST /questions/{id}/bounty` |
| 07 搜索与通知 | `GET /search/questions`、`GET /questions/{id}/related`、`GET /notifications`、`GET /notifications/unread-count` |
| 08 管理员 | `GET /admin/users/all`、`GET /admin/reputation/reconciliation` |
