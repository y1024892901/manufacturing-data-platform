"""Read-only MySQL metadata access; secrets stay in the child environment."""
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def configuration():
    config = {}
    path = ROOT / "infra/.env"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                if key.strip().startswith("MYSQL_"):
                    config[key.strip()] = value.strip().strip(chr(34)).strip(chr(39))
    config.update({key: value for key, value in os.environ.items() if key.startswith("MYSQL_")})
    return config

def query(sql):
    config = configuration()
    binary = str(Path(config.get("MYSQL_HOME", "D:/mysql8")) / "bin/mysql.exe")
    if not Path(binary).is_file():
        binary = shutil.which("mysql")
    if not binary:
        raise RuntimeError("MySQL client not found; configure MYSQL_HOME")
    env = os.environ.copy()
    env["MYSQL_PWD"] = config.get("MYSQL_PASSWORD", "")
    result = subprocess.run([binary, "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
                             "--host=" + config.get("MYSQL_HOST", "127.0.0.1"),
                             "--port=" + config.get("MYSQL_PORT", "3306"),
                             "--user=" + config.get("MYSQL_USER", "root"), "--execute", sql],
                            env=env, capture_output=True, encoding="utf-8", errors="replace", timeout=30)
    if result.returncode:
        raise RuntimeError("MySQL metadata query failed; check local instance and credentials")
    return [line.split("\t") for line in result.stdout.splitlines()]

def columns():
    rows = query("SELECT c.TABLE_SCHEMA,c.TABLE_NAME,c.COLUMN_NAME FROM information_schema.COLUMNS c "
                 "JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME "
                 "WHERE (LEFT(c.TABLE_SCHEMA,4) IN ('src_','mfg_')) "
                 "AND t.TABLE_TYPE='BASE TABLE' AND c.TABLE_NAME<>'flyway_schema_history' "
                 "ORDER BY c.TABLE_SCHEMA,c.TABLE_NAME,c.ORDINAL_POSITION")
    result = {}
    for database, table, column in rows:
        result.setdefault((database, table), set()).add(column)
    return result
