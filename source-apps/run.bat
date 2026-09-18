@echo off
REM ============================================================
REM  启动源系统服务（10 个系统单进程，端口 8080）
REM
REM  从 infra\.env 读取数据库口令与 JWT 密钥注入环境变量，
REM  应用配置中不包含任何明文凭据。
REM
REM  用法:
REM    run.bat              启动（前需先启动 MySQL 8）
REM    run.bat -DskipTests  附加 Maven 参数
REM ============================================================
setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0.."
set "ENVFILE=%PROJECT_ROOT%\infra\.env"
set "JAVA_HOME=D:\jdk-17.0.20"
set "MVN=D:\apache-maven-3.9.9\bin\mvn.cmd"

echo ============================================
echo   制造业数据平台 - 源系统
echo ============================================
echo.

REM ---------- 1. 检查 .env ----------
if not exist "%ENVFILE%" (
    echo [ERROR] 未找到 %ENVFILE%
    echo         请从 infra\.env.example 复制并填写数据库口令。
    exit /b 1
)

REM ---------- 2. 读取环境变量 ----------
for /f "usebackq tokens=1,* delims==" %%a in ("%ENVFILE%") do (
    set "k=%%a"
    set "v=%%b"
    REM 跳过注释与空行
    if not "!k!"=="" if not "!k:~0,1!"=="#" (
        set "!k!=!v!"
    )
)

if "%MYSQL_PASSWORD%"=="" (
    echo [ERROR] infra\.env 中缺少 MYSQL_PASSWORD
    exit /b 1
)
if "%JWT_SECRET%"=="" (
    echo [ERROR] infra\.env 中缺少 JWT_SECRET
    exit /b 1
)

REM ---------- 3. 检查 MySQL ----------
netstat -ano | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 3306 端口无监听，MySQL 8 未启动。
    echo         请先运行: infra\mysql8\start-mysql8.bat
    exit /b 1
)
echo   [OK] MySQL 8 已就绪 (127.0.0.1:%MYSQL_PORT%)

REM ---------- 4. 检查 Java ----------
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] 未找到 JDK: %JAVA_HOME%
    exit /b 1
)
echo   [OK] JDK %JAVA_HOME%

echo.
echo   ------------------------------------------------
echo     服务地址    http://localhost:8080
echo     接口文档    http://localhost:8080/swagger-ui.html
echo     演示账号    http://localhost:8080/api/auth/demo-accounts
echo   ------------------------------------------------
echo   按 Ctrl+C 停止
echo.

REM ---------- 5. 启动 ----------
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
call "%MVN%" -q -pl bootstrap -am spring-boot:run %*

endlocal
