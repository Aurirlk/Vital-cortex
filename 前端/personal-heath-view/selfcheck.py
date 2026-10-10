#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
前端静态自检（2026-10-07）

为什么需要它：本项目 AI 侧不跑 `npm run build`（耗时且 node_modules 可能不完整），
但每次改完 .vue 都希望立刻排除两类低级错误——
  1) `<script>` 里 JS 语法错误（提取为 .mjs 后交给 `node --check`）；
  2) `<template>` 标签不配平 / 未闭合（栈式校验）。

用法（在 前端/personal-heath-view 目录下）：
    python selfcheck.py

退出码：0=全部通过；1=存在失败项。

注意（踩过的坑）：
  * 提取根 template 时**不能用非贪婪** `<template>(.*?)</template>` —— 组件里的
    `<template #default>` 会让匹配提前结束，导致大面积误报。正确做法是「首个行首
    <template> → 文件中最后一个 </template>」。
  * 必须排除自闭合标签（`/>`）与 HTML void 元素，否则 `<el-input ... />` 会被当成未闭合。
"""

import io
import os
import re
import subprocess
import sys

NODE_CANDIDATES = [
    r"C:\Users\ly\.workbuddy\binaries\node\versions\22.22.2-3\node.exe",
    "node",
]

VOID_TAGS = {
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "source", "track", "wbr",
}

TAG_RE = re.compile(r"<(/?)([a-zA-Z][a-zA-Z0-9-]*)((?:\"[^\"]*\"|'[^']*'|[^>\"'])*?)(/?)>", re.S)
ROOT_TPL_RE = re.compile(r"(?m)^\s*<template>\s*$")
SCRIPT_RE = re.compile(r"<script(?:\s[^>]*)?>(.*?)</script>", re.S)
COMMENT_RE = re.compile(r"<!--.*?-->", re.S)


def pick_node():
    for c in NODE_CANDIDATES:
        try:
            r = subprocess.run([c, "--version"], capture_output=True, text=True)
            if r.returncode == 0:
                return c
        except (OSError, FileNotFoundError):
            continue
    return None


def outer_template(src):
    """取根 template 内容：首个行首 <template> → 文件中最后一个 </template>。"""
    starts = [m.end() for m in ROOT_TPL_RE.finditer(src)]
    if starts:
        start = starts[0]
    else:
        m = re.search(r"<template>", src)
        if not m:
            return None
        start = m.end()
    end = src.rfind("</template>")
    return src[start:end] if end > start else None


def check_template(src):
    tpl = outer_template(src)
    if tpl is None:
        return ["没有找到根 <template>"]
    tpl = COMMENT_RE.sub("", tpl)
    stack, errs = [], []
    for m in TAG_RE.finditer(tpl):
        closing, tag, _attrs, self_close = m.group(1), m.group(2).lower(), m.group(3), m.group(4)
        if tag in VOID_TAGS or self_close:
            continue
        if closing:
            if not stack:
                errs.append("多余的闭合标签 </%s>" % tag)
            elif stack[-1] != tag:
                errs.append("标签不配平：期望 </%s>，实得 </%s>" % (stack[-1], tag))
                stack.pop()
            else:
                stack.pop()
        else:
            stack.append(tag)
    if stack:
        errs.append("未闭合标签: " + ", ".join(stack))
    return errs


def check_script(node, name, src, tmp_dir):
    errs = []
    idx = 0
    for m in SCRIPT_RE.finditer(src):
        tmp = os.path.join(tmp_dir, "%s_%d.mjs" % (re.sub(r"\W", "_", name), idx))
        io.open(tmp, "w", encoding="utf-8").write(m.group(1))
        r = subprocess.run([node, "--check", tmp], capture_output=True, text=True, encoding="utf-8")
        if r.returncode != 0:
            errs.append("script#%d JS 语法错误:\n%s" % (idx, (r.stderr or "").strip()[:900]))
        idx += 1
    if idx == 0:
        errs.append("没有找到 <script>")
    return errs


def main():
    base = os.path.abspath(os.path.dirname(__file__))
    node = pick_node()
    if not node:
        print("[skip] 未找到可用的 node，无法做 JS 语法校验")
        return 0

    tmp_dir = os.path.join(base, ".selfcheck")
    os.makedirs(tmp_dir, exist_ok=True)

    targets = []
    src_root = os.path.join(base, "src")
    for root, dirs, files in os.walk(src_root):
        dirs[:] = [d for d in dirs if d != "node_modules"]
        targets += [os.path.join(root, f) for f in files if f.endswith(".vue")]

    bad = 0
    for path in sorted(targets):
        src = io.open(path, encoding="utf-8").read()
        name = os.path.relpath(path, base)
        errs = check_template(src) + check_script(node, name, src, tmp_dir)
        if errs:
            bad += 1
            print("FAIL", name)
            for e in errs:
                print("   -", e)

    # 纯 JS 工具文件同样做语法校验（按 ESM 处理）
    js_files = []
    for root, dirs, files in os.walk(src_root):
        dirs[:] = [d for d in dirs if d != "node_modules"]
        js_files += [os.path.join(root, f) for f in files if f.endswith(".js")]
    for path in sorted(js_files):
        name = os.path.relpath(path, base)
        tmp = os.path.join(tmp_dir, re.sub(r"\W", "_", name) + ".mjs")
        io.open(tmp, "w", encoding="utf-8").write(io.open(path, encoding="utf-8").read())
        r = subprocess.run([node, "--check", tmp], capture_output=True, text=True, encoding="utf-8")
        if r.returncode != 0:
            bad += 1
            print("FAIL", name)
            print("   -", (r.stderr or "").strip()[:900])

    print("checked .vue = %d, .js = %d | failed = %d" % (len(targets), len(js_files), bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
