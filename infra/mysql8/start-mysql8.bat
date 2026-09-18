@echo off
REM ============================================================
REM  Start MySQL 8.0.43 standalone instance (port 3306)
REM
REM  data dir : D:\mysql8\data
REM  isolated : original MySQL 5.7 lives in D:\mysql-5.7.32\data
REM  NOTE     : MySQL 5.7 service must stay stopped (same port)
REM ============================================================
setlocal enabledelayedexpansion

set "MYSQL_HOME=D:\mysql8"
set "MYSQLD=%MYSQL_HOME%\bin\mysqld.exe"
set "MYINI=%MYSQL_HOME%\my.ini"

echo ============================================
echo   Start MySQL 8.0.43  (port 3306)
echo ============================================
echo.

REM --- guard: is the legacy 5.7 Windows service holding the port? ---
REM "sc query" prints: STATE : 4 RUNNING  -> token 4 is the textual state
set "SVC_STATE="
for /f "tokens=4" %%s in ('sc query MySQL 2^>nul ^| findstr /i "STATE"') do set "SVC_STATE=%%s"
if /i "!SVC_STATE!"=="RUNNING" goto :conflict

REM --- already listening? then we are done ---
netstat -ano | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto :alreadyup

echo [1/2] Launching mysqld ...
start "MySQL8" /MIN "%MYSQLD%" --defaults-file="%MYINI%"

echo [2/2] Waiting for readiness ...
call :waitready
if not errorlevel 1 goto :started

echo.
echo [FAILED] Startup timeout. Check log:
echo          %MYSQL_HOME%\logs\error.log
exit /b 1

:started
echo.
echo   [OK] MySQL 8 started.
goto :showinfo

:alreadyup
echo [SKIP] Port 3306 already listening - MySQL 8 is up.
goto :showinfo

:conflict
echo [CONFLICT] Windows service "MySQL" (5.7) is RUNNING and owns port 3306.
echo            Run as Administrator:  net stop MySQL
echo.
exit /b 1

:showinfo
echo.
echo   ------------------------------------------------
echo     Host : 127.0.0.1
echo     Port : 3306
echo     User : root
echo     Pass : see MYSQL_PASSWORD in infra\.env
echo   ------------------------------------------------
echo   Use these values for a Navicat MySQL connection.
echo.
exit /b 0

REM ------------------------------------------------------------
REM  Poll port 3306 for up to 20 seconds.
REM  Returns 0 when listening, 1 on timeout.
REM
REM  Delay uses "ping -n 2" (about 1s), NOT "timeout":
REM  when this script is invoked from Git Bash / MSYS, the Unix
REM  "timeout" shadows Windows timeout.exe and fails on "/t".
REM ------------------------------------------------------------
:waitready
set /a _n=0
:waitloop
set /a _n+=1
ping -n 2 127.0.0.1 >nul 2>&1
netstat -ano | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 exit /b 0
if %_n% lss 20 goto :waitloop
exit /b 1
