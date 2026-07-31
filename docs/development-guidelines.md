# 智康云 - 开发防坑指南

## ⚠️ 编码损坏问题（血泪教训）

### 问题描述
在尝试 Spring Boot 3.x 升级时，使用 PowerShell 的 `-replace` 操作符批量替换 Java 文件中的中文字符，导致 **238 个文件的 UTF-8 编码被损坏**。

### 根本原因
PowerShell 的字符串操作会破坏 UTF-8 多字节中文字符的编码，导致：
- 中文字符被截断（如 `管理员` → `管理?`）
- 字符串引号错位（如 `"文本")` → `"文本?)`）
- BOM 字符被添加到文件开头

### 防范措施

#### ❌ 禁止操作
```powershell
# 禁止：使用 PowerShell -replace 替换中文
$content -replace '旧中文', '新中文'

# 禁止：使用 Set-Content 写入含中文的内容
Set-Content $file -Value $content
```

#### ✅ 推荐操作
```python
# 推荐：使用 Python 处理中文内容
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('旧中文', '新中文')
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
```

```bash
# 推荐：使用 git checkout 恢复损坏文件
git checkout HEAD -- path/to/file.java
```

### 损坏检测
```bash
# 检查文件是否有损坏的 UTF-8 字符
mvn compile 2>&1 | Select-String "UTF-8"

# 检查是否有 BOM 字符
py -3 -c "
with open('file.java', 'rb') as f:
    data = f.read()
    if data.startswith(b'\xef\xbb\xbf'):
        print('Has BOM!')
"
```

### 修复方法
```python
# 修复脚本：移除 BOM + 修复未闭合引号
import os

def fix_java_file(filepath):
    with open(filepath, 'rb') as f:
        data = f.read()
    
    # 移除 BOM
    if data.startswith(b'\xef\xbb\xbf'):
        data = data[3:]
    
    # 解码并清理
    text = data.decode('utf-8', errors='replace')
    text = text.replace('\ufffd', '')
    
    # 修复未闭合引号
    lines = text.split('\n')
    fixed_lines = []
    for line in lines:
        quote_count = line.count('"')
        if quote_count % 2 != 0:
            line = line.rstrip() + '"'
        fixed_lines.append(line)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write('\n'.join(fixed_lines))
```

---

## 📁 项目结构保护

### 核心目录（禁止删除）
- `后端/personal-health-api/src/main/java/cn/kmbeast/core/` - 核心模块
- `后端/personal-health-api/src/main/java/cn/kmbeast/crm/` - CRM 系统
- `前端/personal-heath-view/src/styles/` - 设计系统

### Git 保护策略
```bash
# 提交前检查状态
git status
git diff --name-only

# 提交核心变更
git add .
git commit -m "feat: 功能描述"
git push
```

---

## 🔧 依赖管理

### pom.xml 关键依赖
```xml
<!-- 必须保留的依赖 -->
spring-boot-starter-websocket  <!-- WebSocket 支持 -->
pdfbox:2.0.29                 <!-- PDF 解析 -->
neo4j-java-driver:4.4.12      <!-- 知识图谱 -->
spring-boot-starter-data-redis <!-- 缓存 -->
micrometer-registry-prometheus <!-- 监控 -->
```

### 依赖恢复
如果 pom.xml 被损坏，从 git 恢复：
```bash
git checkout HEAD -- 后端/personal-health-api/pom.xml
```

---

## 📋 任务执行规范

### 批量文件操作
1. **优先使用 Python** 而非 PowerShell
2. **先备份** 再修改
3. **小批量测试** 再全量执行
4. **验证编译** 每批次后检查

### 编码验证
```bash
# 每次批量操作后验证
mvn compile -q && echo "Backend OK"
npm run build && echo "Frontend OK"
```

---

## 🚨 紧急恢复流程

### 1. 后端文件损坏
```bash
# 方法1：git 恢复
git checkout HEAD -- 后端/personal-health-api/src/

# 方法2：Python 修复
py -3 fix_encoding.py

# 方法3：删除并重建
Remove-Item "损坏文件.java"
# 然后重新创建
```

### 2. 前端文件损坏
```bash
# 前端文件一般不会损坏（npm 管理）
# 如果损坏：
git checkout HEAD -- 前端/personal-heath-view/src/
npm install
```

### 3. 完全重置
```bash
# 恢复到最近一次正常提交
git log --oneline -5
git reset --hard HEAD~1
```

---

**记住：PowerShell + 中文 = 灾难。始终使用 Python 处理中文内容！**
