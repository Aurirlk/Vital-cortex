# AI Prompts — DataAgent 资产摘取（阶段 4 / 5.1）

本目录下的提示词文件**复制自**外部开源项目
[spring-ai-alibaba/DataAgent](https://github.com/spring-ai-alibaba/DataAgent)
（`D:\Program\DataAgent`，父 pom `com.alibaba.cloud.ai:spring-ai-alibaba-data-agent:1.0.0-SNAPSHOT`）。

## 开源协议与署名

- **协议**：Apache License 2.0
- **来源仓库**：`github.com/spring-ai-alibaba/DataAgent`（PR#629 状态）
- **摘取方式**：纯文本复制，未做框架层合并；本项目通过 `cn.kmbeast.core.nl2sql.Nl2SqlPromptBuilder`
  在运行时以自研 `{}` 占位符替换加载这些模板，**不依赖 Spring AI**。

> 依据 Apache 2.0 第 4 条（b） redistribution 需保留版权与许可声明，本文件即为署名载体。
> 全部提示词为中文硬编码，换英文模型效果会下降。

## 文件清单（2026-10-07 自 DataAgent 仓库摘取，实测行数）

| 文件 | 行数 | 用途 |
|---|---|---|
| `new-sql-generate.txt` | 58 | 核心 SQL 生成（只读约束仅在此 prompt 内；本项目额外加 `MySqlAiSqlGuard` 硬校验） |
| `query-enhancement.txt` | 65 | 问题改写 |
| `semantic-consistency.txt` | 66 | 语义一致性校验 |
| `sql-error-fixer.txt` | 53 | SQL 错误修复 |
| `intent-recognition.txt` | 77 | 意图识别（对应本项目阶段 3 路由） |
| `evidence-query-rewrite.txt` | 47 | 证据查询改写 |
| `mix-selector.txt` | 43 | 相关表选择 |
| **合计** | **409** | |

> ⚠️ 迁移方案初版记录为 506 行，系早期 commit 计数；DataAgent 仓库后续修订过这些模板，
> 实际摘取为 409 行。以本表实测为准。

## 未接线模板（2026-10-07 标注）

以下模板文件存在于本目录，但**未被 `Nl2SqlPromptBuilder.init()` 加载**、也没有任何调用者：


| 文件 | 正文行数 | 说明 |
|---|---|---|
| `feasibility-assessment.txt` | 47 | 可行性评估（DataAgent 遗留能力） |
| `semantic-model.txt` | 17 | 语义模型（含 `{semanticModel}` 占位符，但 `new-sql-generate.txt` 里没有对应段落） |

**为什么不加载**：加载后只会让「数据智能体 → 提示词」管理页多出两个可编辑项，但对实际行为无改善。
**如需启用**：① `Nl2SqlPromptBuilder.init()` 中加 `load(...)`；② 补 `buildXxxPrompt` 方法与调用点；③ 补测试。


## 已加载但无调用者

| 项 | 状态 | 说明 |
|---|---|---|
| `evidence-query-rewrite.txt` | 已 load | 对应的 `Nl2SqlPromptBuilder.buildEvidenceQueryRewritePrompt()` 无调用者 —— 多轮查询改写未接线（多轮改用 `query-enhancement` + `intent-recognition` 的 `multi_turn` 通道实现） |
| `DataAgentQaResult.steps` | 已实现 | 2026-10-07 之前恒为空；现同步链路已回填，SSE 链路仍为空（避免 result 帧翻倍） |
