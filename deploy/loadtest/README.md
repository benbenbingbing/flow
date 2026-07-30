# Flow 长时间压测工具

本目录提供可重复执行的业务压测工具。默认配置只运行 2 分钟烟测；持续
6 小时 30 分钟的测试必须显式选择 `soak`。测试目标必须是隔离的压测环境，
不得直接对未授权的生产环境执行。

## 覆盖范围

- 登录、令牌定期续期和独立登录流量。
- 当前用户、权限、用户、用户组、字典、待办、已办和任务统计查询。
- 用户组和字典的创建、状态变更、详情查询和删除闭环。
- 可选文件上传、幂等重放、下载和删除闭环，用于验证 S3 兼容存储。
- 可选已发布实体的列表、计数、详情、创建、更新和删除闭环。
- 独立 `/livez` 可用性探针。
- k6 阈值、结构化汇总、目标探针记录、Kubernetes 前后快照，以及现有
  Prometheus/Pod/磁盘/重启采集脚本联动。

测试数据使用 `load_<run-id>_` 前缀。正常迭代会立即清理；进程异常中断时可
使用 `cleanup.sh` 清理同一运行批次遗留的用户组和字典。在直接访问测试 k3s
集群的场景，还可按文件上传幂等键精确发现遗留文件，并通过应用 API 删除。逻辑
删除数据、审计日志、流程历史和遥测数据按系统保留策略处理，不应直接执行数据库
物理删除。

## 准备凭据

高并发测试应建立专用账号池，避免一个管理员账号掩盖权限缓存、用户隔离和
登录竞争问题。凭据文件必须位于 Git 仓库之外，权限设置为 `0600`：

```json
[
  {"username": "loadtest-01", "password": "replace-me"},
  {"username": "loadtest-02", "password": "replace-me"}
]
```

账号至少需要以下能力：

- 用户、用户组、字典和流程任务的读取权限；
- 启用写入场景时，需要用户组、字典和文件管理权限；
- 启用实体场景时，需要目标实体的列表、查看、新增、更新和删除权限。

不得使用 bootstrap 管理员作为正式长稳测试账号。不要把密码、Token、私钥、
真实服务地址的认证参数写入 Git。

## 配置

```bash
cp deploy/loadtest/config.example.env deploy/loadtest/config.env
chmod 600 deploy/loadtest/config.env
```

至少填写：

```bash
LOADTEST_API_BASE_URL=https://flow-loadtest.example.com/api
LOADTEST_HEALTH_URL=https://flow-loadtest.example.com/livez
LOADTEST_CREDENTIALS_FILE=/absolute/path/loadtest-credentials.json
```

非烟测场景要求目标二次确认：

```bash
LOADTEST_PROFILE=soak
LOADTEST_CONFIRM_TARGET=https://flow-loadtest.example.com/api
```

允许业务写入还必须填写：

```bash
LOADTEST_ALLOW_WRITES=true
LOADTEST_CONFIRM_WRITES=isolated-test-environment
```

需要在固定维护窗口内执行完整阶段时，可覆盖业务阶段计划。JSON 必须使用单引号
包裹，`LOADTEST_TOTAL_DURATION` 是 k6 的认证与健康探针总时长，
`LOADTEST_DURATION_SECONDS` 是采集脚本使用的同一时长（秒）：

```bash
LOADTEST_BUSINESS_PHASES_JSON='[{"name":"warmup","start":"0s","duration":"10m","rate":4},{"name":"steady","start":"10m","duration":"50m","rate":8}]'
LOADTEST_TOTAL_DURATION=1h
LOADTEST_DURATION_SECONDS=3600
```

阶段名称必须唯一，且只允许小写字母、数字和下划线。自定义计划仍受原有并发、
阈值、目标确认和写入确认约束；总时长必须覆盖最后一个阶段。

## 执行顺序

先运行只读烟测：

```bash
LOADTEST_PROFILE=smoke deploy/loadtest/run.sh deploy/loadtest/config.env
```

再运行 35 分钟基线：

```bash
LOADTEST_PROFILE=baseline deploy/loadtest/run.sh deploy/loadtest/config.env
```

基线通过后运行 6 小时 30 分钟长稳：

```bash
LOADTEST_PROFILE=soak deploy/loadtest/run.sh deploy/loadtest/config.env
```

长稳通过后，单独运行阶梯压力和突刺测试：

```bash
LOADTEST_PROFILE=stress deploy/loadtest/run.sh deploy/loadtest/config.env
LOADTEST_PROFILE=spike deploy/loadtest/run.sh deploy/loadtest/config.env
```

`run.sh` 优先使用本机 `k6`，否则使用固定的 `grafana/k6:2.0.0` 容器。
容器下载代理属于主机运行时配置，不会被写入镜像、Helm 或仓库。

## 6.5 小时场景

| 阶段 | 时长 | 默认到达率 | 目的 |
| --- | ---: | ---: | --- |
| 预热 | 15 分钟 | 基线的 50% | JIT、缓存、连接池预热 |
| 稳态 | 90 分钟 | 8 次迭代/秒 | 建立正常资源曲线 |
| 峰值 | 30 分钟 | 24 次迭代/秒 | 验证扩缩容与下游余量 |
| 长稳 | 3 小时 45 分钟 | 8 次迭代/秒 | 发现泄漏、堆积和性能漂移 |
| 突刺 | 10 分钟 | 48 次迭代/秒 | 验证短时冲击和排队保护 |
| 恢复 | 20 分钟 | 8 次迭代/秒 | 验证资源和延迟回落 |

到达率表示业务迭代数，不等于 HTTP QPS。一个写迭代会执行创建、查询、变更和
删除多个请求。正式参数必须根据 35 分钟基线结果和目标业务峰值校准。

## 结果

每次运行在 `deploy/loadtest/results/<run-id>/` 生成：

- `run.json`：目标、配置、开始结束时间和退出状态；
- `summary.json`、`summary.txt`：k6 聚合结果和阈值；
- `analysis.json`：最终门禁结论；
- `target-canary.jsonl`：独立健康探针；
- `observability.jsonl`：启用本地 Kubernetes 观测时的指标样本；
- `k8s-*-before/after.txt`：节点、Pod、资源和事件前后快照；
- `k6.log`、`observer.log`：原始运行日志。

结果目录默认被 Git 忽略。正式验收结果应转存到受控对象存储，并设置保留期和
访问权限。

`analysis.json` 只有在以下条件同时满足时才通过：k6 全部阈值通过、无 dropped
iteration、创建与删除计数相等、清理和金丝雀零失败；业务及观测组件全程可用且
无重启；应用指标无请求错误；数据库连接、行锁、内存均未越过配置门限，且没有新增
日志等待或磁盘临时表；OTel 没有拒绝或接收失败并确实持续导出 Trace；Prometheus
没有新增规则求值或目标同步失败。历史累计计数使用首尾增量判断，不会把测试前事件
误算到当前批次。

登录 p95 门禁只计算独立 `login_traffic` 场景。业务 VU 在首次使用时也会登录，阶段
切换会形成刻意的客户端会话冷启动突发；这些样本继续受全部登录 p99 门禁约束，但
不会混入持续认证流量的 SLA。两个限制分别保持严格口径，不能通过放宽阈值规避。

## 中断清理

```bash
LOADTEST_RUN_ID=<run-id> \
LOADTEST_CONFIRM_CLEANUP=<run-id> \
  deploy/loadtest/cleanup.sh deploy/loadtest/config.env
```

清理脚本只匹配 `load_<run-id>_` 前缀，不接受空值或其他字符，也不会执行数据库
级删除。启用过文件生命周期且清理主机可以访问目标 k3s 时，同时设置：

```bash
LOADTEST_CLEANUP_K8S_FILES=true
FLOW_NAMESPACE=flow-hardening
FLOW_MYSQL_STATEFULSET=local-mysql
FLOW_MYSQL_DATABASE=workflow
```

文件发现只执行 `load-file-<run-id>-` 幂等键前缀的只读查询，实际删除仍通过带权限
校验和审计的应用 API。实体和文件通常在同一迭代的 `finally` 中删除；中断后仍需
从结果日志、数据库活跃记录和对象存储清单三方核对。

完整执行与验收方案见
[`docs/production-long-duration-load-test-plan.md`](../../docs/production-long-duration-load-test-plan.md)。
