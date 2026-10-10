# -*- coding: utf-8 -*-
"""
生成项目架构图 / 产品图。

产出两类文件到 docs/assets/：
  - *.drawio ：draw.io 源文件（可用本地 draw.io 桌面版打开继续编辑）
  - *.svg / *.png ：由本地 draw.io CLI 导出的图片（README 与文档引用）

用法：
    python tools/gen_diagrams.py            # 生成 .drawio 并导出 svg+png
    python tools/gen_diagrams.py --src-only # 只生成 .drawio

依赖：本机安装 draw.io 桌面版（CLI 位于 D:\\Program Files\\draw.io\\draw.io.exe）。
若未安装，脚本仍会写出 .drawio，只跳过导出。
"""

import os
import re
import subprocess
import sys

# ---------------------------------------------------------------- 常量

BRAND = '#0050CB'          # 品牌主色
BRAND_LIGHT = '#E8F1FD'
BRAND_LIGHT2 = '#F5F8FC'
INK = '#1F2937'            # 主文字
INK_SOFT = '#475569'
LINE = '#94A3B8'
FONT = 'Microsoft YaHei'

# 分类配色
C_ACCESS = ('#E8F1FD', '#0050CB')   # 接入层
C_SEC = ('#FDECEA', '#C5221F')      # 安全
C_BIZ = ('#E6F4EA', '#137333')      # 业务
C_AI = ('#F3E8FD', '#7627BB')       # AI
C_DATA = ('#FEF7E0', '#B06000')     # 数据
C_INFRA = ('#E8EAED', '#5F6368')    # 基础设施

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, 'docs', 'assets')
DRAWIO_CLI = r'D:\Program Files\draw.io\draw.io.exe'


# ---------------------------------------------------------------- 工具函数

def esc(s):
    return (s.replace('&', '&amp;').replace('<', '&lt;')
             .replace('>', '&gt;').replace('"', '&quot;'))


def prep(label):
    """把标签规整为「无 HTML 标签 + &#10; 换行」。

    draw.io 导出 SVG 时，只要标签里出现 HTML（<br/>、<font>、<b>），
    就会把整块文字栅格化成内嵌 base64 位图 —— 体积暴涨到 1 MB 级且在
    GitHub 上放大发虚。改用 &#10; 换行后导出的是真正的 <text> 矢量文本。
    """
    s = re.sub(r'</?(?:b|i|u|strong|em)>', '', label)
    s = re.sub(r'<font[^>]*>|</font>', '', s)
    s = s.replace('<br/>', '\x01').replace('<br>', '\x01')
    return esc(s).replace('\x01', '&#10;')


def box_style(fill, stroke, font=INK, size=12, bold=0, rounded=1,
              align='center', valign='middle', dashed=0, extra=''):
    return (f'rounded={rounded};whiteSpace=wrap;html=1;arcSize=0.12;'
            f'fillColor={fill};strokeColor={stroke};fontColor={font};'
            f'fontFamily={FONT};fontSize={size};fontStyle={1 if bold else 0};'
            f'align={align};verticalAlign={valign};dashed={dashed};' + extra)


def lane_style(fill, stroke, size=13, start=34):
    return (f'swimlane;html=1;horizontal=1;startSize={start};'
            f'fillColor={fill};strokeColor={stroke};fontColor={stroke};'
            f'fontFamily={FONT};fontSize={size};fontStyle=1;'
            f'swimlaneFillColor={fill};align=center;verticalAlign=middle;'
            f'rounded=1;arcSize=0.03;')


def note_style(color=INK_SOFT, size=11, align='left'):
    return (f'text;html=1;whiteSpace=wrap;strokeColor=none;fillColor=none;'
            f'fontColor={color};fontFamily={FONT};fontSize={size};'
            f'align={align};verticalAlign=middle;')


def arrow_style(color=BRAND, size=18):
    return (f'text;html=1;whiteSpace=wrap;strokeColor=none;fillColor=none;'
            f'fontColor={color};fontFamily={FONT};fontSize={size};'
            f'align=center;verticalAlign=middle;fontStyle=1;')


def edge_style(color=LINE, width=1.6, dashed=0, arrow='blockThin'):
    return (f'edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;'
            f'endArrow={arrow};endFill=1;startArrow=none;startFill=0;'
            f'strokeColor={color};strokeWidth={width};dashed={dashed};'
            f'fontFamily={FONT};fontSize=11;fontColor={INK_SOFT};'
            f'labelBackgroundColor=#FFFFFF;')


def v(cid, label, x, y, w, h, style, parent='1'):
    return (f'<mxCell id="{cid}" value="{prep(label)}" style="{style}" '
            f'vertex="1" parent="{parent}">'
            f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/>'
            f'</mxCell>')


def e(cid, src, dst, label='', style=None, parent='1',
      exit_side='bottom', entry_side='top'):
    sides = {
        'bottom': (0.5, 1, 0.5, 0),
        'top': (0.5, 0, 0.5, 1),
        'right': (1, 0.5, 0, 0.5),
        'left': (0, 0.5, 1, 0.5),
    }
    ex, ey, nx, ny = sides[exit_side if exit_side else 'bottom']
    gx, gy = sides[entry_side if entry_side else 'top'][2], sides[entry_side if entry_side else 'top'][3]
    st = style or edge_style()
    st += (f'exitX={ex};exitY={ey};exitDx=0;exitDy=0;'
           f'entryX={nx};entryY={ny};entryDx=0;entryDy=0;')
    return (f'<mxCell id="{cid}" value="{esc(label)}" style="{st}" edge="1" '
            f'parent="{parent}" source="{src}" target="{dst}">'
            f'<mxGeometry relative="1" as="geometry"/></mxCell>')


HEADER_H = 46          # 顶部标题条高度
HEADER_GAP = 64        # 内容整体下移量


def page(name, cells, w, h, title=None):
    """组装单页 .drawio。

    draw.io 导出时会按「内容边界」裁剪，若最上层泳道的标题正好贴在最上沿，
    标题行会被裁掉（只剩几个像素的残影）。因此统一在顶部放一条可见的标题条，
    并把全部顶层元素下移 HEADER_GAP，标题行就落在安全区内了。
    """
    if title:
        cells = _shift_top_level(cells, HEADER_GAP)
        cells = v('hdr', title, 40, 20, 1100, HEADER_H,
                  box_style(BRAND, BRAND, font='#FFFFFF', size=17, bold=1)) + cells
        h += HEADER_GAP
    return (f'<mxfile host="Electron" type="device">'
            f'<diagram id="{name}" name="{name}">'
            f'<mxGraphModel dx="1200" dy="900" grid="1" gridSize="10" guides="1" '
            f'tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" '
            f'pageWidth="{w}" pageHeight="{h}" math="0" shadow="0">'
            f'<root><mxCell id="0"/><mxCell id="1" parent="0"/>'
            f'{cells}</root></mxGraphModel></diagram></mxfile>')


def _shift_top_level(cells, dy):
    """把所有顶层元素（parent="1" 的 vertex）的 y 坐标增加 dy；子元素坐标相对父，不动。"""
    def repl(m):
        return f'{m.group(1)}{float(m.group(2)) + dy:.0f}{m.group(3)}'
    return re.sub(
        r'(vertex="1" parent="1"><mxGeometry x="-?[\d.]+" y=")(-?[\d.]+)(")',
        repl, cells)


def row(n, total_w, gap, box_w=None):
    """返回 n 个等宽盒子在 [0,total_w] 内的 x 坐标列表。"""
    if box_w is None:
        box_w = (total_w - gap * (n + 1)) / n
    xs = [gap + i * (box_w + gap) for i in range(n)]
    return xs, box_w


# ---------------------------------------------------------------- 图 1

def diagram_architecture():
    c = []
    W = 1180
    # 层 1：接入层
    c.append(v('l1', '接入层 · 三端 Web + 机器接口', 40, 40, 1100, 104,
               lane_style(*C_ACCESS[::1] if False else (C_ACCESS[0], C_ACCESS[1]))))
    xs, bw = row(4, 1100, 20)
    items = [('用户端 Web', 'Vue 3 + Pinia<br/>:21091', C_ACCESS),
             ('医生端 Web', '独立 doctor-token<br/>doctorRequest.js', C_ACCESS),
             ('管理后台', '菜单按 meta.group<br/>分 6 组', C_ACCESS),
             ('CRM / OpenAPI', 'X-CRM-API-Key<br/>机器对机器', C_ACCESS)]
    for i, (t, s, col) in enumerate(items):
        c.append(v(f'l1n{i}', f'<b>{t}</b><br/><font style="font-size:10px">{s}</font>',
                   xs[i], 38, bw, 56, box_style(col[0], col[1], size=12), parent='l1'))

    c.append(v('a1', '▼', 570, 146, 40, 16, arrow_style()))

    # 层 2：安全与访问控制
    c.append(v('l2', '安全与访问控制', 40, 164, 1100, 104, lane_style(C_SEC[0], C_SEC[1])))
    xs, bw = row(5, 1100, 16)
    items = ['JWT 拦截器', '@Protector<br/>角色注解', 'CrmApiKey<br/>Interceptor',
             'DoctorIsolation<br/>医生数据隔离', '限流 · 审计<br/>audit_log']
    for i, t in enumerate(items):
        c.append(v(f'l2n{i}', t, xs[i], 38, bw, 56,
                   box_style(C_SEC[0], C_SEC[1], size=11), parent='l2'))

    c.append(v('a2', '▼', 570, 270, 40, 16, arrow_style()))

    # 层 3：业务服务层
    c.append(v('l3', '业务服务层 · 领域服务 + Mapper（36 个 Mapper / 265 条 SQL）',
               40, 288, 1100, 104, lane_style(C_BIZ[0], C_BIZ[1])))
    xs, bw = row(6, 1100, 14)
    items = ['健康档案', '预约挂号', '健康商城', '内容社区', '体检报告', '通知中心']
    for i, t in enumerate(items):
        c.append(v(f'l3n{i}', t, xs[i], 38, bw, 56,
                   box_style(C_BIZ[0], C_BIZ[1], size=12), parent='l3'))

    c.append(v('a3', '▼', 570, 394, 40, 16, arrow_style()))

    # 层 4：AI 能力层
    c.append(v('l4', 'AI 能力层', 40, 412, 1100, 186, lane_style(C_AI[0], C_AI[1])))
    xs, bw = row(5, 1100, 14)
    top = ['SeaChatWorkflow<br/>四链路统一路由', 'Multi-Agent<br/>AgentCoordinator',
           'ReAct Agent<br/>工具增强推理', '数据智能体<br/>DataAgent 10 步',
           '混合 RAG<br/>双路召回融合']
    bot = ['知识图谱<br/>248 节点 / 485 边', '语音 TTS / ASR<br/>Provider 化',
           'LLM Provider 工厂<br/>多厂商 + 熔断重试', '提示词模板<br/>10 个 / 595 行',
           '报告渲染<br/>Markdown + ECharts']
    for i, t in enumerate(top):
        c.append(v(f'l4a{i}', t, xs[i], 40, bw, 60,
                   box_style(C_AI[0], C_AI[1], size=11), parent='l4'))
    for i, t in enumerate(bot):
        c.append(v(f'l4b{i}', t, xs[i], 112, bw, 60,
                   box_style(C_AI[0], C_AI[1], size=11), parent='l4'))

    c.append(v('a4', '▼', 570, 600, 40, 16, arrow_style()))

    # 层 5：数据与基础设施
    c.append(v('l5', '数据与基础设施', 40, 618, 1100, 186, lane_style(C_DATA[0], C_DATA[1])))
    xs, bw = row(4, 1100, 16)
    top = ['MySQL 9.7<br/>35 表 / 391 字段', 'Redis<br/>缓存 · 会话版本',
           'Neo4j 5.x<br/>长停降级本地三元组', '向量库<br/>LocalVectorStore']
    bot = ['vLLM 自部署<br/>Qwen2.5-7B 医疗模型', '外部 LLM<br/>DeepSeek / 通义 / OpenAI',
           '文件存储<br/>pic/', '运行时缓存根<br/>cache/']
    for i, t in enumerate(top):
        c.append(v(f'l5a{i}', t, xs[i], 40, bw, 60,
                   box_style(C_DATA[0], C_DATA[1], size=11), parent='l5'))
    for i, t in enumerate(bot):
        c.append(v(f'l5b{i}', t, xs[i], 112, bw, 60,
                   box_style(C_DATA[0], C_DATA[1], size=11), parent='l5'))

    # 底部：交付与可观测
    c.append(v('l6', '交付与可观测（随 docker compose 默认启用）', 40, 824, 1100, 76,
               lane_style(C_INFRA[0], C_INFRA[1])))
    xs, bw = row(4, 1100, 16)
    for i, t in enumerate(['Docker Compose<br/>14 个初始化脚本按序挂载',
                           'Prometheus :9090<br/>指标采集',
                           'Grafana :3000<br/>可视化看板',
                           'Loki :3100 + Promtail<br/>日志聚合']):
        c.append(v(f'l6n{i}', t, xs[i], 36, bw, 34,
                   box_style('#FFFFFF', C_INFRA[1], size=10), parent='l6'))

    return page('system-architecture', ''.join(c), W, 920,
                title='系统总体架构 · 分层视图')


# ---------------------------------------------------------------- 图 2

def diagram_dataagent():
    c = []
    steps = [
        ('1 · 意图识别', 'intent_recognizer', '闲聊 or 数据分析'),
        ('2 · 查询增强', 'query_enhancer', '结合上下文改写'),
        ('3 · 数据表召回', 'schema_recaller', '召回候选表'),
        ('4 · 表关系分析', 'table_relation', '外键 / JOIN 依据'),
        ('5 · SQL 生成', 'sql_generator', '只读 SQL'),
        ('6 · 语义一致性校验', 'semantic_checker', '语义对齐校验'),
        ('7 · 安全守卫', 'sql_guard', '词法校验 · 拒绝写操作'),
        ('8 · 租户隔离重写', 'tenant_rewriter', '逐表覆盖租户条件'),
        ('9 · 执行查询', 'sql_executor', '行数 / 时长上限'),
        ('10 · 生成报告', 'report_generator', 'Markdown 分析报告'),
    ]
    c.append(v('p1', '受控 NL2SQL · 十步流水线（DataAgentPipeline，重试 ≤ 2 次，故障修复重试同样过守卫）',
               40, 40, 1100, 228, lane_style(BRAND_LIGHT, BRAND)))
    xs, bw = row(5, 1100, 14)
    for i, (t, tool, desc) in enumerate(steps[:5]):
        c.append(v(f's{i}', f'{t}&#10;{tool} · {desc}',
                   xs[i], 40, bw, 74, box_style('#FFFFFF', BRAND, size=11), parent='p1'))
    c.append(v('srow', '▼', 570, 116, 40, 22, arrow_style(size=14), parent='p1'))
    for i, (t, tool, desc) in enumerate(steps[5:]):
        # 步骤 7「安全守卫」与 8「租户隔离重写」是安全动作，用警示色区分
        stroke = '#C5221F' if i in (1, 2) else BRAND
        c.append(v(f's{i + 5}', f'{t}&#10;{tool} · {desc}',
                   xs[i], 140, bw, 74,
                   box_style('#FFFFFF', stroke, size=11), parent='p1'))
    for i in range(4):
        c.append(e(f'pe{i}', f's{i}', f's{i + 1}', parent='p1',
                   exit_side='right', entry_side='left'))
        c.append(e(f'pe{i + 5}', f's{i + 5}', f's{i + 6}', parent='p1',
                   exit_side='right', entry_side='left'))

    # 安全唯一出口
    c.append(v('g1', '安全唯一出口 safeRewrite() —— 首次生成与重试修复两条路径都必须经过',
               40, 288, 540, 200, lane_style(C_SEC[0], C_SEC[1])))
    guards = ['MySqlAiSqlGuard<br/>拒绝 DML/DDL/多语句/注释/系统库',
              'TenantSqlRewriter<br/>删除模型写的租户谓词，无条件追加主体条件']
    for i, t in enumerate(guards):
        c.append(v(f'g1n{i}', t, 20, 40 + i * 74, 500, 62,
                   box_style('#FFFFFF', C_SEC[1], size=11), parent='g1'))

    c.append(v('g2', 'fail-closed 四类（命中即拒绝，不尝试改写）', 600, 288, 540, 200,
               lane_style(C_SEC[0], C_SEC[1])))
    fails = ['子查询 / CTE', '隐式逗号连接 FROM a, b', '引用白名单之外的表', '有租户列但无执行主体']
    for i, t in enumerate(fails):
        c.append(v(f'g2n{i}', t, 20, 40 + (i // 2) * 74, 240, 62,
                   box_style('#FFFFFF', C_SEC[1], size=11), parent='g2'))
    # 修正 g2 两列布局
    c[-4] = v('g2n0', fails[0], 20, 40, 240, 62, box_style('#FFFFFF', C_SEC[1], size=11), parent='g2')
    c[-3] = v('g2n1', fails[1], 280, 40, 240, 62, box_style('#FFFFFF', C_SEC[1], size=11), parent='g2')
    c[-2] = v('g2n2', fails[2], 20, 114, 240, 62, box_style('#FFFFFF', C_SEC[1], size=11), parent='g2')
    c[-1] = v('g2n3', fails[3], 280, 114, 240, 62, box_style('#FFFFFF', C_SEC[1], size=11), parent='g2')

    c.append(v('a1', '▼', 570, 270, 40, 16, arrow_style()))

    # 身份与审计
    c.append(v('g3', '身份 → 授权 → 审计（三端不同策略）', 40, 508, 1100, 130,
               lane_style(BRAND_LIGHT2, BRAND)))
    xs, bw = row(4, 1100, 16)
    for i, t in enumerate(['用户端<br/>强制查本人，忽略请求 patientId',
                           '医生端<br/>patientId + 校验 appointment ∪ visit_record',
                           '未授权<br/>fail-closed 并记 audit_log.status=0',
                           'SSE 步骤事件<br/>AgentWorkflow.vue 实时渲染']):
        c.append(v(f'g3n{i}', t, xs[i], 40, bw, 62,
                   box_style('#FFFFFF', BRAND, size=11), parent='g3'))

    c.append(v('note', '自然语言提问 → 真实 SQL → 真实数据 → Markdown 报告（含 ECharts 图表），'
                       '全程不猜测、不伪造数据。', 40, 652, 1100, 24,
               note_style(size=11, align='center')))

    return page('data-agent-pipeline', ''.join(c), 1180, 692,
                title='数据智能体 · 受控 NL2SQL 十步流水线')


# ---------------------------------------------------------------- 图 3

def diagram_rag():
    c = []
    # 顶部：意图路由
    c.append(v('i0', '用户提问', 500, 40, 160, 50,
               box_style(BRAND_LIGHT, BRAND, size=12, bold=1)))
    c.append(v('ir', 'IntentRouter · 规则分类（零 LLM）', 340, 120, 480, 62,
               box_style('#FFFFFF', BRAND, size=12, bold=1)))
    c.append(e('e1', 'i0', 'ir'))
    xs, bw = row(5, 1040, 15)
    xs = [x + 40 for x in xs]
    intents = ['HEALTH_<br/>RECORDS', 'GRAPH', 'DRUG', 'KNOWLEDGE', 'CHAT']
    for i, t in enumerate(intents):
        c.append(v(f'i{i}', t, xs[i], 200, bw, 46,
                   box_style(BRAND_LIGHT2, BRAND, size=11)))
        c.append(e(f'ie{i}', 'ir', f'i{i}', parent='1'))

    # 左：知识入库
    c.append(v('ing', '知识入库', 40, 276, 320, 300, lane_style(C_BIZ[0], C_BIZ[1])))
    ing_items = ['健康资讯 / 药品说明书<br/>体检指标 / 问答对', '文本切片 + 元数据',
                 'EmbeddingService<br/>向量化', 'LocalVectorStore<br/>持久化向量库']
    for i, t in enumerate(ing_items):
        c.append(v(f'ing{i}', t, 20, 40 + i * 62, 280, 52,
                   box_style('#FFFFFF', C_BIZ[1], size=11), parent='ing'))
    for i in range(3):
        c.append(e(f'inge{i}', f'ing{i}', f'ing{i + 1}', parent='ing'))

    # 中：双路召回
    c.append(v('ret', '双路召回 HybridRetriever', 380, 276, 420, 300,
               lane_style(C_AI[0], C_AI[1])))
    c.append(v('ret0', '向量检索<br/>语义相似', 25, 40, 170, 52,
               box_style('#FFFFFF', C_AI[1], size=11), parent='ret'))
    c.append(v('ret1', '关键词检索<br/>精确匹配', 225, 40, 170, 52,
               box_style('#FFFFFF', C_AI[1], size=11), parent='ret'))
    c.append(v('ret2', 'RRF 融合 + 打分<br/>source: vector / keyword / both', 25, 112, 370, 52,
               box_style('#FFFFFF', C_AI[1], size=11), parent='ret'))
    c.append(v('ret3', '重排 → topK 截断', 25, 184, 370, 52,
               box_style('#FFFFFF', C_AI[1], size=11), parent='ret'))
    c.append(e('rete0', 'ret0', 'ret2', parent='ret'))
    c.append(e('rete1', 'ret1', 'ret2', parent='ret'))
    c.append(e('rete2', 'ret2', 'ret3', parent='ret'))
    c.append(e('inge_out', 'ing3', 'ret0', parent='1', exit_side='right', entry_side='top'))

    # 右：生成
    c.append(v('gen', '上下文拼装与生成', 820, 276, 320, 300, lane_style(C_AI[0], C_AI[1])))
    gen_items = ['上下文拼装<br/>（引用可溯源）', 'LLM 生成<br/>（Provider 工厂 / 熔断重试）',
                 '流式输出 + Markdown 渲染', 'DOMPurify 消毒<br/>防存储型 XSS']
    for i, t in enumerate(gen_items):
        c.append(v(f'gen{i}', t, 20, 40 + i * 62, 280, 52,
                   box_style('#FFFFFF', C_AI[1], size=11), parent='gen'))
    for i in range(3):
        c.append(e(f'gene{i}', f'gen{i}', f'gen{i + 1}', parent='gen'))
    c.append(e('ret_out', 'ret3', 'gen0', parent='1', exit_side='right', entry_side='left'))

    # 底部：知识图谱链路
    c.append(v('kg', '知识图谱链路（core/graph，抽取零 LLM）', 40, 596, 1100, 150,
               lane_style(C_DATA[0], C_DATA[1])))
    xs, bw = row(5, 1100, 14)
    kg_items = ['MySQL 健康数据', 'GraphExtractor<br/>医学词表 MedicalVocabulary',
                'Neo4j 5.x<br/>248 节点 / 485 边', 'LocalTripleStore<br/>Neo4j 长停自动降级',
                'QueryGraphTool<br/>图谱问答']
    for i, t in enumerate(kg_items):
        c.append(v(f'kg{i}', t, xs[i], 46, bw, 66,
                   box_style('#FFFFFF', C_DATA[1], size=11), parent='kg'))
    for i in range(4):
        c.append(e(f'kge{i}', f'kg{i}', f'kg{i + 1}', parent='kg',
                   exit_side='right', entry_side='left'))

    return page('rag-knowledge-graph', ''.join(c), 1180, 770,
                title='双路 RAG 检索与知识图谱')


# ---------------------------------------------------------------- 图 4

def diagram_deploy():
    c = []
    c.append(v('cli', '浏览器', 60, 40, 160, 50,
               box_style(C_ACCESS[0], C_ACCESS[1], size=12, bold=1)))
    c.append(v('fe', '前端容器<br/>zhikangyun-frontend<br/><b>:21091</b>', 60, 130, 200, 76,
               box_style('#FFFFFF', C_ACCESS[1], size=11)))
    c.append(v('be', '后端容器<br/>zhikangyun-backend<br/><b>:21090</b> · Spring Boot 3', 60, 246, 200, 90,
               box_style('#FFFFFF', BRAND, size=11, bold=0)))
    c.append(e('e1', 'cli', 'fe'))
    c.append(e('e2', 'fe', 'be'))
    c.append(e('e3', 'be', 'fe', label='API', parent='1', style=edge_style(BRAND, 1.4, dashed=1),
               exit_side='top', entry_side='bottom'))

    # 后端依赖
    c.append(v('deps', '后端依赖服务', 340, 130, 380, 260, lane_style(C_DATA[0], C_DATA[1])))
    deps = ['MySQL 8.0 <b>:3306</b><br/>14 个初始化脚本按序挂载', 'Redis <b>:6379</b><br/>缓存 / 会话版本',
            'vLLM <b>:8000</b><br/>Qwen2.5-7B 医疗模型', '外部 LLM API<br/>DeepSeek / 通义 / OpenAI']
    for i, t in enumerate(deps):
        c.append(v(f'd{i}', t, 25, 40 + i * 56, 330, 46,
                   box_style('#FFFFFF', C_DATA[1], size=11), parent='deps'))
    c.append(e('be_dep', 'be', 'deps', parent='1', exit_side='right', entry_side='left'))

    # 可观测
    c.append(v('obs', '可观测（随 compose 默认启用）', 780, 130, 360, 260,
               lane_style(C_INFRA[0], C_INFRA[1])))
    obs = ['Prometheus <b>:9090</b><br/>指标采集', 'Grafana <b>:3000</b><br/>可视化看板',
           'Alertmanager <b>:9093</b><br/>告警路由', 'Loki <b>:3100</b> + Promtail<br/>日志聚合']
    for i, t in enumerate(obs):
        c.append(v(f'o{i}', t, 25, 40 + i * 56, 310, 46,
                   box_style('#FFFFFF', C_INFRA[1], size=11), parent='obs'))
    c.append(e('be_obs', 'be', 'obs', parent='1',
               exit_side='right', entry_side='left'))

    # 网络与卷
    c.append(v('net', '网络：backend-net（后端与数据面）· frontend-net（前端与接入面）<br/>'
                      '持久化卷：mysql_data · backend_data · backend_logs · backend_cache · '
                      'redis_data · prometheus_data · grafana_data · alertmanager_data · loki_data',
               60, 420, 1080, 56, box_style('#FFFFFF', C_INFRA[1], size=11)))

    c.append(v('init', '数据库初始化顺序（文件名前缀决定字典序）：<br/>'
                       '01-init → 02-forum → 03-appointment → 04-extra → 05-ai-usage → 06-audit → '
                       '07-model-announcement →（迁移）08…14 → 终态 <b>35 表 / 391 字段</b>',
               60, 490, 1080, 60, box_style(BRAND_LIGHT, BRAND, size=11)))

    c.append(v('note', '一条命令起全栈：<font face="Courier New">docker compose up -d</font>　·　'
                       '缺任一密钥环境变量服务会拒绝启动（fail-fast）',
               60, 564, 1080, 24, note_style(size=11)))

    return page('deployment-topology', ''.join(c), 1180, 610,
                title='部署拓扑与可观测')


# ---------------------------------------------------------------- 图 5

def diagram_product():
    c = []
    cols = [
        ('用户端', C_ACCESS, ['AI 健康问诊<br/>多轮对话 · 流式输出',
                              '健康档案与趋势<br/>指标录入 · 可视化',
                              '体检报告解读<br/>异常项识别',
                              '预约挂号<br/>排班 · 时段',
                              '健康商城<br/>商品 · 订单 · 评论',
                              '健康社区<br/>资讯 · 互动',
                              '数据智能体<br/>自然语言查自己的数据',
                              '语音助手<br/>TTS / ASR']),
        ('医生端', C_BIZ, ['登录独立<br/>doctor-token',
                           '患者档案查阅<br/>受授权约束',
                           '排班管理',
                           '问诊回复',
                           '数据智能体<br/>仅可查已授权患者',
                           '报告查看']),
        ('管理后台', C_AI, ['用户与角色管理',
                            '内容管理<br/>资讯 · 分类 · 公告',
                            '订单与商品管理',
                            'AI 配置<br/>Provider · 提示词 · 成本',
                            '数据智能体管理<br/>开关 · 会话 · 预设',
                            '知识图谱管理',
                            '审计日志<br/>audit_log',
                            '系统监控']),
    ]
    xs, bw = row(3, 1100, 20)
    max_n = max(len(items) for _, _, items in cols)   # 三列等高，底部对齐
    lane_h = 40 + max_n * 62 + 20
    for ci, (title, col, items) in enumerate(cols):
        c.append(v(f'c{ci}', title, xs[ci], 40, bw, lane_h, lane_style(col[0], col[1])))
        for i, t in enumerate(items):
            c.append(v(f'c{ci}n{i}', t, 18, 40 + i * 62, bw - 36, 52,
                       box_style('#FFFFFF', col[1], size=11), parent=f'c{ci}'))

    c.append(v('foot', '三端共用：AI 问诊 · 数据智能体 · 语音能力；权限与数据隔离在各端独立实现。',
               40, 40 + lane_h + 16, 1100, 24, note_style(size=11, align='center')))

    return page('product-function-map', ''.join(c), 1180, 40 + lane_h + 80,
                title='产品功能全景 · 用户端 / 医生端 / 管理后台')


# ---------------------------------------------------------------- 图 6

def diagram_security():
    c = []
    layers = [
        ('接入层', C_ACCESS, ['HTTPS / 反向代理', 'CORS 白名单', '请求体大小限制']),
        ('认证层', C_SEC, ['JWT（强密钥校验，弱密钥拒绝启动）', '医生端独立 doctor-token',
                           'CRM 接口 X-CRM-API-Key']),
        ('授权与隔离层', C_SEC, ['@Protector 角色注解（管理员 / 用户 / 医生）',
                                 'DoctorIsolation 单点隔离',
                                 '用户端强制查本人，忽略请求 patientId']),
        ('数据与 SQL 层', C_DATA, ['MySqlAiSqlGuard 词法守卫（只读）',
                                   'TenantSqlRewriter 逐表租户覆盖',
                                   '参数化查询 · 行数与时长硬上限']),
        ('输出与审计层', C_BIZ, ['DOMPurify 消毒（防存储型 XSS）',
                                 '敏感字段脱敏 · PII 最小化',
                                 'audit_log 全量审计 · 未授权 fail-closed 记 status=0']),
    ]
    y = 40
    prev = None
    for i, (title, col, items) in enumerate(layers):
        lane_h = 92
        c.append(v(f's{i}', f'{i + 1} · {title}', 40, y, 1100, lane_h,
                   lane_style(col[0], col[1])))
        xs, bw = row(len(items), 1100, 16)
        for j, t in enumerate(items):
            c.append(v(f's{i}n{j}', t, xs[j], 38, bw, 40,
                       box_style('#FFFFFF', col[1], size=11), parent=f's{i}'))
        if prev is not None:
            c.append(v(f'sa{i}', '▼', 570, y - 18, 40, 16, arrow_style()))
        y += lane_h + 18
        prev = i

    c.append(v('note', '设计原则：默认拒绝（fail-closed）· 单点隔离 · 密钥外部化（不入库、不硬编码）· 全链路可审计',
               40, y, 1100, 24, note_style(size=11, align='center')))

    return page('security-architecture', ''.join(c), 1180, y + 40,
                title='安全纵深防御 · 五层')


# ---------------------------------------------------------------- 主流程

DIAGRAMS = {
    '01-system-architecture': diagram_architecture,
    '02-data-agent-pipeline': diagram_dataagent,
    '03-rag-knowledge-graph': diagram_rag,
    '04-deployment-topology': diagram_deploy,
    '05-product-function-map': diagram_product,
    '06-security-architecture': diagram_security,
}


def main():
    args = sys.argv[1:]
    src_only = '--src-only' in args
    os.makedirs(ASSETS, exist_ok=True)

    for name, fn in DIAGRAMS.items():
        xml = fn()
        drawio_path = os.path.join(ASSETS, name + '.drawio')
        with open(drawio_path, 'w', encoding='utf-8') as f:
            f.write(xml)
        print(f'[drawio] {os.path.relpath(drawio_path, ROOT)}  ({len(xml)} B)', flush=True)

    if src_only:
        print('\n已生成 .drawio 源文件（跳过导出）。')
        return

    if not os.path.exists(DRAWIO_CLI):
        print(f'\n未找到 draw.io CLI：{DRAWIO_CLI}\n'
              f'已生成 .drawio 源文件，可手动用 draw.io 打开导出。')
        return

    # draw.io 桌面版是**单实例**应用：若上一次导出的 Electron 进程没退干净，
    # 下一次调用会被转发给残留实例而永久挂住。导出前先检查一次，有残留就提示。
    leftover = 0
    try:
        out = subprocess.run(['tasklist', '/FI', 'IMAGENAME eq draw.io.exe'],
                             capture_output=True, text=True, errors='ignore', timeout=20).stdout
        leftover = out.lower().count('draw.io.exe')
    except Exception:
        pass
    if leftover > 2:
        print(f'\n⚠️ 检测到 {leftover} 个残留的 draw.io 进程，可能阻塞导出。\n'
              f'   建议先执行：taskkill /F /IM draw.io.exe  再重跑本脚本。\n', flush=True)

    # 批量导出：一次 Electron 启动处理整个目录（逐个文件调用会各启一次，非常慢）。
    # SVG 导出会被 draw.io 内嵌成位图（体积 1 MB 级且无矢量优势），故默认只导 PNG。
    formats = [('png', ['-s', '1.5'])]
    if '--svg' in args:
        formats.append(('svg', []))
    for fmt, extra in formats:
        print(f'\n[export] {fmt} ...', flush=True)
        ok = _export_batch(fmt, extra, list(DIAGRAMS))
        for name in DIAGRAMS:
            out = os.path.join(ASSETS, f'{name}.{fmt}')
            if os.path.exists(out):
                print(f'  OK  {name}.{fmt}  {os.path.getsize(out)} B', flush=True)
            else:
                print(f'  --  {name}.{fmt}  未生成', flush=True)

    print('\n完成。图片输出目录：docs/assets/')


def _export_batch(fmt, extra, names, timeout=300):
    """批量导出并**主动回收 draw.io 进程**。

    draw.io 桌面版在 Windows 上跑完 `-x` 导出后经常不退出（Electron 事件循环
    没结束）。残留实例会占用单实例锁，导致下一次调用被转发过去而永久挂住 ——
    表现为「第一次成功、第二次卡死」。所以这里改成：起进程 → 轮询输出文件 →
    文件齐了就 taskkill 掉整棵进程树。
    """
    import time

    outs = {n: os.path.join(ASSETS, f'{n}.{fmt}') for n in names}
    for p in outs.values():                      # 先删旧的，否则轮询会误判为已完成
        if os.path.exists(p):
            os.remove(p)

    cmd = [DRAWIO_CLI, '-x', '-f', fmt] + extra + ['-o', ASSETS, ASSETS]
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    deadline = time.time() + timeout
    while time.time() < deadline:
        if all(os.path.exists(p) for p in outs.values()):
            time.sleep(2)                        # 留出写盘时间
            break
        if proc.poll() is not None:              # 进程自己退了
            break
        time.sleep(2)

    try:
        subprocess.run(['taskkill', '/F', '/T', '/PID', str(proc.pid)],
                       capture_output=True, timeout=30)
    except Exception:
        pass
    return all(os.path.exists(p) for p in outs.values())


if __name__ == '__main__':
    main()
