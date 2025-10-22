# SkyTask - 分布式任务调度系统

**企业级分布式任务调度与执行平台**

[功能特性](#-核心功能) • [快速开始](#-快速开始) • [架构设计](#-系统架构) • [生产应用](#-生产应用场景)

---

## 📖 项目简介

**SkyTask** 是一个功能完整的**分布式任务调度系统**，专为多节点环境下的定时任务与批处理任务设计。它提供了任务分片、依赖管理、失败重试、幂等保证、实时监控等企业级特性，并配备现代化的 Web 管理控制台。

类似于 XXL-Job、Elastic-Job、SchedulerX 等开源调度系统，但具有更完善的多租户隔离、权限管理和微服务架构支持。

### ✨ 核心特性

- 🎯 **灵活的任务调度** - 支持 Cron 表达式、一次性任务、固定频率任务
- 🔄 **分布式分片执行** - 大任务可拆分到多个节点并行执行
- 🔗 **任务依赖管理** - 支持复杂的任务依赖关系，可视化依赖图
- 🛡️ **高可用容错** - 失败自动重试、节点故障转移、执行幂等保证
- 👥 **多租户隔离** - 完善的租户级数据隔离和资源隔离
- 🔐 **细粒度权限控制** - 基于 RBAC 的权限管理，支持接口级权限
- 📊 **实时监控告警** - 执行趋势分析、性能指标监控、失败告警
- 🎨 **现代化 Web 控制台** - Vue 3 + Element Plus 构建的美观易用界面

---

## 🏗️ 系统架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端层 (Vue 3)                           │
│                    SkyTask Web Console                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   网关层 (Spring Cloud Gateway)                  │
│                      统一入口与路由                               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────┬──────────────┬──────────────┬──────────────────┐
│  调度核心     │   认证授权    │  管理API     │   服务注册中心    │
│  Scheduler   │   Auth       │  Admin       │   Eureka        │
│  (8081)      │              │              │   (8761)        │
└──────────────┴──────────────┴──────────────┴──────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      执行器节点 (Worker)                         │
│                   分布式任务执行引擎                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────┬──────────────┬──────────────┬──────────────────┐
│    MySQL     │    Redis     │    Kafka     │     Nacos       │
│   数据存储    │  分布式锁     │   消息队列    │   配置中心       │
└──────────────┴──────────────┴──────────────┴──────────────────┘
```

### 技术栈

**后端**
- **框架**: Spring Boot 2.7.18, Spring Cloud 2021.0.8
- **调度引擎**: Quartz 2.3.2
- **数据库**: MySQL 8.0 + Spring Data JPA
- **缓存**: Redis 6.0 + Redisson
- **服务治理**: Nacos 2.3.1, Eureka, Sentinel
- **消息队列**: Kafka 3.6
- **监控**: Micrometer + Prometheus
- **链路追踪**: Spring Cloud Sleuth + Zipkin

**前端**
- **框架**: Vue 3.4.27
- **UI 组件**: Element Plus 2.8.4
- **状态管理**: Vuex 4.1.0
- **路由**: Vue Router 4.4.5
- **图表**: ECharts 5.6.0
- **HTTP 客户端**: Axios 1.7.7
- **日期处理**: Day.js 1.11.13

---

## 🚀 核心功能

### 1. 任务生命周期管理

#### 📝 任务定义
- **多种任务类型**: Cron 定时、一次性、固定频率
- **多种执行器**: HTTP 回调、Shell 脚本、Spring Bean、gRPC
- **任务参数**: 支持 JSON 格式的动态参数配置
- **任务分组**: 按业务域分组管理（数据报表、风控、营销等）

#### ⚙️ 调度配置
- **Cron 表达式**: 支持秒级精度的 Cron 表达式
- **时区支持**: 支持多时区配置
- **并发策略**: 并行、串行、丢弃（防止任务堆积）
- **超时控制**: 可配置任务执行超时时间

### 2. 分布式执行

#### 🔀 任务分片
```
大任务（10000 条数据）
    ↓
拆分为 10 个分片
    ↓
分配到 10 个节点并行处理
    ↓
分片 0: 处理 0-999
分片 1: 处理 1000-1999
...
分片 9: 处理 9000-9999
```

#### 🎲 负载均衡策略
- **轮询（Round Robin）**: 请求均匀分配到各节点
- **一致性哈希（Consistent Hash）**: 同一任务固定到同一节点
- **分片广播（Sharding）**: 所有节点接收任务，根据分片索引处理对应数据
- **固定节点（Fixed Node）**: 指定特定节点执行

### 3. 容错与重试

#### 🔁 智能重试
- **重试策略**:
  - **固定间隔**: 失败后固定时间间隔重试
  - **指数退避**: 重试间隔逐渐增加（60s → 120s → 240s...）
  - **不重试**: 失败即终止
- **最大重试次数**: 可配置 0-10 次
- **失败告警**: 重试失败后触发告警通知

#### 🛡️ 幂等性保障
- **Redis 分布式锁**: 使用 Redisson 实现，避免任务重复执行
- **幂等键机制**: 支持自定义幂等键（如 `${bizId}-${yyyyMMddHHmm}`）
- **数据库状态机**: 通过数据库状态流转保证操作幂等
- **执行日志验证**: 记录详细的执行历史，可追溯

### 4. 任务依赖

#### 🔗 依赖关系
- **上游依赖**: 任务 A 依赖任务 B，B 成功后才执行 A
- **依赖图可视化**: Web 控制台展示任务依赖关系图
- **依赖检查**: 自动检测并阻止循环依赖

### 5. 监控与日志

#### 📈 实时监控
- **执行统计**: 成功率、失败率、平均耗时等指标
- **趋势分析**: 可视化图表展示 24h/7d/30d 执行趋势
- **节点监控**: 各执行器节点的 CPU、内存、运行任务数
- **性能指标**: 基于 Micrometer 的 Prometheus 指标

#### 📋 执行日志
- **详细记录**: 每次执行的触发时间、执行节点、耗时、结果
- **日志查询**: 按任务、时间范围查询执行历史
- **链路追踪**: 集成 Sleuth，支持分布式链路追踪

### 6. 告警机制

#### 🚨 多种告警规则
- **失败告警**: 任务执行失败时触发
- **超时告警**: 任务执行超时触发
- **成功率告警**: 成功率低于阈值时触发
- **自动降级**: 失败率达到阈值时自动禁用任务

### 7. 多租户与权限

#### 👥 多租户隔离
- **租户级数据隔离**: 每个租户拥有独立的数据空间
- **租户上下文传播**: 请求级别的租户上下文管理
- **资源配额**: 支持按租户分配资源配额

#### 🔐 权限管理（RBAC）
- **用户管理**: 用户账号、密码、状态管理
- **角色管理**: 自定义角色，灵活配置权限
- **权限管理**: 接口级权限控制
  - `task:read` - 查看任务
  - `task:write` - 创建/编辑任务
  - `task:trigger` - 触发任务执行
  - `node:read` - 查看节点信息
  - `config:write` - 修改系统配置
- **审计日志**: 记录所有操作日志，可追溯

---

## 💼 生产应用场景

SkyTask 适用于各种需要定时任务和批处理的生产环境：

### 1. 数据处理场景

#### 📊 数据报表生成
```
场景: 每日凌晨 2 点生成前一天的业务报表
任务配置:
- Cron: 0 0 2 * * ?（每天凌晨 2 点）
- 执行器: HTTP 回调报表服务
- 分片: 10 个分片并行处理不同业务线
- 重试: 失败后每隔 5 分钟重试，最多 3 次
优势: 大幅缩短报表生成时间，提高处理效率
```

#### 🔄 数据同步任务
```
场景: 每小时从数据源同步数据到数据仓库
任务配置:
- Cron: 0 0 * * * ?（每小时执行）
- 执行器: Shell 脚本调用 ETL 工具
- 依赖: 依赖数据清洗任务完成
- 幂等: 基于业务日期的幂等键
优势: 保证数据一致性，支持断点续传
```

### 2. 业务处理场景

#### 🎯 定时营销活动
```
场景: 每天 10 点发送营销短信/邮件
任务配置:
- Cron: 0 0 10 * * ?
- 执行器: HTTP 调用营销服务
- 分片: 100 个分片，每个分片处理 10000 用户
优势: 百万级用户并行处理，快速完成营销任务
```

#### 🧹 数据清理任务
```
场景: 每月 1 号清理过期数据
任务配置:
- Cron: 0 0 0 1 * ?（每月 1 号凌晨）
- 执行器: SQL 脚本
- 超时: 3600 秒
优势: 自动化数据维护，释放存储空间
```

### 3. 系统维护场景

#### 🔄 缓存预热
```
场景: 每天凌晨 6 点预热 Redis 缓存
任务配置:
- Cron: 0 0 6 * * ?
- 执行器: Spring Bean
- 路由: 一致性哈希（固定节点）
优势: 提升白天业务响应速度
```

#### 📦 日志归档
```
场景: 每周日归档日志文件
任务配置:
- Cron: 0 0 0 ? * SUN（每周日凌晨）
- 执行器: Shell 脚本
- Handler: /opt/scripts/log-archive.sh
优势: 自动归档，防止磁盘占满
```

### 4. 复杂业务编排

#### 🔗 依赖任务链
```
数据采集任务
    ↓
数据清洗任务（依赖采集完成）
    ↓
数据分析任务（依赖清洗完成）
    ↓
报表生成任务（依赖分析完成）
```

**适用行业**:
- 💰 **金融**: 账单生成、对账、风控数据处理
- 🛒 **电商**: 订单统计、库存同步、推荐算法训练
- 📱 **互联网**: 用户行为分析、日志处理、缓存更新
- 🏭 **制造业**: 生产计划调度、设备监控数据采集
- 🏥 **医疗**: 报告生成、数据备份、预约提醒

---

## 📦 快速开始

### 环境要求

- **JDK**: 13+
- **Maven**: 3.6+
- **Node.js**: 14+
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **Nacos**: 2.3.1（可选）

### 1. 准备基础环境

#### 方式一：使用 Docker Compose（推荐）

```bash
# 进入基础设施目录
cd infra

# 启动 MySQL, Redis, Nacos, Kafka 等基础服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

#### 方式二：手动安装

**安装 MySQL**
```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE skytask CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 导入表结构和初始数据
mysql -u root -p skytask < data.sql
```

**启动 Redis**
```bash
redis-server --port 6379
# 设置密码（在配置文件中）
redis-cli
> CONFIG SET requirepass "123456"
```

### 2. 初始化数据库

```bash
# 导入数据库脚本
mysql -u root -p skytask < data.sql
```

数据库脚本会创建以下内容：
- ✅ 租户和用户表
- ✅ 任务相关表（task, task_instance, task_log 等）
- ✅ 权限管理表（user, role, permission 等）
- ✅ 演示数据（租户 tenant-alpha，用户 ops-admin）

### 3. 启动后端服务

#### 配置文件

修改 `skytask-scheduler/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/skytask?useUnicode=true&characterEncoding=utf-8
    username: root
    password: 你的密码
  redis:
    host: localhost
    port: 6379
    password: 123456
```

#### 启动服务（按顺序）

**1. 启动 Eureka 服务注册中心**
```bash
cd skytask-eureka
mvn spring-boot:run
```
访问: http://localhost:8761

**2. 启动调度核心服务**
```bash
cd skytask-scheduler
mvn clean package -DskipTests
mvn spring-boot:run
```
访问: http://localhost:8081

**3. 启动网关服务**
```bash
cd skytask-gateway
mvn spring-boot:run
```
访问: http://localhost:8080

**4. 启动 Worker 节点（可选）**
```bash
cd skytask-worker
mvn spring-boot:run
```

### 4. 启动前端

```bash
# 进入前端目录
cd skytask-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run serve

# 或在 Windows 下使用
start.bat
```

访问: http://localhost:8080

### 5. 登录系统

打开浏览器访问 `http://localhost:8080`

**演示账号**:
- 租户: `tenant-alpha`
- 用户名: `ops-admin`
- 密码: `admin123`

---

## 📖 使用指南

### 创建你的第一个任务

1. **登录系统** → 使用演示账号登录
2. **进入任务中心** → 点击左侧菜单"任务中心"
3. **新建任务** → 点击右上角"新建任务"按钮
4. **配置任务**:
   ```
   任务名称: daily-report
   任务类型: Cron定时
   执行方式: HTTP
   Handler: http://your-service/api/generate-report
   Cron表达式: 0 0 2 * * ?  (每天凌晨2点)
   负责人: alice
   ```
5. **保存并启用** → 点击"保存"按钮
6. **查看执行记录** → 在任务详情页查看执行历史

### 手动触发任务

```
任务详情页 → 点击"立即运行"按钮 → 查看执行记录
```

### 查看监控数据

```
总览监控 → 查看执行趋势、Top 告警任务、节点健康度
```

---

## 🗂️ 项目结构

```
SkyTask/
├── skytask-scheduler/          # 调度核心服务
│   ├── src/main/java/
│   │   └── com/skytask/
│   │       ├── scheduler/      # Quartz 调度逻辑
│   │       ├── service/        # 业务服务层
│   │       ├── controller/     # REST API
│   │       ├── executor/       # 任务执行器
│   │       └── repository/     # 数据访问层
│   └── src/main/resources/
│       └── application.yml     # 配置文件
│
├── skytask-worker/             # Worker 执行器节点
├── skytask-gateway/            # API 网关
├── skytask-eureka/             # 服务注册中心
├── skytask-auth/               # 认证授权服务
├── skytask-admin-api/          # 管理 API
│
├── skytask-frontend/           # Vue 3 前端
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   ├── components/         # 通用组件
│   │   ├── api/                # API 接口
│   │   ├── store/              # Vuex 状态管理
│   │   └── router/             # 路由配置
│   └── package.json
│
├── infra/                      # 基础设施
│   ├── docker-compose.yml      # Docker Compose 配置
│   └── config/                 # Nacos 配置文件
│
└── data.sql                    # 数据库初始化脚本
```

---

## 🔧 配置说明

### 核心配置项

**调度器配置** (`application.yml`)

```yaml
skytask:
  scheduler:
    retry:
      default-policy: EXP_BACKOFF    # 默认重试策略
      max-attempts: 3                 # 最大重试次数
      base-backoff-ms: 60000         # 基础退避时间
    alert:
      failure-rate-threshold: 30      # 失败率阈值(%)
      notify-channels: EMAIL          # 告警渠道
    nodes:
      heartbeat-timeout-seconds: 90   # 节点心跳超时
```

### 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 8080 | Vue 前端界面 |
| Gateway | 8080 | API 网关 |
| Scheduler | 8081 | 调度核心服务 |
| Eureka | 8761 | 服务注册中心 |
| Nacos | 8848 | 配置中心 |
| Redis | 6379 | 缓存服务 |
| MySQL | 3306 | 数据库 |
| Kafka | 9092 | 消息队列 |

---

## 🎯 API 文档

### 任务管理 API

#### 获取任务列表
```http
GET /api/tasks?page=1&size=10&keyword=report
```

#### 创建任务
```http
POST /api/tasks
Content-Type: application/json

{
  "name": "daily-report",
  "type": "CRON",
  "executorType": "HTTP",
  "handler": "http://service/api/task",
  "cronExpr": "0 0 2 * * ?",
  "routeStrategy": "ROUND_ROBIN",
  "retryPolicy": "EXP_BACKOFF",
  "maxRetry": 3,
  "timeout": 300,
  "owner": "alice"
}
```

#### 触发任务
```http
POST /api/tasks/{taskId}/trigger
Content-Type: application/json

{
  "manual": true,
  "operator": "admin"
}
```

#### 查询执行记录
```http
GET /api/tasks/{taskId}/records?page=1&size=20
```

---

## 🛠️ 开发指南

### 自定义任务执行器

#### HTTP 执行器示例

你的服务需要提供一个 HTTP 接口接收任务执行请求：

```java
@RestController
@RequestMapping("/api/tasks")
public class TaskExecutionController {
    
    @PostMapping("/execute")
    public WorkerExecutionResult execute(@RequestBody WorkerExecuteRequest request) {
        try {
            // 执行业务逻辑
            String result = processData(request.getParameters());
            
            return WorkerExecutionResult.builder()
                .taskId(request.getTaskId())
                .instanceId(request.getInstanceId())
                .status("SUCCESS")
                .message(result)
                .durationMillis(1000L)
                .build();
        } catch (Exception e) {
            return WorkerExecutionResult.builder()
                .taskId(request.getTaskId())
                .instanceId(request.getInstanceId())
                .status("FAILED")
                .message(e.getMessage())
                .build();
        }
    }
}
```

#### Spring Bean 执行器示例

```java
@Component("myTaskHandler")
public class MyTaskHandler {
    
    public void execute(Map<String, Object> params) {
        // 执行任务逻辑
        String reportType = (String) params.get("reportType");
        // ...
    }
}
```

配置任务时，Handler 填写: `myTaskHandler`

#### Shell 执行器示例

```bash
#!/bin/bash
# /opt/scripts/backup.sh

# 接收参数
BACKUP_PATH=$1
DATE=$(date +%Y%m%d)

# 执行备份
tar -czf "${BACKUP_PATH}/backup_${DATE}.tar.gz" /data/

echo "Backup completed: backup_${DATE}.tar.gz"
```

配置任务时，Handler 填写: `/opt/scripts/backup.sh /backup`

---

## 📊 数据库设计

### 核心表

| 表名 | 说明 |
|------|------|
| `tenant` | 租户表 |
| `user` | 用户表 |
| `role` | 角色表 |
| `permission` | 权限表 |
| `task` | 任务定义表 |
| `task_instance` | 任务执行实例表 |
| `task_log` | 任务执行日志表 |
| `task_dependency` | 任务依赖关系表 |
| `executor_node` | 执行器节点表 |
| `task_retry` | 重试记录表 |

详见 `data.sql` 文件。

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

### 开发流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

### 代码规范

- 后端遵循阿里巴巴 Java 开发手册
- 前端遵循 Vue 3 官方风格指南
- 提交信息遵循 Conventional Commits 规范

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 🙏 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Quartz Scheduler](http://www.quartz-scheduler.org/)
- [Vue.js](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [Redisson](https://redisson.org/)
- [Nacos](https://nacos.io/)

---

## 🗺️ 路线图

- [x] 基础调度功能
- [x] 分布式分片执行
- [x] 任务依赖管理
- [x] 多租户支持
- [x] Web 控制台
- [ ] 可视化任务编排
- [ ] 工作流引擎(根据具体业务进行扩展)
- [ ] 更多执行器类型（Python、Go 等）

---

<div align="center">
**如果这个项目对你有帮助，请给个 ⭐ Star 支持一下！**

Made with ❤️ by SkyTask Team

</div>
