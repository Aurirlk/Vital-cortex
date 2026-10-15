<template>
  <div class="apm">
    <div class="apm-head">
      <div>
        <h3 class="apm-head__title">智能体平台</h3>
        <p class="apm-head__desc">
          新建与管理智能体、配置它可以用哪些工具、可以把问题转交给谁，并回看每一次运行的真实轨迹。
        </p>
      </div>
      <el-tag
        :type="status.dbBacked ? 'success' : 'info'"
        effect="dark"
        size="large"
      >
        {{
          status.dbBacked
            ? `数据库驱动 · ${status.agentCount} 个`
            : "内置兜底角色"
        }}
      </el-tag>
    </div>

    <el-alert
      v-if="!status.dbBacked"
      class="apm-alert"
      type="warning"
      show-icon
      :closable="false"
      title="当前运行在内置兜底角色上"
      :description="
        status.hint || '未检测到 agent_config 数据。请执行建表脚本后刷新本页。'
      "
    />

    <el-tabs v-model="tab" class="apm-tabs">
      <!-- ==================== 1. 智能体配置 ==================== -->
      <el-tab-pane label="智能体配置" name="config">
        <div class="apm-toolbar">
          <el-input
            v-model="filters.agentKey"
            placeholder="按标识搜索"
            clearable
            style="width: 180px"
            @clear="loadConfigs"
            @keyup.enter="searchConfigs"
          />
          <el-select
            v-model="filters.audience"
            placeholder="面向端"
            clearable
            style="width: 140px"
          >
            <el-option label="全部端" value="all" />
            <el-option label="用户端" value="user" />
            <el-option label="医生端" value="doctor" />
            <el-option label="管理端" value="admin" />
          </el-select>
          <el-select
            v-model="filters.enabled"
            placeholder="状态"
            clearable
            style="width: 120px"
          >
            <el-option label="已启用" :value="true" />
            <el-option label="已停用" :value="false" />
          </el-select>
          <el-button type="primary" @click="searchConfigs">查询</el-button>
          <div class="apm-toolbar__spacer"></div>
          <el-button type="primary" plain @click="openCreate"
            >新建智能体</el-button
          >
        </div>

        <el-table
          v-loading="loading.configs"
          :data="configs"
          border
          size="small"
          class="apm-table"
        >
          <el-table-column label="名称" min-width="150">
            <template #default="{ row }">
              <span class="apm-name">{{ row.name }}</span>
              <el-tag
                v-if="row.isDefault"
                size="small"
                type="warning"
                effect="plain"
                class="apm-badge"
              >
                兜底
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="agentKey" label="标识" min-width="140">
            <template #default="{ row }">
              <code class="apm-code">{{ row.agentKey }}</code>
            </template>
          </el-table-column>
          <el-table-column label="面向端" width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{
                audienceLabel(row.audience)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="routingKeywords"
            label="路由关键词"
            min-width="200"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span v-if="row.routingKeywords">{{ row.routingKeywords }}</span>
              <span v-else class="apm-muted">—（不参与关键词路由）</span>
            </template>
          </el-table-column>
          <el-table-column label="轮次" width="70" align="center">
            <template #default="{ row }">{{
              row.maxIterations || "默认"
            }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled"
                :disabled="row.isDefault"
                :loading="togglingId === row.id"
                @change="(v) => toggle(row, v)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="290" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)"
                >编辑</el-button
              >
              <el-button link type="primary" @click="openTools(row)"
                >工具</el-button
              >
              <el-button link type="primary" @click="openHandoffs(row)"
                >委派</el-button
              >
              <el-button
                link
                type="danger"
                :disabled="row.isDefault"
                @click="removeAgent(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="apm-pager"
          layout="total, sizes, prev, pager, next"
          :total="configTotal"
          :current-page="page.current"
          :page-size="page.size"
          :page-sizes="[10, 20, 50]"
          @current-change="onConfigPage"
          @size-change="onConfigSize"
        />
        <p class="apm-note">
          提示：「兜底」智能体是找不到匹配角色时的最终接收者，因此不能被停用或删除
          —— 要换掉它，请先把另一个智能体设为兜底。
        </p>
      </el-tab-pane>

      <!-- ==================== 2. 路由试跑 ==================== -->
      <el-tab-pane label="路由试跑" name="preview">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="apm-tip"
          title="用真实的一句话验证路由规则"
          description="关键词是「数命中数、取最高分、平票按排序取先者」。试跑会给出胜出者、命中的关键词与落选的候选，改关键词时不必靠猜。"
        />

        <div class="apm-preview-form">
          <el-input
            v-model="preview.query"
            type="textarea"
            :rows="3"
            placeholder="例如：我最近血压有点高，还总失眠"
          />
          <div class="apm-preview-actions">
            <el-select
              v-model="preview.audience"
              placeholder="不区分端"
              clearable
              style="width: 140px"
            >
              <el-option label="不区分端" value="" />
              <el-option label="用户端" value="user" />
              <el-option label="医生端" value="doctor" />
              <el-option label="管理端" value="admin" />
            </el-select>
            <el-button
              type="primary"
              :loading="preview.loading"
              @click="runPreview"
              >试跑</el-button
            >
          </div>
        </div>

        <div v-if="preview.result" class="apm-preview-result">
          <div class="apm-verdict">
            <span class="apm-verdict__label">路由到</span>
            <span class="apm-verdict__name">{{
              preview.result.agentName || preview.result.agentKey
            }}</span>
            <code class="apm-code">{{ preview.result.agentKey }}</code>
            <el-tag
              size="small"
              :type="reasonType(preview.result.reason)"
              effect="dark"
            >
              {{ reasonLabel(preview.result) }}
            </el-tag>
            <el-tag v-if="preview.result.intent" size="small" effect="plain">
              意图 {{ preview.result.intent }}
            </el-tag>
            <el-tag
              v-if="preview.result.intentError"
              size="small"
              type="warning"
              effect="plain"
            >
              意图识别异常，已退回关键词
            </el-tag>
          </div>

          <el-table
            :data="preview.result.keywordHits || []"
            border
            size="small"
            class="apm-table"
          >
            <el-table-column label="候选智能体" min-width="160">
              <template #default="{ row }">
                <span class="apm-name">{{ row.agentKey }}</span>
                <el-tag
                  v-if="row.agentKey === preview.result.agentKey"
                  size="small"
                  type="success"
                  effect="plain"
                  class="apm-badge"
                  >胜出</el-tag
                >
              </template>
            </el-table-column>
            <el-table-column
              prop="score"
              label="命中数"
              width="90"
              align="center"
            />
            <el-table-column label="命中的关键词" min-width="240">
              <template #default="{ row }">
                <el-tag
                  v-for="k in row.matchedKeywords"
                  :key="k"
                  size="small"
                  class="apm-kw"
                  >{{ k }}</el-tag
                >
              </template>
            </el-table-column>
          </el-table>
          <p v-if="!(preview.result.keywordHits || []).length" class="apm-note">
            没有任何智能体的关键词命中，因此走的是兜底角色。
          </p>
        </div>
      </el-tab-pane>

      <!-- ==================== 3. 运行记录 ==================== -->
      <el-tab-pane label="运行记录" name="runs">
        <div class="apm-toolbar">
          <el-input
            v-model="runFilters.agentKey"
            placeholder="智能体标识"
            clearable
            style="width: 160px"
          />
          <el-select
            v-model="runFilters.status"
            placeholder="状态"
            clearable
            style="width: 130px"
          >
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-input
            v-model="runFilters.userId"
            placeholder="用户ID"
            clearable
            style="width: 120px"
          />
          <el-button type="primary" @click="searchRuns">查询</el-button>
          <el-button @click="reloadRuns">刷新</el-button>
        </div>

        <el-table
          v-loading="loading.runs"
          :data="runs"
          border
          size="small"
          class="apm-table"
        >
          <el-table-column label="智能体" min-width="130">
            <template #default="{ row }">{{
              row.agentName || row.agentKey
            }}</template>
          </el-table-column>
          <el-table-column label="端" width="90" align="center">
            <template #default="{ row }">{{ runAudience(row) }}</template>
          </el-table-column>
          <el-table-column label="运行" min-width="150">
            <template #default="{ row }">
              <code
                class="apm-code apm-code--clickable"
                @click="openTrace(row)"
              >
                {{ shortId(row.runId) }}
              </code>
              <el-tag
                v-if="row.parentRunId"
                size="small"
                type="info"
                effect="plain"
                class="apm-badge"
              >
                子委派
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="statusType(row.status)"
                effect="dark"
                >{{ row.status }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column
            prop="iterations"
            label="轮次"
            width="70"
            align="center"
          />
          <el-table-column
            prop="tokens"
            label="Tokens"
            width="90"
            align="center"
          />
          <el-table-column label="耗时" width="90" align="center">
            <template #default="{ row }">{{
              row.latencyMs != null ? `${row.latencyMs}ms` : "—"
            }}</template>
          </el-table-column>
          <el-table-column
            prop="question"
            label="提问"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="answerDigest"
            label="回答摘要"
            min-width="200"
            show-overflow-tooltip
          />
          <el-table-column prop="createTime" label="时间" width="160" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTrace(row)"
                >轨迹</el-button
              >
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="apm-pager"
          layout="total, sizes, prev, pager, next"
          :total="runTotal"
          :current-page="runPage.current"
          :page-size="runPage.size"
          :page-sizes="[10, 20, 50]"
          @current-change="onRunPage"
          @size-change="onRunSize"
        />
        <p v-if="!runs.length" class="apm-note">
          没有运行记录。若本机还没执行
          <code>Data/02_迁移脚本/10_agent_architecture.sql</code>，
          记录表不存在，这里会一直为空 —— 这属于预期现象，不影响对话功能本身。
        </p>
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 编辑弹窗 ==================== -->
    <el-dialog
      v-model="form.visible"
      :title="form.isCreate ? '新建智能体' : '编辑智能体'"
      width="560px"
    >
      <el-form
        ref="agentForm"
        :model="form.data"
        :rules="formRules"
        label-width="110px"
      >
        <el-form-item label="标识" prop="agentKey">
          <el-input
            v-model="form.data.agentKey"
            :disabled="!form.isCreate"
            placeholder="小写字母开头，可含数字与下划线"
          />
          <span class="apm-hint"
            >标识创建后不可修改：它是对外暴露的
            key，改它等于换了一个智能体。</span
          >
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.data.name" placeholder="例如：睡眠顾问" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.data.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="面向端">
          <el-select v-model="form.data.audience" style="width: 100%">
            <el-option label="全部端" value="all" />
            <el-option label="用户端" value="user" />
            <el-option label="医生端" value="doctor" />
            <el-option label="管理端" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="路由关键词">
          <el-input
            v-model="form.data.routingKeywords"
            placeholder="用逗号分隔，例如：失眠, 睡眠, 多梦"
          />
          <span class="apm-hint">
            这是关键词路由的全部依据：命中数最多者胜出，平票时按「排序」取靠前者。
          </span>
        </el-form-item>
        <el-form-item label="最大轮次">
          <el-input-number
            v-model="form.data.maxIterations"
            :min="1"
            :max="20"
          />
          <span class="apm-hint">每轮 = 一次模型调用。留空用默认。</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.data.sortOrder" :min="0" :max="9999" />
          <span class="apm-hint"
            >越小越靠前。平票时先声明的胜出，所以排序会影响路由结果。</span
          >
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.data.enabled" />
        </el-form-item>
        <el-form-item label="设为兜底">
          <el-switch v-model="form.data.isDefault" />
          <span class="apm-hint"
            >兜底角色不能停用或删除。全局只需一个（另外可给某个端单独设一个）。</span
          >
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="saveAgent"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <!-- ==================== 工具绑定抽屉 ==================== -->
    <el-drawer
      v-model="tools.visible"
      :title="`工具权限 · ${tools.agentName}`"
      size="520px"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="apm-tip"
        title="「不限制」与「一个都不给」是两件不同的事"
        description="不限制 = 一份绑定行都不写，自动获得全部工具（含以后新增的）。白名单 = 明确只给这几个。若误把「不限制」当空白名单来配，新工具上线时这个智能体会悄悄少一块能力。"
      />

      <el-radio-group v-model="tools.restricted" class="apm-radio">
        <el-radio :value="false">不限制（全量工具）</el-radio>
        <el-radio :value="true">按白名单</el-radio>
      </el-radio-group>

      <el-select
        v-model="tools.selected"
        :disabled="!tools.restricted"
        multiple
        collapse-tags
        collapse-tags-tooltip
        placeholder="选择一个工具都不给"
        style="width: 100%; margin-top: 12px"
      >
        <el-option
          v-for="t in tools.catalog"
          :key="t.name"
          :label="t.name"
          :value="t.name"
          :disabled="t.autoManaged"
        >
          <span>{{ t.name }}</span>
          <span v-if="t.autoManaged" class="apm-muted"
            >（由「委派」自动管理，不可手工绑定）</span
          >
          <div class="apm-opt-desc">{{ t.description }}</div>
        </el-option>
      </el-select>

      <div v-if="tools.restricted && !tools.selected.length" class="apm-warn">
        白名单为空 = 这个智能体一个工具都不能调用，它只能凭自己的知识回答。
      </div>

      <template #footer>
        <el-button @click="tools.visible = false">取消</el-button>
        <el-button type="primary" :loading="tools.saving" @click="saveTools"
          >保存</el-button
        >
      </template>
    </el-drawer>

    <!-- ==================== 委派关系抽屉 ==================== -->
    <el-drawer
      v-model="handoffs.visible"
      :title="`可委派给 · ${handoffs.agentName}`"
      size="520px"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="apm-tip"
        title="委派授权是有方向的，且只有一跳"
        description="这里配的是「谁可以把问题交给谁」——只有配了 A → B，A 才被允许委派给 B，反过来不成立。被委派者不能再往下委派，因此不会出现 A 与 B 互相调用直到耗尽资源的情况。"
      />

      <el-select
        v-model="handoffs.selected"
        multiple
        filterable
        placeholder="不选 = 不允许委派"
        style="width: 100%"
      >
        <el-option
          v-for="a in handoffCandidates"
          :key="a.agentKey"
          :label="a.name || a.agentKey"
          :value="a.agentKey"
        >
          <span>{{ a.name || a.agentKey }}</span>
          <span class="apm-muted"> · {{ a.agentKey }}</span>
        </el-option>
      </el-select>
      <div v-if="!handoffs.selected.length" class="apm-note">
        当前不允许委派：这个智能体遇到专业外的问题时，只能自己回答或建议用户换个话题。
      </div>

      <template #footer>
        <el-button @click="handoffs.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="handoffs.saving"
          @click="saveHandoffs"
          >保存</el-button
        >
      </template>
    </el-drawer>

    <!-- ==================== 运行轨迹抽屉 ==================== -->
    <el-drawer
      v-model="trace.visible"
      :title="`运行轨迹 · ${shortId(trace.runId)}`"
      size="640px"
    >
      <div v-if="trace.data" class="apm-trace">
        <div class="apm-trace__meta">
          <el-tag size="small" effect="plain"
            >节点 {{ trace.data.nodeCount }}</el-tag
          >
          <el-tag size="small" type="warning" effect="plain"
            >委派 {{ trace.data.handoffCount }}</el-tag
          >
          <span class="apm-muted">
            {{
              trace.data.handoffCount > 0
                ? "这是一棵委派树，父运行与子运行共享 runId 前缀"
                : "本次运行没有发生委派"
            }}
          </span>
        </div>

        <div
          v-for="node in trace.data.nodes"
          :key="node.runId"
          class="apm-node"
        >
          <div class="apm-node__head">
            <span class="apm-node__agent">{{
              node.agentName || node.agentKey
            }}</span>
            <el-tag
              size="small"
              :type="statusType(node.status)"
              effect="dark"
              >{{ node.status }}</el-tag
            >
            <el-tag
              v-if="node.parentRunId"
              size="small"
              type="info"
              effect="plain"
              >子委派</el-tag
            >
            <span class="apm-muted">
              {{ node.iterations }} 轮 · {{ node.tokens }} tokens ·
              {{ node.latencyMs != null ? node.latencyMs + "ms" : "—" }}
            </span>
          </div>
          <div class="apm-node__q">问：{{ node.question || "—" }}</div>
          <div v-if="node.answerDigest" class="apm-node__a">
            答：{{ node.answerDigest }}
          </div>
          <div v-if="node.errorMsg" class="apm-node__err">
            错误：{{ node.errorMsg }}
          </div>

          <el-table
            :data="node.steps || []"
            border
            size="small"
            class="apm-table apm-steps"
          >
            <el-table-column
              prop="stepIndex"
              label="#"
              width="50"
              align="center"
            />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.stepType === 'handoff' ? 'warning' : 'info'"
                  effect="plain"
                >
                  {{ row.stepType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="对象" min-width="130">
              <template #default="{ row }">
                <span v-if="row.targetAgent" class="apm-name"
                  >→ {{ row.targetAgent }}</span
                >
                <span v-else>{{ row.toolName || "—" }}</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="latencyMs"
              label="耗时"
              width="80"
              align="center"
            >
              <template #default="{ row }">{{ row.latencyMs }}ms</template>
            </el-table-column>
            <el-table-column
              label="结果摘要"
              min-width="200"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{
                row.resultDigest || "—"
              }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <el-empty v-else description="加载中…" />
    </el-drawer>
  </div>
</template>

<script>
import request from "@/utils/request.js";

const EMPTY_FORM = () => ({
  id: null,
  agentKey: "",
  name: "",
  description: "",
  audience: "all",
  routingKeywords: "",
  maxIterations: 5,
  sortOrder: 100,
  enabled: true,
  isDefault: false,
});

export default {
  name: "AgentPlatformManage",
  data() {
    return {
      tab: "config",
      status: { dbBacked: true, agentCount: 0, hint: "" },

      // ---- 列表 ----
      configs: [],
      configTotal: 0,
      filters: { agentKey: "", audience: "", enabled: null },
      page: { current: 1, size: 10 },
      togglingId: null,

      // ---- 编辑 ----
      form: {
        visible: false,
        isCreate: true,
        saving: false,
        data: EMPTY_FORM(),
      },
      formRules: {
        agentKey: [
          { required: true, message: "标识不能为空", trigger: "blur" },
          {
            // 与后端 KEY_PATTERN 一致：前端先拦一道，避免只为了看一句报错而往返一次
            pattern: /^[a-z][a-z0-9_]{1,63}$/,
            message: "只能用小写字母、数字、下划线，且以字母开头（至少 2 位）",
            trigger: "blur",
          },
        ],
        name: [{ required: true, message: "名称不能为空", trigger: "blur" }],
      },

      // ---- 工具绑定 ----
      tools: {
        visible: false,
        agentId: null,
        agentName: "",
        restricted: false,
        selected: [],
        catalog: [],
        saving: false,
      },

      // ---- 委派关系 ----
      handoffs: {
        visible: false,
        agentId: null,
        agentName: "",
        selected: [],
        saving: false,
      },

      // ---- 试跑 ----
      preview: {
        query: "",
        audience: "",
        loading: false,
        result: null,
      },

      // ---- 运行记录 ----
      runs: [],
      runTotal: 0,
      runFilters: { agentKey: "", status: "", userId: "" },
      runPage: { current: 1, size: 10 },
      trace: { visible: false, runId: "", data: null },

      loading: { configs: false, runs: false },
    };
  },
  computed: {
    handoffCandidates() {
      // 不能委派给自己：后端会拒绝，前端就不该把它列出来让管理员白选一次
      const current = this.handoffs.agentId;
      const row = this.configs.find((c) => c.id === current);
      return this.configs.filter((c) => !row || c.agentKey !== row.agentKey);
    },
  },
  created() {
    this.loadStatus();
    this.loadConfigs();
    this.loadCatalog();
  },
  methods: {
    audienceLabel(a) {
      return (
        { all: "全部端", user: "用户端", doctor: "医生端", admin: "管理端" }[
          a
        ] ||
        a ||
        "全部端"
      );
    },
    /**
     * 运行记录里的「端」：NULL 渲染成「—」而不是「全部端」。
     * 后端在认不出身份时留 NULL 是刻意的（不猜一个端写进审计），
     * 而「全部端」是一个断言 —— 把「不知道」显示成「对所有端开放」，
     * 正好是审计最不该犯的错。
     */
    runAudience(row) {
      return row && row.audience ? this.audienceLabel(row.audience) : "—";
    },
    statusType(s) {
      return (
        { SUCCESS: "success", FAILED: "danger", RUNNING: "warning" }[s] ||
        "info"
      );
    },
    reasonType(reason) {
      if (!reason) return "info";
      if (reason.startsWith("intent:")) return "success";
      if (reason === "keyword") return "primary";
      return "info";
    },
    reasonLabel(r) {
      if (!r.reason) return "未知依据";
      if (r.reason.startsWith("intent:"))
        return `意图优先 · ${r.reason.slice(7)}`;
      if (r.reason === "keyword") return `关键词命中 ${r.score} 个`;
      return "兜底角色";
    },
    shortId(id) {
      return id ? `${id.slice(0, 8)}…` : "—";
    },

    async loadStatus() {
      try {
        const { data } = await request.get("agent/admin/status");
        if (data.code === 200) this.status = data.data || this.status;
      } catch (e) {
        console.error(e);
      }
    },
    async loadCatalog() {
      try {
        const { data } = await request.get("agent/admin/tools");
        if (data.code === 200) this.tools.catalog = data.data || [];
      } catch (e) {
        console.error(e);
      }
    },

    // ---------------- 列表 ----------------
    async loadConfigs() {
      this.loading.configs = true;
      try {
        const body = {
          current: this.page.current,
          size: this.page.size,
          agentKey: this.filters.agentKey || null,
          audience: this.filters.audience || null,
          enabled: this.filters.enabled,
        };
        const { data } = await request.post("agent/admin/config/query", body);
        if (data.code === 200) {
          this.configs = data.data || [];
          this.configTotal = data.total || 0;
        } else {
          this.$message.error(data.msg || "查询失败");
        }
      } catch (e) {
        console.error(e);
      } finally {
        this.loading.configs = false;
      }
    },
    searchConfigs() {
      this.page.current = 1;
      this.loadConfigs();
    },
    onConfigPage(p) {
      this.page.current = p;
      this.loadConfigs();
    },
    onConfigSize(s) {
      this.page.size = s;
      this.page.current = 1;
      this.loadConfigs();
    },

    // ---------------- 增删改 ----------------
    openCreate() {
      this.form.isCreate = true;
      this.form.data = EMPTY_FORM();
      this.form.visible = true;
      this.$nextTick(
        () => this.$refs.agentForm && this.$refs.agentForm.clearValidate()
      );
    },
    openEdit(row) {
      this.form.isCreate = false;
      this.form.data = {
        id: row.id,
        agentKey: row.agentKey,
        name: row.name,
        description: row.description || "",
        audience: row.audience || "all",
        routingKeywords: row.routingKeywords || "",
        maxIterations: row.maxIterations || 5,
        sortOrder: row.sortOrder != null ? row.sortOrder : 100,
        enabled: !!row.enabled,
        isDefault: !!row.isDefault,
      };
      this.form.visible = true;
      this.$nextTick(
        () => this.$refs.agentForm && this.$refs.agentForm.clearValidate()
      );
    },
    saveAgent() {
      this.$refs.agentForm.validate(async (ok) => {
        if (!ok) return;
        this.form.saving = true;
        try {
          const payload = { ...this.form.data };
          const { data } = this.form.isCreate
            ? await request.post("agent/admin/config/save", payload)
            : await request.put("agent/admin/config/update", payload);
          if (data.code === 200) {
            this.$message.success("已保存");
            this.form.visible = false;
            this.loadConfigs();
            this.loadStatus();
          } else {
            this.$message.error(data.msg || "保存失败");
          }
        } catch (e) {
          console.error(e);
        } finally {
          this.form.saving = false;
        }
      });
    },
    async toggle(row, enabled) {
      this.togglingId = row.id;
      try {
        const { data } = await request.post(
          `agent/admin/config/${row.id}/toggle`,
          null,
          {
            params: { enabled },
          }
        );
        if (data.code === 200) {
          row.enabled = enabled;
          this.$message.success(enabled ? "已启用" : "已停用");
        } else {
          // 兜底角色不允许停用：后端会拒，这里必须把开关拨回去，
          // 否则界面显示的是一份与后端不一致的状态
          this.$message.warning(data.msg || "操作被拒绝");
        }
      } catch (e) {
        console.error(e);
      } finally {
        this.togglingId = null;
      }
    },
    async removeAgent(row) {
      try {
        await this.$confirm(
          `删除「${row.name}」后，它配置的工具权限与委派关系会一并清除，且不可恢复。`,
          "确认删除",
          { type: "warning" }
        );
      } catch (e) {
        return; // 用户取消
      }
      try {
        const { data } = await request.post("agent/admin/config/batchDelete", [
          row.id,
        ]);
        if (data.code === 200) {
          this.$message.success("已删除");
          this.loadConfigs();
          this.loadStatus();
        } else {
          this.$message.error(data.msg || "删除失败");
        }
      } catch (e) {
        console.error(e);
      }
    },

    // ---------------- 工具绑定 ----------------
    async openTools(row) {
      this.tools.agentId = row.id;
      this.tools.agentName = row.name;
      this.tools.visible = true;
      try {
        const { data } = await request.get(`agent/admin/config/${row.id}`);
        if (data.code === 200) {
          const d = data.data || {};
          // restricted 由后端给出，而不是「toolNames 为空就当全量」——
          // 表结构上「零绑定行」和「全量」是同一件事，靠猜会永久配错
          this.tools.restricted = !!d.restricted;
          this.tools.selected = d.toolNames || [];
        }
      } catch (e) {
        console.error(e);
      }
    },
    async saveTools() {
      this.tools.saving = true;
      try {
        const { data } = await request.post(
          `agent/admin/config/${this.tools.agentId}/tools`,
          {
            restricted: this.tools.restricted,
            toolNames: this.tools.restricted ? this.tools.selected : [],
          }
        );
        if (data.code === 200) {
          this.$message.success("已保存，10 秒内生效");
          this.tools.visible = false;
        } else {
          this.$message.error(data.msg || "保存失败");
        }
      } catch (e) {
        console.error(e);
      } finally {
        this.tools.saving = false;
      }
    },

    // ---------------- 委派关系 ----------------
    async openHandoffs(row) {
      this.handoffs.agentId = row.id;
      this.handoffs.agentName = row.name;
      this.handoffs.visible = true;
      try {
        const { data } = await request.get(`agent/admin/config/${row.id}`);
        if (data.code === 200) {
          this.handoffs.selected = (data.data || {}).handoffTargets || [];
        }
      } catch (e) {
        console.error(e);
      }
    },
    async saveHandoffs() {
      this.handoffs.saving = true;
      try {
        const { data } = await request.post(
          `agent/admin/config/${this.handoffs.agentId}/handoffs`,
          {
            targetKeys: this.handoffs.selected,
          }
        );
        if (data.code === 200) {
          this.$message.success("已保存，10 秒内生效");
          this.handoffs.visible = false;
        } else {
          this.$message.error(data.msg || "保存失败");
        }
      } catch (e) {
        console.error(e);
      } finally {
        this.handoffs.saving = false;
      }
    },

    // ---------------- 试跑 ----------------
    async runPreview() {
      if (!this.preview.query.trim()) {
        this.$message.warning("请输入要试跑的问题");
        return;
      }
      this.preview.loading = true;
      try {
        const { data } = await request.post("agent/admin/route/preview", {
          query: this.preview.query,
          audience: this.preview.audience || null,
        });
        if (data.code === 200) this.preview.result = data.data;
        else this.$message.error(data.msg || "试跑失败");
      } catch (e) {
        console.error(e);
      } finally {
        this.preview.loading = false;
      }
    },

    // ---------------- 运行记录 ----------------
    async loadRuns() {
      this.loading.runs = true;
      try {
        const body = {
          current: this.runPage.current,
          size: this.runPage.size,
          agentKey: this.runFilters.agentKey || null,
          status: this.runFilters.status || null,
          userId: this.runFilters.userId
            ? Number(this.runFilters.userId)
            : null,
        };
        const { data } = await request.post("agent/admin/runs/query", body);
        if (data.code === 200) {
          this.runs = data.data || [];
          this.runTotal = data.total || 0;
        } else {
          this.$message.error(data.msg || "查询失败");
        }
      } catch (e) {
        console.error(e);
      } finally {
        this.loading.runs = false;
      }
    },
    searchRuns() {
      this.runPage.current = 1;
      this.loadRuns();
    },
    reloadRuns() {
      this.loadRuns();
    },
    onRunPage(p) {
      this.runPage.current = p;
      this.loadRuns();
    },
    onRunSize(s) {
      this.runPage.size = s;
      this.runPage.current = 1;
      this.loadRuns();
    },
    async openTrace(row) {
      this.trace.runId = row.runId;
      this.trace.data = null;
      this.trace.visible = true;
      try {
        const { data } = await request.get(`agent/admin/runs/${row.runId}`);
        if (data.code === 200) this.trace.data = data.data;
        else this.$message.error(data.msg || "轨迹加载失败");
      } catch (e) {
        console.error(e);
      }
    },
  },
  watch: {
    tab(v) {
      // 运行记录只在真正切过去时才拉：默认页签下它是纯浪费的一次请求，
      // 而且表未建时后端会返回空列表 + 一条提示，提前拉会让管理员一进页面以为出错了
      if (v === "runs" && !this.runs.length) this.loadRuns();
    },
  },
};
</script>

<style scoped>
.apm {
  padding: 4px;
}
.apm-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.apm-head__title {
  font-size: 20px;
  font-weight: 700;
  color: #191c1e;
  margin: 0 0 6px;
}
.apm-head__desc {
  font-size: 13px;
  color: #647084;
  margin: 0;
  max-width: 720px;
  line-height: 1.6;
}
.apm-alert,
.apm-tip {
  margin-bottom: 16px;
}
.apm-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.apm-toolbar__spacer {
  flex: 1;
}
.apm-table {
  width: 100%;
}
.apm-pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.apm-name {
  font-weight: 600;
  color: #191c1e;
}
.apm-badge {
  margin-left: 8px;
}
.apm-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  background: #f2f4f7;
  padding: 2px 6px;
  border-radius: 4px;
  color: #40506b;
}
.apm-code--clickable {
  cursor: pointer;
}
.apm-code--clickable:hover {
  background: #e3e8f0;
}
.apm-muted {
  color: #8a95a6;
  font-size: 12px;
}
.apm-hint {
  display: block;
  font-size: 12px;
  color: #8a95a6;
  line-height: 1.5;
  margin-top: 2px;
}
.apm-note {
  font-size: 12px;
  color: #8a95a6;
  line-height: 1.7;
  margin: 12px 0 0;
}
.apm-warn {
  margin-top: 12px;
  padding: 10px 12px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 8px;
  font-size: 13px;
  color: #ad6800;
}
.apm-radio {
  margin-bottom: 4px;
}
.apm-opt-desc {
  font-size: 12px;
  color: #8a95a6;
  line-height: 1.4;
}

/* ---- 试跑 ---- */
.apm-preview-form {
  margin-bottom: 18px;
}
.apm-preview-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
.apm-verdict {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 14px 16px;
  background: #f5f8ff;
  border: 1px solid #d6e2ff;
  border-radius: 10px;
  margin-bottom: 14px;
}
.apm-verdict__label {
  font-size: 13px;
  color: #647084;
}
.apm-verdict__name {
  font-size: 18px;
  font-weight: 700;
  color: #191c1e;
}
.apm-kw {
  margin: 0 4px 4px 0;
}

/* ---- 轨迹 ---- */
.apm-trace__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.apm-node {
  border: 1px solid #e6eaf0;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 14px;
  background: #fcfdff;
}
.apm-node__head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.apm-node__agent {
  font-weight: 700;
  color: #191c1e;
}
.apm-node__q,
.apm-node__a,
.apm-node__err {
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 4px;
  color: #40506b;
}
.apm-node__a {
  color: #2f7a4d;
}
.apm-node__err {
  color: #c0392b;
}
.apm-steps {
  margin-top: 10px;
}
</style>
