# Sprint 4 压测报告：问题列表与详情页（#51 / PB-45）

> 结论：**已完成实测，两个场景都满足 QS-01**。详情页 P95 **99.8ms**（门槛 300ms），列表页 P95 **52.05ms**，错误率 0%。列表缓存命中率 **100%**（Δhits 117032 / Δmisses 0）。
>
> 数据在一次性 Ubuntu runner 上通过 `deploy/docker-compose.yml` 起全栈取到，不是 dev 模式、不是本机残留进程；起栈脚本与被测版本随本报告一起留档，可逐条复现。

## 一、环境

| 项 | 值 |
| --- | --- |
| 目标栈 | `deploy/docker-compose.yml`（MySQL 8.0.39 + Redis 7 + 后端 + nginx，`SPRING_PROFILES_ACTIVE=prod,demo`） |
| 机器 | GitHub Actions `ubuntu-latest`：**4 vCPU / 15 GiB RAM / 3 GiB swap / 145 GB SSD** |
| 容器运行时 | Docker 29.4.2（runner 自带） |
| 压测工具 | `grafana/k6` 镜像内运行，`--network host` 直连 `localhost:8080`，与 compose 里 nginx 的上游一致 |
| 被测提交 | main `252a27b`（已含 #93 的后端镜像 jar 路径修复） |
| 压测时间 | 2026-11-19 04:27:46 → 04:28:46（UTC） |

### 为什么不是本机

本机 Windows 11 + Docker Desktop 4.96.1 无法启动 Linux 引擎，创建 WSL 数据盘时失败：

```
starting engine: engine linux/wsl failed to start: starting WSL engine:
creating distribution storage: creating vhdx: preparing VHDX: creating virtual disk:
failed to create virtual disk: The filename, directory name, or volume label syntax is incorrect.
```

`docker compose config` 能过（编排文件本身无误），`up -d` 却起不来。做过一次等价于「Reset to factory defaults」的清理（停进程 → `wsl --unregister docker-desktop` → 移走损坏的 `wsl\main` 盘目录 → 重启），发行版能重新注册、`ext4.vhdx` 能重建，但**下一步建数据盘仍然报同一个错**，属系统级 VHD API 层面的缺陷，需要重装 Docker Desktop 才能验证。为了不让 PBI 停在「脚本就绪、数字待补」，改用一次性 runner 起同一份编排取数。

**绝不使用 dev 模式数据**：IDEA 里跑的 dev 模式没有 nginx、没有连接池调优、缓存行为不同，那种数字没有参考价值。本报告所有数字都来自 compose 栈。

## 二、脚本

`perf/questions-list.js`

| 场景 | 请求 | 强度 |
| --- | --- | --- |
| A `list` | `GET /api/v1/questions?page=0&size=20` | `constant-vus`，50 并发，30 秒 |
| B `detail` | `GET /api/v1/questions/{id}`（`setup()` 取列表第一条） | 同上，**与 A 串行**，避免互相抢连接池污染读数 |

阈值（任一不满足即 k6 退出码非 0）：

- `http_req_duration{scn:list}` → `p(95)<300`
- `http_req_duration{scn:detail}` → `p(95)<300`（QS-01）
- `http_req_failed{scn:list}` / `http_req_failed{scn:detail}` → `rate<0.01`

### 运行

```bash
# 1) 起栈（必须，不要用 dev 模式）
docker compose -f deploy/docker-compose.yml up -d --build
curl -fsS http://localhost:8080/actuator/health/readiness

# 2) 压测（-i 把脚本从标准输入喂给容器，无需本地装 k6）
docker run --rm -i --network host -v "$PWD/perf:/perf:ro" grafana/k6 run \
  --summary-trend-stats "avg,min,med,p(50),p(90),p(95),p(99),max" \
  /perf/questions-list.js

# 3) 清理（压测会写大量通知与声誉事件，不清理会污染演示数据）
docker compose -f deploy/docker-compose.yml down -v
```

`--summary-trend-stats` 里显式加了 `p(99)`：k6 默认汇总只到 p(95)，而任务卡要求 P99。

## 三、结果

| 场景 | 并发 | 持续 | 请求总数 | RPS | P50 (ms) | P90 (ms) | P95 (ms) | P99 (ms) | max (ms) | 错误率 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A 列表 | 50 | 30s | 58514 | **1950** | 22.43 | 43.65 | **52.05** | 74.51 | 151.61 | 0.00% |
| B 详情 | 50 | 30s | 31643 | **1054** | 39.90 | 84.92 | **99.80** | 136.04 | 273.21 | 0.00% |

合计 90158 请求 / 60.08 秒 = **1501 req/s**（两个场景串行，所以整场平均被 60 秒摊薄）。

| 项 | 值 |
| --- | --- |
| 初始 `keyspace_hits` / `keyspace_misses` | 2 / 2 |
| 压测后 `keyspace_hits` / `keyspace_misses` | 117034 / 2 |
| **Δhits / Δmisses** | **117032 / 0** |
| **命中率** | **100%**（117032 / 117032） |
| 缓存 key 数（`qa:list:*`） | 压测前后均为 2 |
| 是否满足 QS-01（详情 P95 ≤ 300ms） | **是**，99.8ms，留 3 倍余量 |
| 是否满足阈值（两场景 P95、错误率） | **全部通过**，k6 四条阈值均 ✓ |

### 关于「每个列表请求 2 次 Redis 命中」

117032 / 58514 ≈ 2.000，不是巧合：`RedisQuestionListCache` 先用一个 GET 读代际号 `qa:list:gen`，再用第二个 GET 读 `qa:list:{gen}:all:0:20`。所以命中数天然是请求数的两倍，Δmisses 为 0 说明每个请求都命中、一次都没有回源。

## 四、缓存命中验证

### 代码层事实（已核实）

- 列表页走 Cache-Aside，端口 `QuestionListCache`，实现 `RedisQuestionListCache`。
- 读取路径：`QuestionQueryService.questionPage()` → `cache.get(courseId, page)`，命中直接返回，未命中查库后 `cache.put()` 回写。
- key 规则：`qa:list:{代际号}:{courseId|all}:{page}:{size}`，代际号存在 `qa:list:gen`。
- TTL：60 秒 + 0–15 秒随机抖动（防雪崩）。
- 失效策略：任何写操作（提问、编辑、删除、回答、投票、采纳）把 `qa:list:gen` 加 1，旧 key 自然过期，不做 `KEYS` 扫描；失效动作注册在事务提交之后（`afterCommit`），避免并发读把旧数据写回。
- 详情页**没有**缓存层：`GET /api/v1/questions/{id}` 直接查库并累加浏览量（`ViewCounter`，Redis 计数后批量落库）。因此详情场景的 P95 是数据库 + 连接池的真实读数，99.8ms 说明数据访问本身很快，瓶颈不在 SQL。

### 采集方法

```bash
docker compose -f deploy/docker-compose.yml exec -T redis redis-cli info stats | grep -E '^keyspace_(hits|misses)'
docker compose -f deploy/docker-compose.yml exec -T redis redis-cli --scan --pattern 'qa:list:*' | wc -l
```

压测前后各取一次，差值即本次压测造成的命中/未命中。注意这个口径是"整库"的，分母里还包含会话等其它 Redis 访问；本次 Δmisses 恰为 0，说明窗口内除缓存回源外没有其它未命中。

## 五、已知会影响读数的因素（重要，别把结论读大）

1. **演示数据只有 3 条问题**。`size=20` 一页装得下全部，SQL 计划远比真实数据集简单。这组数字是"该数据量下的上限"，**不能**当成百级/千级数据量的承诺。
2. **详情场景是读写混合**：除了查库还要 `INCR` 浏览量计数。它不是纯读。
3. **列表缓存 TTL 只有 60–75 秒**。30 秒窗口内若恰好跨过一次代际号失效，会看到一段回源抬升——本次 Δmisses 为 0，说明窗口内没有撞上。
4. **压测会触发大量事件**（通知、声誉、搜索投影），后台中继在这 60 秒里本身也在抢 CPU；真实生产的事件密度低于压测。
5. **没有预热**。首个请求包含 JIT 与连接池建立，对 P99 影响明显（P99 136ms vs P95 99.8ms 的差距部分来自这里）。
6. **runner 是共享虚拟机**，4 vCPU 是超分后的额度，同一宿主机上的邻居会影响绝对延迟。**横向比较应在同一环境、同一时间窗口内做**，不要拿本报告的数字去和外部的生产机器对比。

## 六、过程中发现的一个真问题（另报 Issue）

第一次起栈时后端启动失败，日志原文：

```
org.springframework.dao.CannotAcquireLockException: PreparedStatementCallback;
SQL [INSERT INTO outbox_event (event_id, event_type, payload, occurred_at, created_at, attempts) VALUES (?, ?, ?, ?, ?, 0) ];
Deadlock found when trying to get lock; try restarting transaction
  at ...OutboxIntegrationEventPublisher.publish(OutboxIntegrationEventPublisher.java:35)
  at ...QuestionCommandService.post(QuestionCommandService.java:64)
  at ...DemoDataSeeder.run(DemoDataSeeder.java:142)
```

即：**演示数据播种（写问题 → 写发件箱事件）与发件箱中继（每 500ms 轮询并更新 outbox 行）在并发时可能死锁**，导致后端启动即退出、`一键部署冒烟` 门禁偶发红灯。重跑同一提交可以恢复（main 上连续三次部署冒烟都是 success），所以属于**低概率但真实存在**的竞态，不是环境噪声。已按上述证据单独提交 Issue。

复现/规避：起栈后若 `co-backend` 退出，`docker compose -f deploy/docker-compose.yml up -d --force-recreate backend` 重试即可（本次实测重启后一次通过）。

## 七、复现步骤（完整）

```bash
# 前提：一台能跑 Docker 的 Linux 机器（4 vCPU 起）
git checkout 252a27b
docker compose -f deploy/docker-compose.yml up -d --build
for i in $(seq 1 60); do curl -fsS http://localhost:8080/actuator/health/readiness && break; sleep 5; done

docker compose -f deploy/docker-compose.yml exec -T redis redis-cli info stats | grep keyspace_
docker run --rm -i --network host -v "$PWD/perf:/perf:ro" grafana/k6 run \
  --summary-trend-stats "avg,min,med,p(50),p(90),p(95),p(99),max" /perf/questions-list.js
docker compose -f deploy/docker-compose.yml exec -T redis redis-cli info stats | grep keyspace_

docker compose -f deploy/docker-compose.yml down -v
```
