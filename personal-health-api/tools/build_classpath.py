# -*- coding: utf-8 -*-
"""
为 AI 侧的 javac / JUnit 直跑构建 classpath。

背景：本机 Maven 3.6.1 低于插件要求（3.6.3），且含空格的 -Dmaven.repo.local
在 PowerShell 下会被拆成插件前缀，所以命令行 mvn 跑不通，只能直接用 javac。
但 javac 需要完整 classpath，这里从 ~/.m2/repository 里把所有 jar 捞出来。

⚠️ 必须按 artifact 去重取最高版本：仓库里同时存在新旧两个 lombok（旧版会屏蔽
注解处理，导致 @Slf4j/@Data 生成的成员“找不到符号”）和新旧两个 jjwt（0.12 才有
.id()/.verifyWith()），全量拼进去会把最坑的那个排在最前面。

用法：
    python tools/build_classpath.py main      # main 源码
    python tools/build_classpath.py test      # main + test 源码
输出一行，可直接贴到 javac -cp 后面。
"""
import os
import re
import sys

PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 本机 Maven 用了自定义本地仓库（含空格，Windows 下叫 maven），
# 与大仓库同名的 ~/.m2/repository 可能是空的，优先挑 jar 最多的那个。
_CANDIDATES = [
    os.environ.get("MAVEN_REPO", ""),
    "D:/Program Files/Maven/repository",
    os.path.expanduser("~") + "/.m2/repository",
]
M2 = max((p for p in _CANDIDATES if p and os.path.isdir(p)),
         key=lambda p: sum(len(f) for _d, _s, f in os.walk(p)))

# 版本里出现 SNAPSHOT / RELEASE / 时间戳 时按字符串比较，其余按数字段比较
_VERSION_NUM = re.compile(r"\d+")


def version_key(v: str):
    parts = []
    for seg in v.replace("-", ".").split("."):
        seg = _VERSION_NUM.search(seg)
        parts.append((1, int(seg.group())) if seg else (0, 0))
    return parts


def dedupe_jars(root: str):
    """按 artifact 目录分组，每组只保留版本号最高的那个 jar。"""
    best = {}
    for dirpath, _dirnames, filenames in os.walk(root):
        if not any(f.endswith(".jar") for f in filenames):
            continue
        # artifact 目录 = jar 所在目录的上两级（.../groupId/artifactId/version/*.jar）
        rel = os.path.relpath(dirpath, root)
        parts = rel.split(os.sep)
        if len(parts) < 3:
            continue
        artifact = os.sep.join(parts[-3:-1])
        jars = [os.path.join(dirpath, f) for f in filenames if f.endswith(".jar")]
        if not jars:
            continue
        current = best.get(artifact)
        newest = max(jars, key=lambda p: version_key(os.path.basename(os.path.dirname(p))))
        if current is None or version_key(os.path.basename(os.path.dirname(newest))) \
                > version_key(os.path.basename(os.path.dirname(current))):
            best[artifact] = newest
    return sorted(best.values())


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "main"
    # ⚠️ 刻意不使用 m2 全量 jar：仓库里有上千个 jar、同名库并存多版本
    # （slf4j-api 2.0.9/2.0.18、lang3 3.8.1/3.20……），全拼进去既超
    # Windows 单参数 32767 字符上限，又容易把旧版排到前面（旧 lombok 会屏蔽注解处理）。
    # target/lib5 是全项目手工整理过的「唯一权威」jar 集，同为多个版本时取其新版，
    # 缺什么再单独补 —— 比让机器按路径猜版本可靠得多。
    cp = [os.path.join(PROJ, "target", "classes")]
    if mode == "test":
        cp.append(os.path.join(PROJ, "target", "test-classes"))

    lib5 = os.path.join(PROJ, "target", "lib5")
    if not os.path.isdir(lib5):
        raise SystemExit("缺少 target/lib5（手工整理的 jar 集），无法构建 classpath")
    cp += [os.path.join(lib5, f) for f in sorted(os.listdir(lib5))
           if f.endswith(".jar")]

    # m2 兜底（默认关）：把整个本地仓库拼进去会轻松超过 32767 字符上限，
    # 只有在确实缺 jar、且已经确认版本不会互相覆盖时才加。
    if "--with-m2" in sys.argv:
        have = {os.path.basename(p) for p in cp}
        cp += [p for p in dedupe_jars(M2) if os.path.basename(p) not in have]

    # 源码目录用相对路径输出，方便直接在项目根执行 javac
    if mode == "test":
        cp.append(os.path.join(PROJ, "src", "test", "java"))
    # Windows 下 javac 的 classpath 分隔符是分号（不是 Linux 的冒号），
    # 路径内部统一 forward slash（含空格的仓库路径在 cmd 下也不好使）
    cp = [p.replace(os.sep, "/") for p in cp]
    joined = ";".join(cp).replace(PROJ, ".")

    # classpath 有 5 万字符以上，直接塞命令行会触发
    # "Argument list too long"；javac/java 都支持 @argfile，由脚本自己生成最稳。
    if "--argfile" in sys.argv:
        with open(os.path.join(PROJ, "target", ".javac.argf"), "w", encoding="utf-8") as f:
            f.write("-encoding\nUTF-8\n-nowarn\n-cp\n\"%s\"\n-d\ntarget/test-classes\n" % joined)
        with open(os.path.join(PROJ, "target", ".java.argf"), "w", encoding="utf-8") as f:
            f.write("\"%s\"\n" % joined)
        return
    print(joined)


if __name__ == "__main__":
    main()
