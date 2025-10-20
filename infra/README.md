# SkyTask Infrastructure Stack

This folder contains helper assets for Stage 3 - Infrastructure Preparation. It provides a
containerized, local environment for the external systems that SkyTask integrates with.

## Prerequisites

- Docker Engine 20+ and docker-compose plugin.
- Optional: copy `.env.example` to `.env` in this directory to override defaults.

## Services

| Service              | Purpose                             | Default Endpoint            |
|----------------------|-------------------------------------|-----------------------------|
| Nacos Server         | Service discovery & config center   | `http://localhost:8848`     |
| Sentinel Dashboard   | Flow control / 熔断治理控制台             | `http://localhost:8858`     |
| ZooKeeper            | Kafka metadata coordination         | `localhost:2181`            |
| Kafka Broker         | Task log / 事件流 pipeline           | `localhost:9092`            |

> Sentinel dashboard credentials default to `sentinel/sentinel`. Override via the `.env`.

## Usage

```bash
cd infra
cp .env.example .env   # optional, only if you need to customise
docker compose up -d
```

After the containers are running:

- Verify Nacos: open `http://localhost:8848/nacos` (default account `nacos/nacos`).
- Verify Sentinel: open `http://localhost:8858/`.
- Verify Kafka: run `docker compose logs kafka` to ensure the broker started.

### Nacos 配置示例
- `config/scheduler-config.yaml` 提供了调度中心动态参数示例，建议 Data ID `skytask-scheduler.yaml`、Group `DEFAULT_GROUP`。
- 推送至 Nacos 后，可调用 `POST /actuator/refresh` 或依赖自动刷新机制应用最新配置。

## Shutdown and Cleanup

```bash
docker compose down
# Remove persisted volumes if you need a clean slate
rm -rf volumes/
```

Persisted data lives inside `infra/volumes/*`. Keep these directories if you want to retain
metadata between restarts.
