# infra/configs/ — 服务配置

## 技术栈
YAML / TOML / INI

## 放什么代码

| 文件 | 服务的配置 | 关键内容 |
|---|---|---|
| `postgresql.conf.snippet` | PostgreSQL | `shared_buffers`、`work_mem`、`max_connections` 调优，适配演示机内存 |
| `dagster.yaml` | Dagster | 实例存储、调度器、运行队列、并发上限 |
| `dbt_profiles.yml` | dbt | 连接 `warehouse` 库，四层 schema 映射，线程数 |
| `nginx.conf` | 前端静态托管 | SPA history 路由回退、后端 API 反向代理 |

## 干什么事情
1. 把「能跑」的魔数集中管理，不散落在代码里
2. `dbt_profiles.yml` 用环境变量插值连接串，本地开发与容器运行共用一份
3. 演示机内存有限，`postgresql.conf.snippet` 按 4~8G 内存档位给出保守参数
