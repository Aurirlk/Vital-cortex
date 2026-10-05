# -*- coding: utf-8 -*-
"""给 UserMapper.xml 适配 phone / phone_verified 字段（新增于 2026-10-03 库表整理）。"""
import io
import os

P = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 "src", "main", "resources", "mapper", "UserMapper.xml")
s = io.open(P, encoding="utf-8").read()
orig = s

# 1) insert 增加 phone / phone_verified
old = "                          user_email,\n                          user_role,"
new = ("                          user_email,\n                          phone,\n"
       "                          phone_verified,\n                          user_role,")
assert old in s, "insert 列片段未找到"
s = s.replace(old, new, 1)

old = "                #{userEmail},\n                #{userRole},"
new = ("                #{userEmail},\n                #{phone},\n"
       "                <choose>\n"
       "                    <when test=\"phoneVerified != null\">#{phoneVerified}</when>\n"
       "                    <otherwise>0</otherwise>\n"
       "                </choose>,\n"
       "                #{userRole},")
assert old in s, "insert 值片段未找到"
s = s.replace(old, new, 1)

# 2) update 增加字段
old = ("            <if test=\"userEmail != null\">\n"
       "                user_email = #{userEmail},\n"
       "            </if>")
new = old + ("\n            <if test=\"phone != null\">\n"
             "                phone = #{phone},\n"
             "            </if>\n"
             "            <if test=\"phoneVerified != null\">\n"
             "                phone_verified = #{phoneVerified},\n"
             "            </if>")
assert old in s, "update 片段未找到"
s = s.replace(old, new, 1)

# 3) resultMap 增加映射
old = ("        <result column=\"user_email\" property=\"userEmail\"/>\n"
       "        <result column=\"user_role\" property=\"userRole\"/>")
new = ("        <result column=\"user_email\" property=\"userEmail\"/>\n"
       "        <result column=\"phone\" property=\"phone\"/>\n"
       "        <result column=\"phone_verified\" property=\"phoneVerified\"/>\n"
       "        <result column=\"user_role\" property=\"userRole\"/>")
assert old in s, "resultMap 片段未找到"
s = s.replace(old, new, 1)

# 4) query / queryCount：手机号模糊搜索
old = ("            <if test=\"userEmail != null and userEmail != ''\">\n"
       "                AND u.user_email LIKE concat('%',#{userEmail},'%')\n"
       "            </if>")
new = old + ("\n            <if test=\"phone != null and phone != ''\">\n"
             "                AND u.phone LIKE concat('%',#{phone},'%')\n"
             "            </if>")
n = s.count(old)
assert n >= 2, "期望 query/queryCount 至少 2 处，实际 %d" % n
s = s.replace(old, new)

# 5) getByActive：支持按手机号查（登录/状态判定）
old = ("            <if test=\"userAccount != null and userAccount != ''\">\n"
       "                AND u.user_account = #{userAccount}\n"
       "            </if>")
new = old + ("\n            <if test=\"phone != null and phone != ''\">\n"
             "                AND u.phone = #{phone}\n"
             "            </if>")
assert old in s, "getByActive 片段未找到"
s = s.replace(old, new, 1)

io.open(P, "w", encoding="utf-8", newline="").write(s)
print("UserMapper.xml 已适配 phone")
print("  改动: insert / update / resultMap / query+%d / getByActive" % n)
print("  文件变化:", s != orig)
