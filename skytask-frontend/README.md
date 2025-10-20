# SkyTask 前端控制台

基于 Vue CLI 5 + Vue 3 + Element Plus 的分布式任务调度平台管理界面。提供任务管理、调度监控、执行节点运维、配置与告警等操作入口，对接后端的 SkyTask Scheduler / Admin 服务。

## 快速开始

```bash
pnpm install # 或 npm install / yarn
pnpm serve   # 本地开发，默认端口 8090
```

> `vue.config.js` 已将 `/api`、`/auth` 代理到 `http://localhost:8080`，可按实际后端地址调整。

## 目录结构

- `src/main.js`：Vue 应用入口，注册 Router、Vuex、Element Plus。
- `src/router/index.js`：路由，包含总览、任务中心、节点监控、配置管理、任务详情。
- `src/store`：Vuex 模块，`tasks`/`nodes`/`auth`/`ui` 对应业务状态。
- `src/api`：HTTP 封装及业务 API，按照任务、节点、告警、鉴权分类。
- `src/views`：页面组件。
  - `DashboardView.vue`：执行趋势、节点健康、最近告警等监控面板。
  - `TaskCenterView.vue`：任务检索、分组、创建/编辑/启停、执行记录。
  - `TaskDetailView.vue`：任务元数据、策略、依赖链、执行日志。
  - `NodeMonitorView.vue`：执行器节点负载、下线/重平衡、心跳详情。
  - `ConfigCenterView.vue`：全局调度参数、告警规则、环境/租户说明。
- `src/components`：通用组件。
  - `TaskFormDrawer.vue`：任务新增/编辑抽屉。
  - `TaskExecutionDrawer.vue`：任务执行记录抽屉。
  - `StatusTag.vue`：统一的状态标签。
- `src/styles`：全局样式、主题变量。

## 前端调用约定

所有接口遵循 `/api` 前缀，并自动附带：

- `Authorization: Bearer {token}`（来自 `auth` 模块的 JWT）。
- `X-SkyTask-Env: dev|test|prod` 当前环境标识。

### 任务管理

| 功能 | 方法 | 路径 | 参数 |
| --- | --- | --- | --- |
| 查询任务统计 | GET | `/api/tasks/metrics` | `range` (`24h`/`7d`) |
| 查询任务列表 | GET | `/api/tasks` | `page`,`size`,`keyword`,`status`,`owner`,`tags`（逗号分隔） |
| 获取任务详情 | GET | `/api/tasks/{taskId}` | - |
| 创建任务 | POST | `/api/tasks` | 任务实体 |
| 更新任务 | PUT | `/api/tasks/{taskId}` | 任务实体 |
| 启停任务 | PATCH | `/api/tasks/{taskId}/status` | `{ enabled: boolean }` |
| 删除任务 | DELETE | `/api/tasks/{taskId}` | - |
| 手动触发 | POST | `/api/tasks/{taskId}/trigger` | `{ manual: true, operator: string }` |
| 执行记录 | GET | `/api/tasks/{taskId}/records` | `page`,`size` |
| Cron 智能提示 | GET | `/api/tasks/cron/suggestions` | `keyword` |
| 导出任务 | GET | `/api/tasks/export` | 触发下载 |

### 执行节点

| 功能 | 方法 | 路径 |
| --- | --- | --- |
| 节点列表 | GET | `/api/scheduler/nodes` |
| 节点指标 | GET | `/api/scheduler/nodes/metrics` |
| 心跳详情 | GET | `/api/scheduler/nodes/{nodeId}/heartbeat` |
| 手动下线 | POST | `/api/scheduler/nodes/{nodeId}/offline` |
| 分片重分配 | POST | `/api/scheduler/nodes/{nodeId}/rebalance` |

### 告警与配置

| 功能 | 方法 | 路径 |
| --- | --- | --- |
| 查询告警规则 | GET | `/api/alerts/rules` |
| 新增告警规则 | POST | `/api/alerts/rules` |
| 更新告警规则 | PUT | `/api/alerts/rules/{ruleId}` |
| 删除告警规则 | DELETE | `/api/alerts/rules/{ruleId}` |
| 切换启用 | PUT | `/api/alerts/rules/{ruleId}`（部分字段） |
| 通道联通测试 | POST | `/api/alerts/test` |

### 鉴权

| 功能 | 方法 | 路径 |
| --- | --- | --- |
| 登录 | POST | `/auth/login` |
| 刷新 Token | POST | `/auth/refresh` |
| 获取用户信息 | GET | `/auth/profile` |

## 环境切换

- 头部环境选择器驱动 `auth/changeEnv`，并透传 `env` 到 URL Query。
- 所有 API 请求携带 `X-SkyTask-Env`，后端可据此选择对应的配置/数据源。

## 后续拓展建议

1. 接入实际的 gRPC 执行节点注册信息，完善节点实时数据展示。
2. 将日志接口对接 Elasticsearch / ClickHouse，实现执行日志全文检索。
3. 结合权限中心（RBAC），对页面操作按钮进行权限粒度控制。
