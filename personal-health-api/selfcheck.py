# -*- coding: utf-8 -*-
"""
后端静态自检：在不执行编译的前提下，检查改动引入的常见编译期错误。

适用背景：本机 Maven 版本低于 maven-compiler-plugin 要求，编译由用户在 IDEA 侧执行；
AI 侧需要一种不依赖构建的校验手段来兜底。

检查项（均为编译期必然报错或高危问题）：
1. 使用了 log.* 但类上缺 @Slf4j
2. Mapper XML 的 statement id 与 Mapper 接口方法不匹配
3. Mapper XML namespace 与接口全限定名不匹配
4. 引用了 cn.kmbeast 包下不存在的类
5. Java 文件花括号配平
6. Mapper XML 标签配平
7. SQL 中出现 ${} 拼接（注入风险）

用法：python personal-health-api/selfcheck.py
"""
import io
import os
import re
import sys

# 脚本位于 <项目>/personal-health-api/ 下，模块根目录就是脚本所在目录
MOD = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(MOD, "src", "main", "java")
RES = os.path.join(MOD, "src", "main", "resources")

CHANGED = [
    "cn/kmbeast/pojo/entity/News.java",
    "cn/kmbeast/pojo/entity/Department.java",
    "cn/kmbeast/pojo/entity/HospitalDoctor.java",
    "cn/kmbeast/pojo/em/RoleEnum.java",
    "cn/kmbeast/pojo/dto/request/DoctorLoginDTO.java",
    "cn/kmbeast/service/DoctorAuthService.java",
    "cn/kmbeast/service/impl/DoctorAuthServiceImpl.java",
    "cn/kmbeast/controller/DoctorController.java",
    "cn/kmbeast/mapper/HospitalDoctorMapper.java",
    "cn/kmbeast/mapper/UserMapper.java",
    "cn/kmbeast/mapper/DepartmentMapper.java",
    "cn/kmbeast/PersonalHealthApplication.java",
    "cn/kmbeast/core/voice/VoiceSynthesisExecutor.java",
    "cn/kmbeast/core/voice/VoiceExecutorLifecycle.java",
    "cn/kmbeast/core/voice/TTSFactory.java",
    "cn/kmbeast/core/voice/EdgeTTSProvider.java",
    "cn/kmbeast/service/impl/VoiceServiceImpl.java",
    "cn/kmbeast/service/impl/AiChatCacheServiceImpl.java",
    "cn/kmbeast/service/AiChatCacheService.java",
    "cn/kmbeast/controller/AiController.java",
    "cn/kmbeast/controller/AppointmentController.java",
    "cn/kmbeast/controller/DoctorController.java",
    "cn/kmbeast/controller/UserController.java",
    "cn/kmbeast/service/impl/AppointmentServiceImpl.java",
    "cn/kmbeast/service/DoctorAuthService.java",
    "cn/kmbeast/service/impl/DoctorAuthServiceImpl.java",
    "cn/kmbeast/Interceptor/JwtInterceptor.java",
    "cn/kmbeast/pojo/entity/User.java",
    "cn/kmbeast/pojo/vo/DepartmentVO.java",
    "cn/kmbeast/service/AppointmentService.java",
    "cn/kmbeast/service/impl/AppointmentServiceImpl.java",
    "cn/kmbeast/mapper/NewsMapper.java",
    "cn/kmbeast/mapper/CommentMapper.java",
    "cn/kmbeast/aop/ProtectorAspect.java",
    "cn/kmbeast/Interceptor/JwtInterceptor.java",
]
XMLS = [
    "mapper/HospitalDoctorMapper.xml",
    "mapper/UserMapper.xml",
    "mapper/DepartmentMapper.xml",
    "mapper/CommentMapper.xml",
    "mapper/NewsMapper.xml",
    "mapper/CommentMapper.xml",
]

errs, warns, skipped = [], [], []


def read(p):
    return io.open(p, encoding="utf-8").read()


all_java = {}
for dp, dn, fn in os.walk(JAVA):
    for f in fn:
        if f.endswith(".java"):
            p = os.path.join(dp, f)
            all_java[os.path.relpath(p, JAVA).replace("\\", "/").replace(".java", "")] = p

# 1 & 5: log/@Slf4j + 花括号
for rel in CHANGED:
    p = os.path.join(JAVA, rel)
    if not os.path.exists(p):
        skipped.append("未创建（可跳过）: %s" % rel)
        continue
    s = read(p)
    if re.search(r"(?<![\w.])log\.(info|warn|error|debug|trace)\(", s) and "@Slf4j" not in s:
        errs.append("%s 使用了 log.* 但类上缺少 @Slf4j" % rel)
    if s.count("{") != s.count("}"):
        errs.append("%s 花括号不配平: { %d vs } %d" % (rel, s.count("{"), s.count("}")))
    for imp in re.findall(r"^import\s+(cn\.kmbeast\.[\w\.]+);", s, re.M):
        if imp.replace(".", "/") not in all_java:
            errs.append("%s 引用了不存在的类: %s" % (rel, imp))

# 2 & 3 & 6 & 7: Mapper XML
for rel in XMLS:
    p = os.path.join(RES, rel)
    if not os.path.exists(p):
        skipped.append("未创建（可跳过）: %s" % rel)
        continue
    s = read(p)
    ns = re.search(r'namespace="([^"]+)"', s)
    if not ns:
        errs.append("%s 缺 namespace" % rel)
        continue
    iface = ns.group(1).replace(".", "/") + ".java"
    ip = os.path.join(JAVA, iface)
    if not os.path.exists(ip):
        errs.append("%s 的 namespace 对应接口不存在: %s" % (rel, ns.group(1)))
        continue
    isrc = read(ip)
    jm = set(re.findall(r"^\s*[\w<>,\[\]\s\.]+?\s+(\w+)\s*\(", isrc, re.M))
    xm = set(re.findall(r'<\s*(?:select|insert|update|delete)\s+id="(\w+)"', s))
    if xm - jm:
        errs.append("%s: XML 有 statement 但接口无方法 %s" % (rel, sorted(xm - jm)))
    only_java = {m for m in jm - xm if m not in ("toString", "equals", "hashCode")}
    if only_java:
        warns.append("%s: 接口有方法但 XML 未实现 %s（默认方法/继承可忽略）" % (rel, sorted(only_java)))
    if "${" in s:
        errs.append("%s: SQL 中出现 ${} 拼接，应改用 #{}" % rel)
    for t in ("select", "insert", "update", "delete", "mapper"):
        o = len(re.findall(r"<%s[\s>]" % t, s))
        c = len(re.findall(r"</%s>" % t, s))
        if o != c:
            errs.append("%s: <%s> 配平异常 %d/%d" % (rel, t, o, c))

print("=" * 60)
print("后端静态自检（不依赖构建）")
print("=" * 60)
if skipped:
    print("跳过 %d 项：" % len(skipped))
    for x in skipped:
        print("   - %s" % x)
    print()
if warns:
    print("提示 %d 项：" % len(warns))
    for w in warns:
        print("   ! %s" % w)
    print()
if errs:
    print("错误 %d 项：" % len(errs))
    for e in errs:
        print("   X %s" % e)
    sys.exit(1)
print("OK 未发现编译期必然错误")
