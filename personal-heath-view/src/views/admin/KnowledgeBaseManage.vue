<template>
  <div class="kbm">
    <div class="kbm-head">
      <div>
        <h3 class="kbm-head__title">知识库管理</h3>
        <p class="kbm-head__desc">
          建库 → 上传文档（PDF / Word / Excel / 粘贴文本）→ 自动分块入库并建立向量索引。
          智能体通过 <code>search_knowledge_base</code> 工具检索这里的内容。
        </p>
      </div>
      <el-button type="primary" @click="openKbDialog()">新建知识库</el-button>
    </div>

    <el-tabs v-model="tab" class="kbm-tabs">
      <!-- ==================== 知识库 ==================== -->
      <el-tab-pane label="知识库" name="kb">
        <el-table :data="kbRows" border size="small" v-loading="kbLoading" class="kbm-table">
          <el-table-column prop="id" label="ID" width="60" align="center" />
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="code" label="标识" width="130">
            <template #default="{ row }">
              <code class="kbm-code">{{ row.code }}</code>
            </template>
          </el-table-column>
          <el-table-column label="适用端" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ audienceText(row.audience) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分块" width="110" align="center">
            <template #default="{ row }">
              <span class="kbm-num">{{ row.chunkSize }} / {{ row.chunkOverlap }}</span>
              <div class="kbm-sub">长度 / 重叠</div>
            </template>
          </el-table-column>
          <el-table-column label="召回" width="110" align="center">
            <template #default="{ row }">
              <span class="kbm-num">{{ row.topK }} 条 · 权重 {{ row.weight }}</span>
            </template>
          </el-table-column>
          <el-table-column label="规模" width="120" align="center">
            <template #default="{ row }">
              <span class="kbm-num">{{ row.docCount || 0 }} 篇 / {{ row.chunkCount || 0 }} 块</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                {{ row.enabled ? "启用" : "停用" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDocs(row)">文档</el-button>
              <el-button link type="primary" size="small" @click="openKbDialog(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="loadKbDetail(row)">详情</el-button>
              <el-button link type="danger" size="small" @click="removeKb(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="!kbLoading && kbRows.length === 0"
          type="info"
          :closable="false"
          show-icon
          class="kbm-tip"
          title="还没有知识库，或知识库表尚未建立"
          description="若尚未执行 Data/02_迁移脚本/11_knowledge_base.sql，请先执行；执行后刷新本页。"
        />
      </el-tab-pane>

      <!-- ==================== 文档 ==================== -->
      <el-tab-pane label="文档" name="doc" :disabled="!currentKb">
        <div v-if="currentKb" class="kbm-doc-bar">
          <div class="kbm-doc-bar__title">
            当前知识库：<b>{{ currentKb.name }}</b>
            <code class="kbm-code">{{ currentKb.code }}</code>
          </div>
          <div class="kbm-doc-bar__actions">
            <el-upload
              :http-request="doUpload"
              :show-file-list="false"
              :accept="acceptAttr"
              :disabled="uploading"
            >
              <el-button type="primary" :loading="uploading">上传文件</el-button>
            </el-upload>
            <el-button @click="textDialog = true">粘贴文本</el-button>
            <el-button @click="loadDocs">刷新</el-button>
          </div>
        </div>

        <el-alert
          v-if="currentKb"
          type="info"
          :closable="false"
          show-icon
          class="kbm-tip"
          :title="'支持格式：' + formatText"
          :description="
            '单个文件上限 10MB（spring.servlet.multipart.max-file-size 与 crm.rag.parse.max-bytes 需一致）。' +
            ' 内容指纹相同的文档会被拒绝，避免同一份资料重复入库。'
          "
        />

        <el-table :data="docRows" border size="small" v-loading="docLoading" class="kbm-table">
          <el-table-column prop="id" label="ID" width="60" align="center" />
          <el-table-column prop="title" label="标题" min-width="150" />
          <el-table-column prop="fileType" label="类型" width="80" align="center" />
          <el-table-column label="大小" width="90" align="center">
            <template #default="{ row }">{{ sizeText(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="来源" width="90" align="center">
            <template #default="{ row }">{{ sourceText(row.sourceType) }}</template>
          </el-table-column>
          <el-table-column label="块数" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openChunks(row)">
                {{ row.chunkCount || 0 }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="上传时间" width="160" />
          <el-table-column label="操作" width="180" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="reindexDoc(row)">重建索引</el-button>
              <el-button link type="danger" size="small" @click="removeDoc(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="docRows.some((d) => d.status === 'FAILED')" class="kbm-failed">
          <div v-for="d in docRows.filter((x) => x.status === 'FAILED')" :key="d.id" class="kbm-failed__row">
            <b>{{ d.title }}</b> 解析失败：{{ d.errorMsg || "无错误信息" }}
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 数据源 ==================== -->
      <el-tab-pane label="数据源" name="ds">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="kbm-tip"
          title="知识块默认存在本应用主库"
          description="只有在需要把远端库的资料收进知识库时，才在这里登记远端 MySQL 连接。远端连接一律使用只读账号，且取数 SQL 必须通过只读校验。"
        />
        <div class="kbm-doc-bar">
          <div class="kbm-doc-bar__title">已登记的数据源</div>
          <div class="kbm-doc-bar__actions">
            <el-button type="primary" @click="openDsDialog()">新建数据源</el-button>
            <el-button @click="loadDataSources">刷新</el-button>
          </div>
        </div>

        <el-table :data="dsRows" border size="small" v-loading="dsLoading" class="kbm-table">
          <el-table-column prop="id" label="ID" width="60" align="center" />
          <el-table-column prop="name" label="名称" min-width="130" />
          <el-table-column prop="code" label="标识" width="120" />
          <el-table-column prop="type" label="类型" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.type === 'remote' ? 'warning' : 'info'">
                {{ row.type === "remote" ? "远端" : "主库" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="jdbcUrl" label="连接串" min-width="200" show-overflow-tooltip />
          <el-table-column prop="username" label="账号" width="110" />
          <el-table-column label="连通性" width="150" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.lastCheckOk === true" type="success" size="small">正常</el-tag>
              <el-tag v-else-if="row.lastCheckOk === false" type="danger" size="small">失败</el-tag>
              <el-tag v-else type="info" size="small">未检测</el-tag>
              <div v-if="row.lastCheckTime" class="kbm-sub">{{ row.lastCheckTime }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="290" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="checkDs(row)">测试连接</el-button>
              <el-button link type="primary" size="small" @click="openPreview(row)">试跑取数</el-button>
              <el-button link type="primary" size="small" @click="openDsDialog(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeDs(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 检索质量 ==================== -->
      <el-tab-pane label="检索质量" name="quality">
        <div class="kbm-quality-bar">
          <el-radio-group v-model="summaryDays" size="small" @change="loadSummary">
            <el-radio-button :value="1">今天</el-radio-button>
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
          </el-radio-group>
          <div>
            <el-button size="small" @click="loadSummary">刷新</el-button>
            <el-button size="small" @click="purgeLogs">清理过期日志</el-button>
          </div>
        </div>

        <div class="kbm-cards">
          <div class="kbm-card">
            <div class="kbm-card__label">检索总次数</div>
            <div class="kbm-card__value">{{ summary.total || 0 }}</div>
            <div class="kbm-card__desc">含空结果</div>
          </div>
          <div class="kbm-card">
            <div class="kbm-card__label">两路都召回</div>
            <div class="kbm-card__value">{{ summary.hybrid || 0 }}</div>
            <div class="kbm-card__desc">词法 + 向量，最可信的一类</div>
          </div>
          <div class="kbm-card">
            <div class="kbm-card__label">仅词法召回</div>
            <div class="kbm-card__value">{{ summary.sparseOnly || 0 }}</div>
            <div class="kbm-card__desc">可能确实是术语查询</div>
          </div>
          <div class="kbm-card">
            <div class="kbm-card__label">仅向量召回</div>
            <div class="kbm-card__value">{{ summary.denseOnly || 0 }}</div>
            <div class="kbm-card__desc">口语化提问的典型形态</div>
          </div>
          <div class="kbm-card" :class="{ 'kbm-card--warn': degradePct >= 30 }">
            <div class="kbm-card__label">退化率</div>
            <div class="kbm-card__value">{{ degradePct }}%</div>
            <div class="kbm-card__desc">向量路无召回且走了 LIKE 兜底</div>
          </div>
          <div class="kbm-card">
            <div class="kbm-card__label">空结果</div>
            <div class="kbm-card__value">{{ summary.emptyResult || 0 }}</div>
            <div class="kbm-card__desc">含「一个启用的知识库都没有」</div>
          </div>
          <div class="kbm-card">
            <div class="kbm-card__label">平均耗时</div>
            <div class="kbm-card__value">{{ summary.avgLatency != null ? summary.avgLatency : "—" }}</div>
            <div class="kbm-card__desc">毫秒</div>
          </div>
        </div>

        <el-alert
          v-if="degradePct >= 30"
          type="warning"
          :closable="false"
          show-icon
          class="kbm-tip"
          title="退化率偏高"
          description="大量检索都没能走通向量路、只能靠 LIKE 兜底。请检查 EMBEDDING_API_URL 是否可达、kb_chunk 上是否已建立 ngram 全文索引（11_knowledge_base.sql）。"
        />

        <el-table v-if="byPathRows.length" :data="byPathRows" border size="small" class="kbm-table">
          <el-table-column prop="path" label="检索路径" min-width="130" />
          <el-table-column prop="total" label="次数" width="100" align="center" />
          <el-table-column label="占比" min-width="180">
            <template #default="{ row }">
              <el-progress :percentage="pathPct(row.total)" :stroke-width="10" />
            </template>
          </el-table-column>
          <el-table-column label="平均耗时" width="110" align="center">
            <template #default="{ row }">{{ row.avgLatency }} ms</template>
          </el-table-column>
        </el-table>

        <div class="kbm-doc-bar">
          <div class="kbm-doc-bar__title">最近检索日志</div>
          <div class="kbm-doc-bar__actions">
            <el-select v-model="logQuery.path" clearable placeholder="全部路径" size="small" style="width: 150px" @change="loadLogs">
              <el-option v-for="p in PATH_OPTIONS" :key="p" :label="p" :value="p" />
            </el-select>
            <el-input
              v-model="logQuery.queryText"
              clearable
              placeholder="按问题内容搜索"
              size="small"
              style="width: 200px"
              @keyup.enter="loadLogs"
            />
            <el-button size="small" @click="loadLogs">查询</el-button>
          </div>
        </div>

        <el-table :data="logRows" border size="small" v-loading="logLoading" class="kbm-table">
          <el-table-column prop="queryText" label="问题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="path" label="路径" width="110" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="pathTagType(row.path)">{{ row.path }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="召回（词法/向量/双路/最终）" width="200" align="center">
            <template #default="{ row }">
              <span class="kbm-num">
                {{ row.sparseHits }} / {{ row.denseHits }} / {{ row.bothHits }} / {{ row.fusedHits }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90" align="center">
            <template #default="{ row }">{{ row.latencyMs }} ms</template>
          </el-table-column>
          <el-table-column prop="audience" label="受众" width="90" align="center" />
          <el-table-column prop="createTime" label="时间" width="160" />
        </el-table>

        <el-pagination
          v-if="logTotal > logQuery.size"
          class="kbm-pager"
          layout="total, prev, pager, next"
          :total="logTotal"
          :page-size="logQuery.size"
          :current-page="logQuery.current"
          @current-change="onLogPage"
        />

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="kbm-tip"
          title="召回率 / 精确率在「RAG 监控」页"
          description="本页统计的是「检索链路是否健康」（走了哪条路、退化了吗）；召回率与精确率需要人工标好的金标评测集，属于检索质量评估，在「RAG 监控 → 召回率 / 精确率」里跑。"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 知识库新建 / 编辑 ==================== -->
    <el-dialog v-model="kbDialog" :title="kbForm.id ? '编辑知识库' : '新建知识库'" width="620px">
      <el-form label-width="130px">
        <el-form-item label="标识" required>
          <el-input v-model="kbForm.code" :disabled="!!kbForm.id" placeholder="小写字母/数字/下划线，如 drug_manual" />
          <div class="kbm-hint">建库后不可修改：它会被写进向量集合名与检索日志</div>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="kbForm.name" placeholder="如 药品说明书库" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="kbForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="适用端">
          <el-select v-model="kbForm.audience" style="width: 180px">
            <el-option label="全部" value="all" />
            <el-option label="普通用户" value="user" />
            <el-option label="医生" value="doctor" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="kbForm.dataSourceId" clearable placeholder="默认：本应用主库" style="width: 260px">
            <el-option
              v-for="ds in dsRows"
              :key="ds.id"
              :label="ds.name + (ds.type === 'remote' ? '（远端）' : '')"
              :value="ds.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分块长度">
          <el-input-number v-model="kbForm.chunkSize" :min="20" :max="4000" />
          <span class="kbm-hint">字符。下限 20，上限 4000；说明书宜小、科普长文宜大</span>
        </el-form-item>
        <el-form-item label="分块重叠">
          <el-input-number v-model="kbForm.chunkOverlap" :min="0" :max="2000" />
          <span class="kbm-hint">不得超过分块长度的一半，否则相邻块大量重复</span>
        </el-form-item>
        <el-form-item label="默认召回条数">
          <el-input-number v-model="kbForm.topK" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="检索权重">
          <el-input-number v-model="kbForm.weight" :min="0" :max="9.999" :step="0.1" :precision="3" />
          <span class="kbm-hint">多条知识库同时命中时，权重高的库排名更靠前（0 ~ 9.999）</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="kbForm.enabled" />
          <span class="kbm-hint">停用后即便被显式指定也不会参与检索</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialog = false">取消</el-button>
        <el-button type="primary" :loading="kbSaving" @click="saveKb">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 粘贴文本 ==================== -->
    <el-dialog v-model="textDialog" title="粘贴文本入库" width="640px">
      <el-form label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="textForm.title" placeholder="这份资料的名称" />
        </el-form-item>
        <el-form-item label="适用端">
          <el-select v-model="textForm.audience" style="width: 180px">
            <el-option label="跟随知识库" value="" />
            <el-option label="全部" value="all" />
            <el-option label="普通用户" value="user" />
            <el-option label="医生" value="doctor" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="textForm.content" type="textarea" :rows="12" placeholder="直接粘贴正文，会自动按知识库的分块参数切块" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="textDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="doUploadText">入库</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 数据源新建 / 编辑 ==================== -->
    <el-dialog v-model="dsDialog" :title="dsForm.id ? '编辑数据源' : '新建数据源'" width="620px">
      <el-form label-width="120px">
        <el-form-item label="标识" required>
          <el-input v-model="dsForm.code" :disabled="!!dsForm.id" placeholder="如 remote_emr" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="dsForm.name" placeholder="如 远端病历库" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="dsForm.type">
            <el-radio value="local">主库</el-radio>
            <el-radio value="remote">远端 MySQL</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="dsForm.type === 'remote'">
          <el-form-item label="连接串" required>
            <el-input
              v-model="dsForm.jdbcUrl"
              placeholder="jdbc:mysql://host:3306/db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
            />
          </el-form-item>
          <el-form-item label="账号" required>
            <el-input v-model="dsForm.username" placeholder="只读账号，如 ai_ro" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="dsForm.password"
              type="password"
              show-password
              :placeholder="dsForm.id ? '留空表示不修改' : '明文输入，落库前加密'"
            />
            <div class="kbm-hint">密码以密文存储，列表接口不会返回它</div>
          </el-form-item>
        </template>
        <el-form-item label="备注">
          <el-input v-model="dsForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="dsForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsDialog = false">取消</el-button>
        <el-button type="primary" :loading="dsSaving" @click="saveDs">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 只读取数试跑 ==================== -->
    <el-dialog v-model="previewDialog" title="只读取数试跑" width="760px">
      <div class="kbm-hint" style="margin-bottom: 8px">
        仅允许单条 SELECT，通过只读校验后才会执行。非 SELECT 语句一律拒绝。
      </div>
      <el-input v-model="previewSql" type="textarea" :rows="4" placeholder="SELECT id, name FROM patient LIMIT 20" />
      <el-button type="primary" size="small" style="margin-top: 10px" :loading="previewing" @click="runPreview">
        执行
      </el-button>
      <el-table v-if="previewRows.length" :data="previewRows" border size="small" style="margin-top: 12px">
        <el-table-column
          v-for="col in previewCols"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <el-alert v-if="previewError" type="error" :closable="false" show-icon style="margin-top: 12px" :title="previewError" />
    </el-dialog>

    <!-- ==================== 分块预览抽屉 ==================== -->
    <el-drawer v-model="chunkDrawer" title="分块预览" size="620px">
      <div class="kbm-hint" style="margin-bottom: 10px">
        分块效果直接决定召回精度。块太长会定位不准，太短则上下文断裂。
      </div>
      <div v-for="c in chunkRows" :key="c.id" class="kbm-chunk">
        <div class="kbm-chunk__head">
          <span>#{{ c.chunkIndex }}</span>
          <span>{{ c.contentLen }} 字</span>
          <el-tag v-if="c.enabled === false" type="info" size="small">已停用</el-tag>
        </div>
        <div class="kbm-chunk__body">{{ c.content }}</div>
      </div>
      <el-empty v-if="chunkRows.length === 0" description="该文档还没有文本块" />
    </el-drawer>
  </div>
</template>

<script>
import request from "@/utils/request.js";

const EMPTY_KB = () => ({
  id: null,
  code: "",
  name: "",
  description: "",
  dataSourceId: null,
  audience: "all",
  chunkSize: 200,
  chunkOverlap: 50,
  topK: 5,
  weight: 1,
  enabled: true,
});

const EMPTY_DS = () => ({
  id: null,
  code: "",
  name: "",
  type: "remote",
  jdbcUrl: "",
  username: "",
  password: "",
  remark: "",
  enabled: true,
});

export default {
  name: "KnowledgeBaseManage",
  data() {
    return {
      tab: "kb",
      PATH_OPTIONS: ["hybrid", "sparse_only", "dense_only", "sparse_like", "none"],

      // 知识库
      kbRows: [],
      kbLoading: false,
      kbDialog: false,
      kbSaving: false,
      kbForm: EMPTY_KB(),
      currentKb: null,

      // 文档
      docRows: [],
      docLoading: false,
      uploading: false,
      formats: [],
      textDialog: false,
      textForm: { title: "", content: "", audience: "" },
      chunkDrawer: false,
      chunkRows: [],

      // 数据源
      dsRows: [],
      dsLoading: false,
      dsDialog: false,
      dsSaving: false,
      dsForm: EMPTY_DS(),
      previewDialog: false,
      previewSql: "",
      previewRows: [],
      previewError: "",
      previewing: false,
      previewDsId: null,

      // 检索质量
      summaryDays: 7,
      summary: {},
      byPathRows: [],
      logRows: [],
      logLoading: false,
      logTotal: 0,
      logQuery: { queryText: "", path: "", current: 1, size: 15 },
    };
  },
  computed: {
    acceptAttr() {
      return this.formats.length ? this.formats.map((f) => "." + f).join(",") : "";
    },
    formatText() {
      return this.formats.length ? this.formats.join(" / ") : "PDF / Word / Excel / 文本";
    },
    degradePct() {
      const rate = this.summary.degradeRate;
      return rate == null ? 0 : Math.round(rate * 100);
    },
    previewCols() {
      return this.previewRows.length ? Object.keys(this.previewRows[0]) : [];
    },
  },
  created() {
    this.loadKbs();
    this.loadFormats();
    this.loadDataSources();
  },
  watch: {
    tab(v) {
      if (v === "quality" && !this.logRows.length) {
        this.loadSummary();
        this.loadLogs();
      }
    },
  },
  methods: {
    // ---------------------------------------------------------------- 通用
    ok(res) {
      return res && res.data && res.data.code === 200;
    },
    payload(res) {
      return (res && res.data && res.data.data) || null;
    },
    toast(res, fallback) {
      const msg = (res && res.data && res.data.msg) || fallback;
      this.$message.error(msg);
    },

    // ---------------------------------------------------------------- 知识库
    async loadKbs() {
      this.kbLoading = true;
      try {
        const res = await request.post("rag/admin/kb/query", { current: 1, size: 100 });
        if (this.ok(res)) {
          this.kbRows = this.payload(res) || [];
          // 当前知识库可能已被删除或改名，保持列表与详情一致
          if (this.currentKb) {
            const fresh = this.kbRows.find((k) => k.id === this.currentKb.id);
            if (fresh) this.currentKb = fresh;
          }
        } else {
          this.toast(res, "加载知识库失败");
        }
      } catch (e) {
        this.$message.error("加载知识库失败");
      } finally {
        this.kbLoading = false;
      }
    },
    openKbDialog(row) {
      this.kbForm = row ? { ...EMPTY_KB(), ...row } : EMPTY_KB();
      this.kbDialog = true;
    },
    async saveKb() {
      this.kbSaving = true;
      try {
        const body = { ...this.kbForm };
        const res = this.kbForm.id
          ? await request.put("rag/admin/kb/update", body)
          : await request.post("rag/admin/kb/save", body);
        if (this.ok(res)) {
          this.$message.success("已保存");
          this.kbDialog = false;
          this.loadKbs();
        } else {
          this.toast(res, "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败");
      } finally {
        this.kbSaving = false;
      }
    },
    async loadKbDetail(row) {
      try {
        const res = await request.get(`rag/admin/kb/${row.id}`);
        if (!this.ok(res)) {
          this.toast(res, "加载详情失败");
          return;
        }
        const d = this.payload(res) || {};
        const vec = d.vector || {};
        const lines = [
          `名称：${(d.knowledgeBase || {}).name || "-"}`,
          `文本块总数：${d.chunkTotal != null ? d.chunkTotal : "-"}`,
          `向量集合：${vec.collection || "-"}`,
          `向量条数：${vec.count != null ? vec.count : "不可用"}`,
        ];
        this.$alert(lines.join("<br/>"), "知识库详情", { dangerouslyUseHTMLString: true });
      } catch (e) {
        this.$message.error("加载详情失败");
      }
    },
    async removeKb(row) {
      try {
        await this.$confirm(
          `删除知识库「${row.name}」？库内的文档、文本块与向量索引会一并清理。库内还有文本块时会被拒绝。`,
          "确认删除",
          { type: "warning" }
        );
      } catch (e) {
        return;
      }
      try {
        const res = await request.delete(`rag/admin/kb/${row.id}`);
        if (this.ok(res)) {
          this.$message.success("已删除");
          if (this.currentKb && this.currentKb.id === row.id) this.currentKb = null;
          this.loadKbs();
        } else {
          this.toast(res, "删除失败");
        }
      } catch (e) {
        this.$message.error("删除失败");
      }
    },

    // ---------------------------------------------------------------- 文档
    openDocs(row) {
      this.currentKb = row;
      this.tab = "doc";
      this.loadDocs();
    },
    async loadDocs() {
      if (!this.currentKb) return;
      this.docLoading = true;
      try {
        const res = await request.post("rag/admin/doc/query", {
          kbId: this.currentKb.id,
          current: 1,
          size: 100,
        });
        if (this.ok(res)) {
          this.docRows = this.payload(res) || [];
        } else {
          this.toast(res, "加载文档失败");
        }
      } catch (e) {
        this.$message.error("加载文档失败");
      } finally {
        this.docLoading = false;
      }
    },
    async loadFormats() {
      try {
        const res = await request.get("rag/admin/formats");
        if (this.ok(res)) this.formats = this.payload(res) || [];
      } catch (e) {
        // 拿不到格式清单不影响主流程：accept 留空即不限制，由后端兜底校验
      }
    },
    doUpload(opt) {
      this.uploading = true;
      const form = new FormData();
      form.append("kbId", this.currentKb.id);
      form.append("file", opt.file);
      request
        .post("rag/admin/doc/upload", form, {
          headers: { "Content-Type": "multipart/form-data" },
        })
        .then((res) => {
          // 业务失败（超限、重复指纹、解析失败）走的是 HTTP 200 + code != 200，
          // 属于正常返回而非网络异常，两者都要回调给 el-upload 以免它把本次上传
          // 一直挂在「进行中」。
          if (this.ok(res)) {
            this.$message.success("已入库");
            this.loadDocs();
            this.loadKbs();
            if (opt.onSuccess) opt.onSuccess(res);
          } else {
            this.toast(res, "上传失败");
            if (opt.onError) opt.onError(new Error((res.data && res.data.msg) || "上传失败"));
          }
        })
        .catch((e) => {
          this.$message.error("上传失败");
          if (opt.onError) opt.onError(e);
        })
        .finally(() => {
          this.uploading = false;
        });
    },
    async doUploadText() {
      if (!this.textForm.title.trim() || !this.textForm.content.trim()) {
        this.$message.warning("标题与正文都不能为空");
        return;
      }
      this.uploading = true;
      try {
        const res = await request.post("rag/admin/doc/text", {
          kbId: this.currentKb.id,
          title: this.textForm.title,
          content: this.textForm.content,
          audience: this.textForm.audience || null,
        });
        if (this.ok(res)) {
          this.$message.success("已入库");
          this.textDialog = false;
          this.textForm = { title: "", content: "", audience: "" };
          this.loadDocs();
          this.loadKbs();
        } else {
          this.toast(res, "入库失败");
        }
      } catch (e) {
        this.$message.error("入库失败");
      } finally {
        this.uploading = false;
      }
    },
    async openChunks(row) {
      try {
        const res = await request.get(`rag/admin/doc/${row.id}/chunks`, { params: { limit: 50 } });
        if (this.ok(res)) {
          this.chunkRows = this.payload(res) || [];
          this.chunkDrawer = true;
        } else {
          this.toast(res, "加载分块失败");
        }
      } catch (e) {
        this.$message.error("加载分块失败");
      }
    },
    async reindexDoc(row) {
      try {
        await this.$confirm(
          `为「${row.title}」重建向量索引？会用当前知识库的分块参数重新切块并重新向量化。`,
          "确认重建",
          { type: "warning" }
        );
      } catch (e) {
        return;
      }
      try {
        const res = await request.post(`rag/admin/doc/${row.id}/reindex`);
        if (this.ok(res)) {
          this.$message.success("重建完成");
          this.loadDocs();
        } else {
          this.toast(res, "重建失败");
        }
      } catch (e) {
        this.$message.error("重建失败");
      }
    },
    async removeDoc(row) {
      try {
        await this.$confirm(`删除文档「${row.title}」？其文本块与向量会一并删除。`, "确认删除", {
          type: "warning",
        });
      } catch (e) {
        return;
      }
      try {
        const res = await request.delete(`rag/admin/doc/${row.id}`);
        if (this.ok(res)) {
          this.$message.success("已删除");
          this.loadDocs();
          this.loadKbs();
        } else {
          this.toast(res, "删除失败");
        }
      } catch (e) {
        this.$message.error("删除失败");
      }
    },

    // ---------------------------------------------------------------- 数据源
    async loadDataSources() {
      this.dsLoading = true;
      try {
        const res = await request.post("rag/admin/ds/query", { current: 1, size: 100 });
        if (this.ok(res)) {
          this.dsRows = this.payload(res) || [];
        } else {
          this.toast(res, "加载数据源失败");
        }
      } catch (e) {
        this.$message.error("加载数据源失败");
      } finally {
        this.dsLoading = false;
      }
    },
    openDsDialog(row) {
      this.dsForm = row ? { ...EMPTY_DS(), ...row, password: "" } : EMPTY_DS();
      this.dsDialog = true;
    },
    async saveDs() {
      this.dsSaving = true;
      try {
        const body = { ...this.dsForm };
        const res = this.dsForm.id
          ? await request.put("rag/admin/ds/update", body)
          : await request.post("rag/admin/ds/save", body);
        if (this.ok(res)) {
          this.$message.success("已保存");
          this.dsDialog = false;
          this.loadDataSources();
        } else {
          this.toast(res, "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败");
      } finally {
        this.dsSaving = false;
      }
    },
    async checkDs(row) {
      try {
        const res = await request.post(`rag/admin/ds/${row.id}/check`);
        if (this.ok(res)) {
          const r = this.payload(res) || {};
          if (r.ok) this.$message.success("连接正常");
          else this.$message.error("连接失败：" + (r.message || "未知原因"));
          this.loadDataSources();
        } else {
          this.toast(res, "检测失败");
        }
      } catch (e) {
        this.$message.error("检测失败");
      }
    },
    async removeDs(row) {
      try {
        await this.$confirm(
          `删除数据源「${row.name}」？仍被知识库引用时会被拒绝。`,
          "确认删除",
          { type: "warning" }
        );
      } catch (e) {
        return;
      }
      try {
        const res = await request.delete(`rag/admin/ds/${row.id}`);
        if (this.ok(res)) {
          this.$message.success("已删除");
          this.loadDataSources();
        } else {
          this.toast(res, "删除失败");
        }
      } catch (e) {
        this.$message.error("删除失败");
      }
    },
    openPreview(row) {
      this.previewDsId = row.id;
      this.previewSql = "";
      this.previewRows = [];
      this.previewError = "";
      this.previewDialog = true;
    },
    async runPreview() {
      if (!this.previewSql.trim()) {
        this.$message.warning("请输入 SELECT 语句");
        return;
      }
      this.previewing = true;
      this.previewError = "";
      try {
        const res = await request.post(`rag/admin/ds/${this.previewDsId}/preview`, {
          sql: this.previewSql,
          limit: 20,
        });
        if (this.ok(res)) {
          this.previewRows = this.payload(res) || [];
          if (this.previewRows.length === 0) this.$message.info("查询成功，没有返回数据");
        } else {
          this.previewRows = [];
          this.previewError = (res.data && res.data.msg) || "执行失败";
        }
      } catch (e) {
        this.previewRows = [];
        this.previewError = "执行失败";
      } finally {
        this.previewing = false;
      }
    },

    // ---------------------------------------------------------------- 检索质量
    async loadSummary() {
      try {
        const res = await request.get("rag/admin/retrieval/summary", {
          params: { days: this.summaryDays },
        });
        if (this.ok(res)) {
          const d = this.payload(res) || {};
          const { byPath, ...rest } = d;
          this.summary = rest;
          this.byPathRows = byPath || [];
        }
      } catch (e) {
        this.$message.error("加载检索统计失败");
      }
    },
    async loadLogs() {
      this.logLoading = true;
      try {
        const res = await request.post("rag/admin/retrieval/query", {
          queryText: this.logQuery.queryText || null,
          path: this.logQuery.path || null,
          current: this.logQuery.current,
          size: this.logQuery.size,
        });
        if (this.ok(res)) {
          this.logRows = this.payload(res) || [];
          this.logTotal = (res.data && res.data.total) || 0;
        } else {
          this.toast(res, "加载检索日志失败");
        }
      } catch (e) {
        this.$message.error("加载检索日志失败");
      } finally {
        this.logLoading = false;
      }
    },
    onLogPage(page) {
      this.logQuery.current = page;
      this.loadLogs();
    },
    async purgeLogs() {
      try {
        await this.$confirm("清理超过保留天数的检索日志？保留天数由 crm.rag.retrieval-log.keep-days 配置。", "确认清理", {
          type: "warning",
        });
      } catch (e) {
        return;
      }
      try {
        const res = await request.post("rag/admin/retrieval/purge");
        if (this.ok(res)) {
          this.$message.success("已清理");
          this.loadSummary();
          this.loadLogs();
        } else {
          this.toast(res, "清理失败");
        }
      } catch (e) {
        this.$message.error("清理失败");
      }
    },
    pathPct(total) {
      const base = this.summary.total || 0;
      return base ? Math.round((total / base) * 100) : 0;
    },
    pathTagType(path) {
      if (path === "hybrid") return "success";
      if (path === "sparse_like") return "danger";
      if (path === "none") return "info";
      return "warning";
    },

    // ---------------------------------------------------------------- 文案
    audienceText(a) {
      return { all: "全部", user: "普通用户", doctor: "医生", admin: "管理员" }[a] || a || "全部";
    },
    sourceText(t) {
      return { file: "文件", text: "粘贴", url: "链接", remote: "远端库" }[t] || t || "-";
    },
    statusText(s) {
      return { PARSING: "解析中", INDEXED: "已入库", FAILED: "失败", PENDING: "待处理" }[s] || s || "-";
    },
    statusType(s) {
      if (s === "INDEXED") return "success";
      if (s === "FAILED") return "danger";
      return "warning";
    },
    sizeText(bytes) {
      if (bytes == null) return "-";
      if (bytes < 1024) return bytes + " B";
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
      return (bytes / 1024 / 1024).toFixed(2) + " MB";
    },
  },
};
</script>

<style scoped>
.kbm {
  padding: 4px 2px;
}
.kbm-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}
.kbm-head__title {
  margin: 0 0 6px;
  font-size: 18px;
  color: #1b2433;
}
.kbm-head__desc {
  margin: 0;
  font-size: 13px;
  color: #8b95a5;
  line-height: 1.6;
  max-width: 780px;
}
.kbm-tabs {
  margin-top: 4px;
}
.kbm-table {
  margin-top: 4px;
}
.kbm-code {
  font-size: 12px;
  color: #0b5fd0;
  background: #f2f6fd;
  border-radius: 4px;
  padding: 1px 6px;
}
.kbm-num {
  font-variant-numeric: tabular-nums;
  font-size: 12.5px;
  color: #3d4859;
}
.kbm-sub {
  font-size: 11.5px;
  color: #98a2b3;
  line-height: 1.4;
  margin-top: 2px;
}
.kbm-hint {
  margin-left: 12px;
  font-size: 12.5px;
  color: #98a2b3;
}
.kbm-tip {
  font-size: 13px;
  color: #67707e;
  background: #f7f9fc;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 10px 12px;
  margin: 10px 0 4px;
  line-height: 1.7;
}
.kbm-doc-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 10px 0 4px;
}
.kbm-doc-bar__title {
  font-size: 13.5px;
  color: #3d4859;
}
.kbm-doc-bar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kbm-failed {
  margin-top: 12px;
  border: 1px solid #fde2e2;
  background: #fff6f6;
  border-radius: 8px;
  padding: 10px 12px;
}
.kbm-failed__row {
  font-size: 12.5px;
  color: #b42318;
  line-height: 1.8;
}
.kbm-quality-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6px 0 12px;
}
.kbm-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}
.kbm-card {
  border: 1px solid #edf1f7;
  border-radius: 10px;
  padding: 14px 16px;
  background: #fff;
}
.kbm-card--warn {
  border-color: #fdd9b5;
  background: #fffaf3;
}
.kbm-card__label {
  font-size: 12.5px;
  color: #8b95a5;
}
.kbm-card__value {
  font-size: 24px;
  font-weight: 700;
  color: #1b2433;
  margin: 6px 0 4px;
  font-variant-numeric: tabular-nums;
}
.kbm-card__desc {
  font-size: 11.5px;
  color: #98a2b3;
  line-height: 1.5;
}
.kbm-pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.kbm-chunk {
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.kbm-chunk__head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #8b95a5;
  margin-bottom: 6px;
}
.kbm-chunk__body {
  font-size: 13px;
  color: #3d4859;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
