# ADR-012：Grafana 面板随编排 provisioning

**状态**：Sprint 4 已完成 ｜ **驱动特征**：可观测性（PB-44 / #50）

## 背景

三个运维指标在 Sprint 2–3 就注册好了，但「看指标」这件事一直停在注释层面：

| 指标 | 注册位置 | 语义 |
| --- | --- | --- |
| `co_outbox_backlog` | `OutboxRelay` | 未达重试上限的待投递事件数，积压即消费端变慢或出错 |
| `co_outbox_dead_letters` | `OutboxRelay` | 已达重试上限、不再自动重试的事件数，> 0 需人工介入 |
| `co_reputation_mismatches` | `ReputationJobs` | 账本汇总与账户余额不一致的用户数（FF-8），应恒为 0 |

`deploy/prometheus.yml` 抓 `/actuator/prometheus`，文件末尾只用注释列了「建议关注的四个指标」。而 `deploy/docker-compose.yml` 的 grafana 服务只挂了数据卷 `grafana-data:/var/lib/grafana`，没有 provisioning 挂载，结果就是：**换了台机器（或删了卷）起栈，Grafana 里空空如也**，面板得靠人手工导入 JSON——这件事被记成了 `RELEASE_NOTES.md` 的「已知问题」。

注释不是面板，面板是运维在故障时刻真正会看的东西。一个需要手工操作才能出现的面板，等于故障时大概率不存在。

## 决策

把面板变成仓库里的代码，Grafana 启动时自动加载：

1. `deploy/grafana/provisioning/datasources/prometheus.yml` 注册 Prometheus 数据源，并**固定 `uid: prometheus`**。
2. `deploy/grafana/provisioning/dashboards/dashboards.yml` 以 file provider 把面板加载到 `CampusOverflow` 文件夹，`allowUiUpdates: false`。
3. `deploy/grafana/dashboards/campus-overflow.json` 定义四个面板，2×2 布局：发件箱积压、死信事件、声誉对账不一致、详情页 P95。
4. `deploy/docker-compose.yml` 的 grafana 服务加两个**只读**挂载：`./grafana/provisioning:/etc/grafana/provisioning:ro` 与 `./grafana/dashboards:/var/lib/grafana/dashboards:ro`。

三个具体取舍：

- **数据源固定 uid**。面板 JSON 引用数据源需要稳定标识；写名字会在改名时断，依赖自动生成的 uid 又不可预测。固定 uid 后，仓库里两份文件是一对明确的常量。
- **`allowUiUpdates: false`**：单一真相源在仓库。运维在 UI 上调了面板，重启即被覆盖——这是刻意的，避免「服务器上的面板和仓库里的面板不一样」这类无法复现的状态。
- **详情页 P95 用 `histogram_quantile` 而非直接取 summary**：Spring Boot 3 + Micrometer 默认导出 `http_server_requests_seconds_bucket/_count/_sum`（percentile histogram），不是 summary。查询写成
  `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri="/api/v1/questions/{questionId}"}[5m])) by (le))`，
  阈值 0.3 s 变红（QS-01）。

阈值设计贴着业务后果而不是「看起来合理」：积压 50 黄 / 100 红（并写明「> 100 持续 5 分钟」）；死信与对账不一致为 `stat` 面板，> 0 直接红底——这两类只要非零就已经需要人工介入，不存在「观察一段时间」的余地。

## 后果

- **正面**：起栈（带 `--profile observability`）后面板自动出现，`RELEASE_NOTES.md` 的「需手工导入」一条可以删掉；面板随代码评审一起演进，改面板走 PR 而不是「谁在 UI 上点过」。
- **负面**：面板 JSON 是手写的大文件，改动时容易写错字段。缓解手段是 CI 的 deploy job 会真起栈（ADR-011），且 `docker compose config` 校验挂载；JSON 语法错误会让 Grafana 加载失败，起栈时即可见。
- **运维约束**：provisioning 挂载为只读，Grafana 进程无法回写；若将来需要「运维临时调面板」，得改仓库或另建一个可写目录，而不是放开这个挂载。
- **数据面**：面板只是展示层，指标缺失（例如某次重构误删了 `Gauge.builder`）不会让面板报错，只会让曲线消失。因此指标注册点仍需单元/集成测试覆盖，本 ADR 不承担该职责。

## 被否决的替代方案

- **继续手工导入 JSON**：故障时刻最需要面板，而手工导入恰恰在最不该依赖人的时刻依赖人。
- **让 Grafana 用默认自动 uid，面板按数据源名字引用**：名字可改、uid 不可预测，断链时症状是「面板全空但没有报错」，排查成本高于固定 uid 的维护成本。
- **把面板塞进 Grafana 镜像**：需要自建镜像与额外构建链，且面板更新要重新构建镜像；bind mount 让面板改动与代码改动同源同版本。
- **面板里直接用 `http_server_requests_seconds` 取 summary**：Spring Boot 3 默认不导出 summary，查询会永远为空——这正是「照抄模板不看真实指标名」的典型坑。
