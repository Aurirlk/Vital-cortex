import bcrypt, re
with open(r"D:/Program/智康云-健康管理系统/Data/_pwd_check.txt", "r", encoding="utf-8") as f:
    txt = f.read()
m = re.search(r"admin\t(\S+)", txt)
h = m.group(1).strip()
print("HASH len =", len(h))
print("HASH =", h)
candidates = ["123456", "admin", "12345678", "password", "123456789", "admin123", "root", "123123"]
try:
    hb = h.encode()
    for c in candidates:
        try:
            ok = bcrypt.checkpw(c.encode(), hb)
        except Exception as e:
            ok = "ERR:" + str(e)
        print("  checkpw(%r) = %s" % (c, ok))
except Exception as e:
    print("bcrypt error:", e)
