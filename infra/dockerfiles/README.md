# infra/dockerfiles/ — 服务镜像定义

## 技术栈
多阶段构建（multi-stage build）· python:3.11-slim · node:22-alpine

## 放什么代码

| 文件 | 基础镜像 | 用途 |
|---|---|---|
| `api.Dockerfile` | python:3.11-slim | FastAPI 网关 + Admin API |
| `dagster.Dockerfile` | python:3.11-slim | Dagster webserver + daemon |
| `dbt.Dockerfile` | python:3.11-slim | dbt 运行环境（也可复用 api 镜像） |
| `web.Dockerfile` | node:22-alpine → nginx:alpine | 前端构建 + 静态托管 |

> **为什么是 Python 3.11 而不是宿主机上的 3.14**：数据生态（dbt、Dagster、LightGBM、SHAP）
> 对 3.13+ 的支持尚不完整。容器内固定 3.11 可彻底规避版本地狱，且与宿主机 Python 完全解耦。

## 干什么事情
1. 统一 Python 版本，避免"本机能跑容器不能跑"
2. 多阶段构建把 node_modules 留在构建阶段，运行镜像只保留 dist
3. 所有镜像预装 `curl`，供健康检查使用
