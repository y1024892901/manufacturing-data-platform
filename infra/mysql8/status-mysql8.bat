@echo off
REM ============================================================
REM  Show status of both MySQL instances
REM  Password is read from infra\.env  (never hard-coded here)
REM ============================================================
setlocal enabledelayedexpansion

set "MYSQL_HOME=D:\mysql8"
set "MYSQL=%MYSQL_HOME%\bin\mysql.exe"
set "ENVFILE=%~dp0..\.env"

echo ============================================
echo   MySQL instance status
echo ============================================
echo.

REM --- legacy 5.7 ---
REM sc query outputs: "        STATE              : 1  STOPPED"
REM token 3 = numeric state, token 4 = textual state
echo [Legacy MySQL 5.7 - D:\mysql-5.7.32]
set "S57=NOT INSTALLED"
for /f "tokens=4" %%s in ('sc query MySQL 2^>nul ^| findstr /i "STATE"') do set "S57=%%s"
echo     Service : !S57!
echo     DataDir : D:\mysql-5.7.32\data  (18 databases, NOT touched by this project)
echo.

REM --- project MySQL 8 ---
echo [Project MySQL 8.0.43 - D:\mysql8]
netstat -ano | findstr ":3306 " | findstr "LISTENING" >nul 2>&1
if %errorlevel%==0 (
    echo     Service : RUNNING
    echo     Port    : 3306

    set "DBPWD="
    if exist "%ENVFILE%" (
        for /f "usebackq tokens=1,* delims==" %%a in ("%ENVFILE%") do (
            if /i "%%a"=="MYSQL_PASSWORD" set "DBPWD=%%b"
        )
    )

    if not "!DBPWD!"=="" (
        echo.
        echo     --- instance info ---
        "%MYSQL%" -uroot -P3306 -h127.0.0.1 --password="!DBPWD!" -e "SELECT VERSION() AS version, @@port AS port, @@character_set_server AS charset; SHOW DATABASES;" 2>nul
    ) else (
        echo     ^(MYSQL_PASSWORD not found in infra\.env - skipping database list^)
    )
    set "DBPWD="
) else (
    echo     Service : STOPPED
    echo     Start   : infra\mysql8\start-mysql8.bat
)

echo.
echo ============================================
endlocal
