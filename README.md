# CampusOverflow

校园技术问答社区。学生提问、回答、投票、采纳；教师认证优质回答；声望与徽章激励；中文全文检索；事件驱动的通知中心。

后端是 **Spring Boot 3 的模块化单体**，按四个限界上下文切分，跨上下文只通过 `*-api` 发布语言通信，状态变更靠**事务性发件箱**异步投递。前端是 React + TypeScript + Vite。

当前版本 **v1.0.1**，Sprint 0–4 已交付。

---

## 1. 一键部署（推荐）

```bash
# 1) 准备环境变量（可选；不建也能用开发默认值启动）
cp deploy/.env.example deploy/.env
#    Windows: Copy-Item deploy\.env.example deploy\.env

# 2) 起全栈（首次会自动构建前后端镜像）
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

| 地址 | 说明 |
| --- | --- |
| http://localhost | 前端页面 |
| http://localhost:8080/swagger-ui.html | 接口文档 |
| http://localhost:8080/actuator/health/readiness | 后端就绪探针 |
| http://localhost:3000 | Grafana（加 `--profile observability`） |
| http://localhost:9090 | Prometheus（加 `--profile observability`） |

带监控栈启动：

```bash
docker compose -f deploy/docker-compose.yml --profile observability up -d --build
```

停止与清理：

```bash
docker compose -f deploy/docker-compose.yml down        # 停止，保留数据卷
docker compose -f deploy/docker-compose.yml down -v     # 停止并删除数据（不可恢复）
```

### 演示账号

默认 `SPRING_PROFILES=prod,demo`，启动时自动写入演示数据，**密码统一 `Campus2026`**：

| 账号 | 角色 |
| --- | --- |
| `admin01` | 管理员 |
| `T2001` | 教师（王老师，课程 CS2001「数据结构」） |
| `TA3001` | 助教（李助教） |
| `2023001` / `2023002` / `2023003` | 学生（小明 / 小红 / 小刚） |

演示数据含 3 个问题，其中「Spring `@Transactional` 同类调用失效」带悬赏并已完成采纳结算，登录后通知中心能同时看到回答、徽章、悬赏获奖三类通知。

> 真实部署请把 `deploy/.env` 里的 `SPRING_PROFILES` 改成 `prod`，避免写入演示数据。

---

## 2. 本地开发

```bash
# 1) 只起中间件
docker compose -f deploy/docker-compose.yml up -d mysql redis

# 2) 后端（dev profile）
cd backend && mvn spring-boot:run -pl co-bootstrap -Dspring-boot.run.profiles=dev

# 3) 前端
cd frontend && npm install && npm run dev      # http://localhost:5173
```

---

## 3. 模块结构

| 上下文 | 职责 | 模块前缀 |
| --- | --- | --- |
| Identity | 注册、登录、课程、角色、选课、教师认证 | `co-identity-*` |
| Q&A | 提问、回答、评论、投票、采纳、悬赏、标签 | `co-qa-*` |
| Reputation | 积分账本、徽章、排行榜、悬赏结算 | `co-reputation-*` |
| Discovery | 全文检索、相关问题、通知中心 | `co-discovery-*` |

共享部分：`co-shared-kernel`（领域异常、Actor、事件契约、分页）、`co-bootstrap`（安全链、发件箱中继、全局异常、Flyway、装配）。

每个上下文内部再分层：`domain`（聚合与业务规则，零框架依赖）→ `application`（用例编排与事务边界）→ `infrastructure`（JPA / Redis / JDBC 适配）→ `api`（发布语言与外部契约）。

### 值得先读的设计文档

- `docs/adr/`：12 条架构决策记录——**为什么**这样设计比代码本身更重要
- `docs/ddd/`：上下文映射、领域模型、事件目录、统一语言、聚合清单
- `docs/backlog/`：52 个 PBI 的 Backlog、Gherkin 验收标准、用户故事地图
- `docs/scrum/`：Sprint 0–4 的计划、回顾与燃尽图
- `docs/reports/`：测试结果与覆盖率报告
- `docs/final/`：答辩演示文稿
- `docs/错误码.md`：错误码分段与 HTTP 状态映射，领域异常必须遵守

---

## 4. 质量门禁与自检

| 项目 | 要求 | 自检命令 |
| --- | --- | --- |
| 单元测试 | 全绿（领域层 + 应用层全量） | `mvn -f backend/pom.xml test` |
| 架构约束 | ArchUnit FF-1~FF-6 全通过 | `mvn -f backend/pom.xml test -pl co-bootstrap -am -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false` |
| 领域层覆盖率 | 四个上下文分别 ≥ 70% | `mvn -f backend/pom.xml verify -DskipITs` |
| 集成测试 | 端到端链路通过（需 Docker） | `mvn -f backend/pom.xml verify -Dsurefire.skip=true` |
| 前端 | 类型检查与构建通过 | `cd frontend && npm run typecheck && npm run build` |
| 一键部署 | compose 起全栈并探活通过 | CI 的 `deploy` job（main push 时执行） |

CI 见 `.github/workflows/ci.yml`，四个 job：后端构建与验证、前端构建、容器镜像构建、一键部署冒烟。

**集成测试自 Sprint 3 起是硬门禁**——此前设了 `continue-on-error`，Docker API 版本不匹配导致的失败被掩盖了很久；移除该开关后才真正能拦问题。

---

## 5. 常见坑

| 现象 | 原因 |
| --- | --- |
| 声誉没有变化 | 声誉是异步结算的：事件先落 `outbox_event`，由中继投递；查 `last_error` 字段 |
| 中文搜索没结果 | 必须用 compose 里的 MySQL（已配 `ngram_token_size=2`），自建库要对照参数 |
| 同一事件加了两次分 | 消费端必须幂等：账本唯一键 `(event_id, user_id, reason)` 或 `processed_event` 表 |
| 通知红点不刷新 | 已改为 SSE 推送（Sprint 4，#92），断线时回退为 30 秒轮询 |
| 提交后 CI 红 | 先本地跑 `mvn verify` 再推；不要把红的分支合进 `main` |
| 合并后分支残留 | 合并即删：PR 合入后立即删除源分支，远端只留 main 与在途分支 |

---

## 6. 已知问题

见 `RELEASE_NOTES.md` 第五节。均已在 Sprint 4 剩余 Issue 中登记。
