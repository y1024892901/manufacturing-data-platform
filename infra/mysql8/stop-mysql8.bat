@echo off
REM ============================================================
REM  Stop MySQL 8.0.43 standalone instance (port 3306)
REM  Password is read from infra\.env  (never hard-coded here)
REM ============================================================
setlocal enabledelayedexpansion

set "MYSQL_HOME=D:\mysql8"
set "MYSQLADMIN=%MYSQL_HOME%\bin\mysqladmin.exe"
set "ENVFILE=%~dp0..\.env"

echo ============================================
echo   Stop MySQL 8.0.43  (port 3306)
echo ============================================
echo.

REM --- read password from .env ---
set "DBPWD="
if exist "%ENVFILE%" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%ENVFILE%") do (
        if /i "%%a"=="MYSQL_PASSWORD" set "DBPWD=%%b"
    )
)

if "%DBPWD%"=="" (
    echo [ERROR] MYSQL_PASSWORD not found in:
    echo         %ENVFILE%
    exit /b 1
)

REM --- graceful shutdown ---
"%MYSQLADMIN%" -uroot -P3306 -h127.0.0.1 --password="%DBPWD%" shutdown 2>nul
set "DBPWD="

REM Wait ~3s for the port to be released.
REM Uses "ping -n 4" instead of "timeout": when invoked from Git Bash,
REM the Unix "timeout" shadows Windows timeout.exe and fails on "/t".
ping -n 4 127.0.0.1 >nul 2>&1

netstat -ano | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo     Graceful shutdown did not take effect, killing process ...
    for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":3306 " ^| findstr "LISTENING"') do (
        taskkill /F /PID %%p >nul 2>&1
        echo     Killed PID %%p
    )
) else (
    echo     [OK] MySQL 8 stopped.
)

echo.
endlocal
