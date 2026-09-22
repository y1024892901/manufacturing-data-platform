# infra/mysql8/ — MySQL 8.0.43 独立实例管理

## 技术栈
MySQL Community Server **8.0.43**（免安装 ZIP 版，非安装包）· Windows 批处理脚本

## 为什么是独立实例

本机原本已有 **MySQL 5.7.32**（`D:\mysql-5.7.32`），但它**撑不住这个项目**：

| 数仓必需能力 | MySQL 5.7 | MySQL 8.0.43 |
|---|---|---|
| 窗口函数（账龄快照、排名、去重取最新） | ❌ | ✅ |
| CTE / 递归 CTE（BOM 多层展开） | ❌ | ✅ |
| `JSON_TABLE`（报表结构展开） | ❌ | ✅ |
| `utf8mb4_0900_ai_ci`（中文排序准确） | ❌ | ✅ |
| 安全更新 | 2023-10 已 EOL | ✅ |

实测 5.7 对 CTE 直接报 `ERROR 1064` 语法错误，不是配置问题，是版本能力缺失。

**所以：另装一个 8.0.43，与原 5.7 完全隔离，互不干扰。**

## 目录与端口

| 项 | 值 |
|---|---|
| 安装目录 | `D:\mysql8` |
| 数据目录 | `D:\mysql8\data` |
| 配置文件 | `D:\mysql8\my.ini` |
| 错误日志 | `D:\mysql8\logs\error.log` |
| 慢查询日志 | `D:\mysql8\logs\slow.log` |
| **端口** | **3306** |
| 字符集 | `utf8mb4` / `utf8mb4_0900_ai_ci` |

## 与原 MySQL 5.7 的隔离关系

```
┌──────────────────────────────────────────────────────────┐
│  原 MySQL 5.7.32                   本项目 MySQL 8.0.43   │
│  D:\mysql-5.7.32\data              D:\mysql8\data        │
│  Windows 服务: MySQL               Windows 服务: 无（进程）│
│  端口: 3306（须停止）               端口: 3306（本项目用）  │
│  18 个业务库（book_store 等）        本项目 18 个库         │
│                                                            │
│  ★ 两个数据目录物理隔离，互不可见                          │
│  ★ 但端口相同 —— 原 5.7 服务必须保持停止                   │
└──────────────────────────────────────────────────────────┘
```

> **重要**：原 5.7 是 Windows 服务，默认可能是「自动启动」。若开机后它先占用了 3306，
> 本项目的 MySQL 8 就起不来。建议将 5.7 服务改为**手动启动**：
>
> ```powershell
> # 管理员身份执行
> Set-Service -Name MySQL -StartupType Manual
> ```

## 三个管理脚本

| 脚本 | 用途 |
|---|---|
| `start-mysql8.bat` | 启动实例（**会自动检查 5.7 是否抢端口**） |
| `stop-mysql8.bat` | 优雅关闭（`mysqladmin shutdown`，失败才强制终止） |
| `status-mysql8.bat` | 查看两个实例的状态与库列表 |

**密码从 `infra/.env` 的 `MYSQL_PASSWORD` 读取**，脚本中不出现明文。
`infra/.env` 已被 `.gitignore` 排除，不会入库。

## 初始化记录（已完成，无需重做）

本实例的建立过程（供换机复现参考）：

```bash
# 1. 下载（官方归档源，244MB）
curl -o mysql-8.0.43-winx64.zip \
  https://cdn.mysql.com/archives/mysql-8.0/mysql-8.0.43-winx64.zip

# 2. 解压到 D:\mysql8

# 3. 写 my.ini（见 D:\mysql8\my.ini）

# 4. 初始化数据目录（生成空密码 root）
D:\mysql8\bin\mysqld.exe --defaults-file=D:/mysql8/my.ini --initialize-insecure --console

# 5. 启动
D:\mysql8\bin\mysqld.exe --defaults-file=D:/mysql8/my.ini

# 6. 设置密码并开放远程
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '<见 .env>';
CREATE USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '<见 .env>';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
```

## Navicat 连接配置

| 项 | 值 |
|---|---|
| 连接名 | 制造业数据平台（任意） |
| 主机 | `127.0.0.1` |
| 端口 | `3306` |
| 用户名 | `root` |
| 密码 | 见 `infra/.env` |

连上后能看到本项目的 18 个库（`src_*` / `mfg_*`）。

> 若同时想管理原 5.7 的 18 个库，需先启动 5.7 服务并临时改端口，**不建议在演示期间操作**。

## 干什么事情

1. 为本项目提供**源系统数据库**（10 个 `src_*` 库，模拟 MDM 与 9 类业务系统）
2. 提供**数仓四层库**（`mfg_ods` / `mfg_dwd` / `mfg_dws` / `mfg_ads`）
3. 提供**平台库**（`mfg_meta` 元数据 / `mfg_auth` 权限与审批 / `mfg_app` 向量与会话 / `mfg_ops` 运维日志）
4. 演示前用 `start-mysql8.bat` 一键拉起，收工用 `stop-mysql8.bat` 关闭
