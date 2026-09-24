#!/usr/bin/env python
"""Run the rollback-only inbox/QMS smoke test after mvn package (Java 17+)."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--java", default=str(Path(os.environ["JAVA_HOME"]) / "bin/java.exe") if os.environ.get("JAVA_HOME") else shutil.which("java"))
    args = ap.parse_args()
    if not args.java:
        raise SystemExit("Java 17+ is required; set JAVA_HOME or pass --java.")
    env = os.environ.copy()
    config = ROOT / "infra/.env"
    if config.exists():
        for line in config.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                if key.strip().startswith("MYSQL_"):
                    env.setdefault(key.strip(), value.strip().strip(chr(34)).strip(chr(39)))
    if not env.get("MYSQL_PASSWORD"):
        raise SystemExit("Configure MYSQL_PASSWORD in the environment or infra/.env.")
    jar = ROOT / "source-apps/bootstrap/target/app-bootstrap-1.0.0.jar"
    if not jar.exists():
        raise SystemExit("Build source-apps with mvn package first.")
    with tempfile.TemporaryDirectory(prefix="mfg-smoke-") as temp:
        libs = Path(temp)
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith("BOOT-INF/lib/") and name.endswith(".jar"):
                    (libs / Path(name).name).write_bytes(archive.read(name))
        cp = os.pathsep.join([str(ROOT / "source-apps/shared/common/target/classes"),
                              str(ROOT / "source-apps/qms/target/classes"), str(libs / "*")])
        result = subprocess.run([args.java, "-cp", cp, str(ROOT / "ops/tests/BusinessEventDatabaseSmoke.java")],
                                env=env, capture_output=True, encoding="utf-8", errors="replace")
        output = result.stdout + result.stderr
        output = output.replace(env["MYSQL_PASSWORD"], "[REDACTED]")
        print(output, end="")
        return result.returncode

if __name__ == "__main__":
    raise SystemExit(main())
