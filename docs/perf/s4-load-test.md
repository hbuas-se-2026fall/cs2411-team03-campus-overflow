# Sprint 4 压测报告：问题列表与详情页（#51 / PB-45）

> 结论先行：**脚本与口径已就绪，数值尚未采集**——本机 Docker Desktop 4.96.1 无法启动 Linux 引擎（见「环境」一节），compose 栈起不来，因此不在本地伪造任何数字。补跑只需三条命令（见文末），跑完把表格填上即可。

## 一、环境

| 项 | 值 |
| --- | --- |
| 目标栈 | `deploy/docker-compose.yml`（MySQL 8.0.39 + Redis 7 + 后端 + nginx，`prod,demo` profile） |
| 压测工具 | k6 官方镜像 `grafana/k6`，`--network host` 直连宿主机 8080 |
| 本机 | Windows 11，Docker Desktop 4.96.1（WSL2 引擎），JDK 21，无 k6 原生安装 |
| 本机是否成功起栈 | **否** |

本机 Docker Desktop 在创建 WSL 数据盘时报错退出，原文：

```
starting engine: engine linux/wsl failed to start: starting WSL engine:
creating distribution storage: creating vhdx: preparing VHDX: creating virtual disk:
failed to create virtual disk: The filename, directory name, or volume label syntax is incorrect.
```

`docker compose config` 可以正常通过（编排文件本身无误），但 `up -d` 需要引擎，故无法启动。这是本机 Docker 安装的缺陷，与仓库代码无关；修复需要在 Docker Desktop 里做工厂重置或重装（会清空本地镜像与卷），因此没有擅自执行。

**因此本报告的环境行留待彩排机实测后填写**，并明确记录：不允许用 IDEA 里跑的 dev 模式数据充当本报告结果——dev 模式没有 nginx 反向代理、没有连接池调优、缓存行为也不同，数字没有参考价值。

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

补充的自定义指标：`list_latency_ms`、`detail_latency_ms`（Trend）、`list_failed`、`detail_failed`（Rate），用于在报告里直接给出分场景分位数，而不必从总的 `http_req_duration` 里反推。

### 运行

```bash
# 1) 起栈（必须，不要用 dev 模式）
docker compose -f deploy/docker-compose.yml up -d --build
#    等后端就绪
curl -fsS http://localhost:8080/actuator/health/readiness

# 2) 压测（-i 把脚本从标准输入喂给容器，无需本地装 k6）
docker run --rm -i --network host grafana/k6 run \
  --summary-trend-stats "avg,min,med,p(50),p(90),p(95),p(99),max" \
  - < perf/questions-list.js

# 3) 清理（压测会写大量通知与声誉事件，不清理会污染演示数据）
docker compose -f deploy/docker-compose.yml down -v
```

`--summary-trend-stats` 里显式加了 `p(99)`，因为 k6 默认汇总只到 p(95)。

## 三、缓存命中验证

### 代码层事实（已核实）

- 列表页走 Cache-Aside 缓存，端口 `QuestionListCache`，实现 `RedisQuestionListCache`。
- 读取路径：`QuestionQueryService.questionPage()` → `cache.get(courseId, page)`，命中直接返回，未命中查库后 `cache.put()` 回写。
- key 规则：`qa:list:{代际号}:{courseId|all}:{page}:{size}`，代际号存在 `qa:list:gen`。
- TTL：60 秒 + 0–15 秒随机抖动（防雪崩）。
- 失效策略：任何写操作（提问、编辑、删除、回答、投票、采纳）把 `qa:list:gen` 加 1，旧 key 自然过期，不做 `KEYS` 扫描；失效动作注册在事务提交之后，避免并发读把旧数据写回。
- 详情页**没有**缓存层：`GET /api/v1/questions/{id}` 直接查库并累加浏览量（`ViewCounter`，Redis 计数后批量落库）。所以详情场景的 P95 反映的是数据库 + 连接池的真实能力。

### 采集方法

在压测前后各取一次 Redis 统计，差值即本次压测造成的命中/未命中次数：

```bash
docker exec co-redis redis-cli info stats | grep -E '^keyspace_(hits|misses)'
```

命中率 = Δhits / (Δhits + Δmisses)。注意分母里还包含会话读取等其它 Redis 访问，因此这个数是"整库"口径；若要只算列表缓存，可先 `redis-cli --scan --pattern 'qa:list:*' | wc -l` 观察 key 数量，并在压测中观察 `qa:list:*` 的 key 数是否稳定。

## 四、结果

> 待彩排机补跑后填写。

| 场景 | 并发 | 持续 | RPS | P50 (ms) | P95 (ms) | P99 (ms) | 错误率 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A 列表 | 50 | 30s | 待测 | 待测 | 待测 | 待测 | 待测 |
| B 详情 | 50 | 30s | 待测 | 待测 | 待测 | 待测 | 待测 |

| 项 | 值 |
| --- | --- |
| Δkeyspace_hits | 待测 |
| Δkeyspace_misses | 待测 |
| 命中率 | 待测 |
| 是否满足 QS-01（详情 P95 ≤ 300ms） | 待测 |
| 机器配置（CPU / 内存 / 磁盘） | 待测 |

## 五、已知会影响读数的因素

1. **演示数据只有 3 条问题**。列表页 `size=20` 时一页装得下全部数据，SQL 计划远比真实数据集简单；这组数字只能说明"小数据量下的上限"，不代表规模化后的表现。若要更接近真实，需先灌入百级问题再压，但会改变演示数据，需在独立环境做。
2. **详情场景会写 Redis**（浏览量计数），是读多写少的混合负载，不是纯读。
3. **列表缓存 TTL 只有 60–75 秒**，30 秒的压测窗口若恰好跨过一次全量失效，会看到一段"回源"的延迟抬升——这是设计如此（代际号失效），不是异常。
4. **压测会触发大量事件**（通知、声誉、搜索投影），后台中继在压测期间本身也在消耗 CPU，会略微抬高延迟。
5. 首次请求包含 JIT 预热与连接池建立，50 并发下的 P99 通常对预热敏感；若对 P99 有要求，应预热后再开始计时（脚本未做预热，保持简单可复现）。

## 六、补跑待办

- [ ] 在能起 Docker 的机器上跑三条命令，回填第四节的表格
- [ ] 记录机器配置与 Docker 版本
- [ ] 若 P95 超出 300ms，定位是 SQL 慢查询、连接池不足还是缓存未命中，另开 Issue
- [ ] 压测后确认已执行 `down -v`，演示数据未被污染
