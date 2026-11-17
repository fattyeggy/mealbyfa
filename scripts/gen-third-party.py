#!/usr/bin/env python3
"""生成 THIRD_PARTY_NOTICES.md：后端 Maven 直接依赖 + 前端 npm 直接依赖的许可证清单。

用法：python3 scripts/gen-third-party.py
依赖：本地构建过前端（node_modules 存在）与后端（tools/m2 有依赖 pom）。
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
M2 = os.path.join(ROOT, "tools", "m2")
ENV = os.path.join(ROOT, "scripts", "env.sh")
OUT = os.path.join(ROOT, "docs", "交付", "THIRD_PARTY_NOTICES.md")


def maven_direct_deps():
    """解析 dependency:list，仅保留 compile/runtime 直接依赖。"""
    cmd = f'source "{ENV}" && cd "{os.path.join(ROOT, "backend")}" && mvn -B -ntp -q dependency:list -DincludeScope=runtime -DoutputFile=/tmp/deps.txt >/dev/null 2>&1'
    subprocess.run(["bash", "-c", cmd], check=False)
    if not os.path.exists("/tmp/deps.txt"):
        return []
    deps = []
    pattern = re.compile(r'^\s+([\w.\-]+):([\w.\-]+):jar:([\w.\-]+):(compile|runtime)')
    for line in open("/tmp/deps.txt", encoding="utf-8"):
        m = pattern.match(line)
        if m:
            deps.append((m.group(1), m.group(2), m.group(3)))
    return sorted(set(deps))


def pom_license(group, artifact, version):
    path = os.path.join(M2, *group.split("."), artifact, version, f"{artifact}-{version}.pom")
    if not os.path.exists(path):
        return "本地未找到 pom，需人工核对"
    xml = open(path, encoding="utf-8", errors="ignore").read()
    names = re.findall(r"<license>.*?<name>(.*?)</name>", xml, re.S)
    return " / ".join(sorted({n.strip() for n in names})) if names else "pom 未声明，需人工核对"


def npm_deps(package_dir):
    pkg_path = os.path.join(ROOT, package_dir, "package.json")
    if not os.path.exists(pkg_path):
        return []
    pkg = json.load(open(pkg_path, encoding="utf-8"))
    result = []
    for name, version in sorted(pkg.get("dependencies", {}).items()):
        mod = os.path.join(ROOT, package_dir, "node_modules", name, "package.json")
        if os.path.exists(mod):
            meta = json.load(open(mod, encoding="utf-8"))
            license_name = meta.get("license") or meta.get("licenses") or "未声明"
            if isinstance(license_name, list):
                license_name = ", ".join(str(x) for x in license_name)
            result.append((name, version, str(license_name)))
        else:
            result.append((name, version, "未安装"))
    return result


def main():
    lines = ["# 第三方依赖声明 (THIRD_PARTY_NOTICES)", "",
             "> 由 `scripts/gen-third-party.py` 生成。仅列**直接依赖**；",
             "> 交付前建议在 CI 中运行完整扫描（Maven: license-maven-plugin；前端: license-checker）。", "",
             "本项目自身代码的授权见 `LICENSE.md`。", ""]

    backend = maven_direct_deps()
    lines.append("## 后端（Maven，runtime 直接依赖）")
    lines.append("")
    lines.append("| 依赖 | 版本 | 许可证 |")
    lines.append("|---|---|---|")
    if backend:
        for group, artifact, version in backend:
            lines.append(f"| `{group}:{artifact}` | {version} | {pom_license(group, artifact, version)} |")
    else:
        lines.append("| （未能解析，请先构建后端） | — | — |")
    lines.append("")

    for title, directory in (("运营后台（npm）", "admin"), ("小程序（npm）", "miniapp")):
        deps = npm_deps(directory)
        lines.append(f"## {title}")
        lines.append("")
        lines.append("| 依赖 | 声明版本 | 许可证 |")
        lines.append("|---|---|---|")
        if deps:
            for name, version, license_name in deps:
                lines.append(f"| `{name}` | {version} | {license_name} |")
        else:
            lines.append("| （未安装依赖） | — | — |")
        lines.append("")

    lines.extend([
        "## 说明", "",
        "1. Spring Boot、MyBatis-Plus、H2、MySQL Connector/J、Lombok 等为 Apache-2.0 或兼容许可；",
        "2. Vue、Vite、Element Plus 为 MIT；uni-app 系列遵循 DCloud 许可，商用前请核对官方条款；",
        "3. 本项目未内嵌或修改上述依赖源码，均以二进制/包依赖方式引用；",
        "4. 若后续引入 GPL/AGPL 依赖，需在交付评审中单独标注。",
    ])

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w", encoding="utf-8").write("\n".join(lines))
    print(f"已生成 {OUT}")
    print(f"后端直接依赖 {len(backend)} 项")


if __name__ == "__main__":
    main()
