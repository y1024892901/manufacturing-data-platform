# infra/mysql8/ — 目前进展

> 截至 2026-09-22 · 对应整体计划见 [PLAN.md](PLAN.md)

## 一句话结论

三个管理脚本**全部已实现且可直接使用**（启动/停止/查状态，共 193 行批处理），实例本身已完成安装与初始化、`root` 口令已写入 `infra/.env`，脚本层面功能完整；缺口在于**未安装为系统服务**（需人工记得启动）与**缺少自动化安装脚本**（换机只能照 README 手抄 6 步）。

## 文件清单

| 文件 | 行数 | 说明 |
|---|---|---|
| `start-mysql8.bat` | 85 | 启动 MySQL 8.0.43；**先检测 5.7 服务是否抢占 3306**，再检测端口是否已监听，最后 `start /MIN` 拉起 `mysqld` 并轮询最多 20 秒确认就绪。失败会指向 `D:\mysql8\logs\error.log` |
| `stop-mysql8.bat` | 52 | 从 `..\.env` 读 `MYSQL_PASSWORD` 后 `mysqladmin shutdown` 优雅关闭；等约 3 秒若端口仍被监听，则 `netstat` 找出 PID 并 `taskkill /F` |
| `status-mysql8.bat` | 56 | 打印 5.7 服务状态（`sc query MySQL`）与 8.0 的监听状态；8.0 在跑时进一步连库输出 `VERSION()` / `@@port` / `@@character_set_server` 与 `SHOW DATABASES` |
| `README.md` | 112 | 实例档案：为什么是 8.0 而非 5.7、目录与端口表、与 5.7 的隔离关系图、初始化记录（6 步）、Navicat 连接参数 |

> 本目录**没有** `my.ini`、数据目录或二进制文件 —— 它们都在仓库之外的 `D:\mysql8\`。

## 关键机制 / 使用方式

### 日常使用

```bat
infra\mysql8\start-mysql8.bat      :: 演示前拉起
infra\mysql8\status-mysql8.bat     :: 确认活着 + 看库列表
infra\mysql8\stop-mysql8.bat       :: 收工关闭
```

三个脚本的退出码有语义：`start` 成功/已在线返回 `0`，抢端口或超时返回 `1` —— 可被上层脚本串联判断。

### 为什么必须是 8.0.43 而不是已有的 5.7.32

| 数仓必需能力 | MySQL 5.7 | MySQL 8.0.43 |
|---|---|---|
| 窗口函数（账龄快照、排名、去重取最新） | ❌ | ✅ |
| CTE / 递归 CTE（BOM 多层展开） | ❌ | ✅ |
| `JSON_TABLE`（报表结构展开） | ❌ | ✅ |
| `utf8mb4_0900_ai_ci`（中文排序准确） | ❌ | ✅ |
| 安全更新 | 2023-10 已 EOL | ✅ |

实测 5.7 对 CTE 直接报 `ERROR 1064` 语法错误 —— 不是配置问题，是版本能力缺失。

### 端口冲突是本目录最需要防的事故

```
原 MySQL 5.7.32                 本项目 MySQL 8.0.43
D:\mysql-5.7.32\data            D:\mysql8\data
Windows 服务: MySQL             Windows 服务: 无（进程）
端口: 3306                      端口: 3306
★ 数据目录物理隔离，互不可见
★ 但端口相同 —— 5.7 服务必须保持停止
```

`start-mysql8.bat` 用 `sc query MySQL` 取第 4 个 token 判断服务状态，若为 `RUNNING` 直接 `goto :conflict` 并提示用管理员执行 `net stop MySQL`，**不会盲目拉起导致失败**。建议把 5.7 改为手动启动：

```powershell
Set-Service -Name MySQL -StartupType Manual    # 管理员身份
```

### 两个实现上的细节（都有注释说明原因）

1. **等待用 `ping -n` 而不是 `timeout`** —— 脚本会被从 Git Bash / MSYS 调用，Unix 的 `timeout` 会遮蔽 Windows `timeout.exe`，导致 `/t` 参数报错。故 `ping -n 2`（≈1 秒）做延时、`ping -n 4`（≈3 秒）做关停等待。
2. **口令读取方式** —— `for /f "usebackq tokens=1,* delims==" %%a in ("%ENVFILE%")` 按行解析 `infra\.env` 找 `MYSQL_PASSWORD`；找不到时 `stop` 会明确报 `[ERROR] MYSQL_PASSWORD not found` 并返回 `1`，而非拿空口令去连。

### 实例参数（摘要）

| 项 | 值 |
|---|---|
| 安装目录 / 数据目录 | `D:\mysql8` / `D:\mysql8\data` |
| 配置文件 / 错误日志 | `D:\mysql8\my.ini` / `D:\mysql8\logs\error.log` |
| 慢查询日志 | `D:\mysql8\logs\slow.log` |
| 端口 / 字符集 | `3306` / `utf8mb4` + `utf8mb4_0900_ai_ci` |
| 连接（Navicat） | `127.0.0.1:3306`，用户 `root`，口令见 `infra\.env` |

## 未实现 / 缺口

| # | 缺口 | 影响 | 说明 |
|---|---|---|---|
| 1 | **未安装为 Windows 服务** | 中 | 实例是普通进程，机器重启后不会自动拉起；演示前必须人工执行 `start-mysql8.bat`。这是有意选择（避免抢端口），但需要流程上记住 |
| 2 | **无自动安装脚本** | 中 | `README.md` 的 6 步初始化（下载 244MB zip → 解压 → 写 `my.ini` → `--initialize-insecure` → 启动 → `ALTER USER` 设口令+开放远程）是**文档而非脚本**，换机需人工照抄，且其中有一步需要手工填口令 |
| 3 | `MYSQL_HOME` 环境变量形同虚设 | 低 | `infra/.env` 里有 `MYSQL_HOME=D:\mysql8`，但三个 .bat 各自**硬编码** `set "MYSQL_HOME=D:\mysql8"`，并不读取它 —— 换安装路径要改 3 个脚本 |
| 4 | 版本字符串不一致 | 低 | `README.md` 章标题写「MySQL 8.0.43」，但 `start-mysql8.bat` 的提示文案只写「Start MySQL 8.0.43 (port 3306)」而 `status` 输出的标题写「Project MySQL 8.0.43」；实例实际版本以 `SELECT VERSION()` 为准 |
| 5 | 库数说法不一致 | 低 | `README.md` 写「本项目 **20** 个库」，`status-mysql8.bat` 注释里 5.7 的库数写「18 databases」。实测项目库为 **18**（见 [../db-init/STATUS.md](../db-init/STATUS.md)）；`SHOW DATABASES` 还会额外带出 `information_schema` 等系统库，故实际输出行数会更多 |
| 6 | 无健康检查/告警 | 低 | 没有「实例挂了自动重启」或「磁盘/连接数超限告警」；`ops/monitoring/` 尚未实现 |
| 7 | 无备份 | 中 | 数据全在 `D:\mysql8\data`，无定期 dump；`ops/backup/` 尚未实现 |
| 8 | 远程访问面较大 | 中 | 初始化记录中创建了 `root'@'%'` 并 `GRANT ALL PRIVILEGES ON *.*`（历史遗留：早期为容器内 Dagster/api 经 `host.docker.internal` 访问所建，容器方案已废弃，该账号可考虑收回）。演示环境下可接受，但该账号对整个局域网开放，**不应照搬到任何非演示环境** |
