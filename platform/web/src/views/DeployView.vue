<template>
  <div>
    <div class="page-head deploy-head">
      <div class="deploy-title">
        <h1>{{ title }}</h1>
        <label class="picker page-picker">项目
          <select v-model="projectId" @change="changeProject">
            <option v-for="item in projects" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
      </div>
    </div>

    <div class="card env-card">
      <div class="env-stepper">
        <template v-for="(item, index) in envs" :key="item.key">
          <button
            class="env-node"
            :class="{ active: index === activeIndex, done: index < activeIndex }"
            type="button"
            @click="selectEnv(item.key)"
          >
            <span class="env-circle">{{ item.label }}</span>
          </button>
          <span
            v-if="index < envs.length - 1"
            class="env-line"
            :class="{ done: index < activeIndex }"
          ></span>
        </template>
      </div>
      <div v-if="board.running" class="env-lock-hint">
        <span class="pill warning"><i></i>{{ board.running.env }} 部署中 · {{ board.running.operatorName }}</span>
      </div>
    </div>

    <div class="deploy-body">
      <div class="card section-card">
        <div class="panel-head">
          <h3>已部署分支</h3>
          <div class="head-actions">
            <span class="muted-text">{{ env }} 环境 · 共 {{ board.deployed.length }} 个</span>
            <button
              v-if="board.deployed.length"
              class="btn small"
              type="button"
              title="按各分支最新提交重新部署当前环境的已部署分支"
              :disabled="busy || Boolean(board.running)"
              @click="redeployAll"
            >
              重新部署
            </button>
            <button
              v-if="board.failedCount"
              class="btn small primary"
              type="button"
              @click="retryFailed"
            >
              重试{{ board.failedCount > 1 ? ` (${board.failedCount})` : '' }}
            </button>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th style="width:24%">需求</th>
                <th style="width:20%">分支</th>
                <th style="width:12%">部署提交</th>
                <th style="width:12%">部署时间</th>
                <th style="width:18%">标签</th>
                <th style="width:14%; text-align:right">部署结果</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in board.deployed" :key="row.id">
                <td>
                  <div class="req-cell">
                    <button class="link" type="button" @click="openDetail(row.id)">{{ row.id }}</button>
                    <span class="req-title">{{ row.title }}</span>
                  </div>
                </td>
                <td><code>{{ row.branch }}</code></td>
                <td><code>{{ row.sha || '-' }}</code></td>
                <td>{{ row.time || '-' }}</td>
                <td>
                  <div class="tag-row">
                    <template v-for="(tag, tagIndex) in row.tags" :key="tagIndex">
                      <button
                        v-if="tag.action"
                        class="pill tag-btn"
                        :class="tag.tone"
                        type="button"
                        @click="handleTag(tag, row)"
                      >
                        {{ tag.label }}
                      </button>
                      <span v-else class="pill" :class="tag.tone"><i></i>{{ tag.label }}</span>
                    </template>
                    <button
                      v-if="row.status === 'running' && board.pipeline?.status === 'running'"
                      class="btn small danger"
                      type="button"
                      @click="abortDeployment"
                    >
                      终止部署
                    </button>
                  </div>
                </td>
                <td class="result-cell" style="text-align:right">
                  <span class="pill" :class="statusMeta(row.status).cls" :title="row.error || ''">
                    <i></i>{{ statusMeta(row.status).label }}
                  </span>
                  <button
                    v-if="row.status === 'conflict'"
                    class="link"
                    type="button"
                    style="margin-left:10px"
                    @click="resolveConflict(row)"
                  >
                    已解决
                  </button>
                </td>
              </tr>
              <tr v-if="!board.deployed.length">
                <td colspan="6" class="empty">该环境还没有部署记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="env === 'prod' && board.prodReleases.length" class="card section-card">
        <div class="panel-head">
          <h3>生产版本与回滚</h3>
          <span class="muted-text">部署失败或线上验证异常时可回滚</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th style="width:34%">需求</th>
                <th style="width:24%">当前线上版本</th>
                <th style="width:24%">上一版本</th>
                <th style="width:18%"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in board.prodReleases" :key="row.id">
                <td>
                  <button class="link" type="button" @click="openDetail(row.id)">{{ row.id }}</button>
                  <div class="title">{{ row.title }}</div>
                </td>
                <td><code>{{ row.currentSha }}</code><div class="title">{{ row.currentTime }}</div></td>
                <td>
                  <code>{{ row.previousSha || '-' }}</code>
                  <div class="title">{{ row.previousTime || '-' }}</div>
                </td>
                <td class="ops">
                  <button
                    class="btn small"
                    type="button"
                    :disabled="!row.previousSha"
                    @click="openRollback(row)"
                  >
                    回滚到上一版本
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card section-card">
        <div class="panel-head">
          <h3>待部署分支</h3>
          <div class="head-actions">
            <span class="muted-text">已选 {{ selectedCount }} 个</span>
            <button
              class="btn primary"
              type="button"
              :disabled="!selectedCount"
              @click="deploySelected"
            >
              部署选中{{ selectedCount ? ` (${selectedCount})` : '' }}
            </button>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th style="width:5%">
                  <input
                    type="checkbox"
                    :checked="allSelected"
                    :disabled="!selectablePending.length"
                    @change="toggleSelectAll($event.target.checked)"
                  >
                </th>
                <th style="width:32%">需求</th>
                <th style="width:26%">分支</th>
                <th style="width:12%">提交</th>
                <th style="width:25%">标签</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in board.pending"
                :key="row.id"
                :class="{ 'row-disabled': !row.gate.ok }"
                :title="row.gate.reason || ''"
              >
                <td>
                  <input
                    type="checkbox"
                    :checked="selected.includes(row.id) && row.gate.ok"
                    :disabled="!row.gate.ok"
                    @change="toggleSelect(row.id)"
                  >
                </td>
                <td>
                  <div class="req-cell">
                    <button class="link" type="button" @click="openDetail(row.id)">{{ row.id }}</button>
                    <span class="req-title">{{ row.title }}</span>
                  </div>
                </td>
                <td><code>{{ row.branch }}</code></td>
                <td><code>{{ row.tipSha }}</code></td>
                <td>
                  <div class="tag-row">
                    <template v-for="(tag, tagIndex) in row.tags" :key="tagIndex">
                      <button
                        v-if="tag.action"
                        class="pill tag-btn"
                        :class="tag.tone"
                        type="button"
                        @click="handleTag(tag, row)"
                      >
                        {{ tag.label }}
                      </button>
                      <span v-else class="pill" :class="tag.tone"><i></i>{{ tag.label }}</span>
                    </template>
                  </div>
                  <div v-if="!row.gate.ok && row.gate.reason" class="muted-text" style="margin-top:4px">
                    {{ row.gate.reason }}
                  </div>
                </td>
              </tr>
              <tr v-if="!board.pending.length">
                <td colspan="5" class="empty">没有待部署的分支</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <PipelinePanel
        v-if="board.pipeline"
        :pipeline="board.pipeline"
        :env="env"
        @abort="abortDeployment"
      />

      <div class="card section-card">
        <div class="panel-head">
          <h3>Pod 状态</h3>
          <span class="muted-text">{{ env }} 环境 · {{ board.pods.length }} 个</span>
        </div>
        <div class="pod-list">
          <div v-for="pod in board.pods" :key="pod.name" class="pod-card">
            <div class="pod-name">
              <span class="pod-dot" :class="{ failed: pod.status !== 'Running' }"></span>
              <strong>{{ pod.name }}</strong>
              <span class="pill" :class="pod.status === 'Running' ? 'success' : 'danger'">
                <i></i>{{ pod.status }}
              </span>
            </div>
            <div class="pod-meta">{{ pod.ready }} · {{ pod.age }} · {{ pod.node }}</div>
            <div class="pod-actions">
              <button class="link" type="button" @click="openPodLogs(pod)">日志</button>
              <button class="link" type="button" @click="openPodYaml(pod)">YAML</button>
            </div>
          </div>
        </div>
        <div v-if="!board.pods.length" class="empty" style="padding:28px">该环境暂无 Pod</div>
      </div>

      <div v-if="busy" class="deploy-mask">
        <span class="spinner"></span>
        <span>正在加载 {{ serviceLabel }} {{ env }} 环境…</span>
      </div>
    </div>

    <DeployConfirmDialog
      v-model="confirmOpen"
      :rows="confirmRows"
      :service="service"
      :env="env"
      :submitting="submitting"
      @confirm="confirmDeploy"
    />

    <CrDialog
      v-model="crOpen"
      :requirement="crRequirement"
      :service="service"
      :project-id="projectId"
      @saved="onCrSaved"
    />

    <RequirementDialog
      v-model="detailOpen"
      :requirement-id="detailId"
      :project-id="projectId"
      :project="currentProject"
      @saved="() => loadBoard()"
    />

    <BaseModal v-model="rollbackOpen" title="回滚确认" width="min(540px, 100%)">
      <div class="kv">
        <span>服务</span><span><span class="pill" :class="service === 'api' ? 'info' : 'primary'">{{ serviceLabel }}</span></span>
        <span>需求</span><span>{{ rollbackRow?.id }} · {{ rollbackRow?.title }}</span>
        <span>当前版本</span><span><code>{{ rollbackRow?.currentSha }}</code></span>
        <span>回滚到</span>
        <span><code>{{ rollbackRow?.previousSha }}</code>（{{ rollbackRow?.previousTime }}）</span>
      </div>
      <div class="notice">回滚会以旧版本重新走一遍发布流程，并替换当前生产 Pod。</div>
      <template #footer>
        <button class="btn" type="button" @click="rollbackOpen = false">取消</button>
        <button class="btn primary" type="button" :disabled="submitting" @click="confirmRollback">
          {{ submitting ? '提交中…' : '确认回滚' }}
        </button>
      </template>
    </BaseModal>

    <BaseModal v-model="codeOpen" :title="codeTitle" width="min(760px, 100%)">
      <div v-if="codeLoading" class="page-loading">加载中…</div>
      <pre v-else class="code-block">{{ codeContent }}</pre>
      <template #footer>
        <button class="btn" type="button" @click="codeOpen = false">关闭</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import api from '../api/client';
import BaseModal from '../components/BaseModal.vue';
import PipelinePanel from '../components/PipelinePanel.vue';
import DeployConfirmDialog from '../components/DeployConfirmDialog.vue';
import CrDialog from '../components/CrDialog.vue';
import RequirementDialog from '../components/RequirementDialog.vue';
import { useApprovalStore } from '../stores/approval';

const props = defineProps({
  service: { type: String, required: true }
});

const envs = [
  { key: 'dev', label: 'dev' },
  { key: 'test', label: 'test' },
  { key: 'uat', label: 'uat' },
  { key: 'prod', label: 'prod' }
];

const emptyBoard = () => ({
  deployed: [],
  pending: [],
  failedCount: 0,
  pipeline: null,
  prodReleases: [],
  pods: [],
  running: null
});

const projects = ref([]);
const projectId = ref('');
const env = ref('dev');
const board = ref(emptyBoard());
const loading = ref(false);
const selected = ref([]);
const confirmOpen = ref(false);
const confirmRows = ref([]);
const submitting = ref(false);
const crOpen = ref(false);
const crRequirement = ref(null);
const detailOpen = ref(false);
const detailId = ref('');
const rollbackOpen = ref(false);
const rollbackRow = ref(null);
const codeOpen = ref(false);
const codeTitle = ref('');
const codeContent = ref('');
const codeLoading = ref(false);
const approvalStore = useApprovalStore();

let pollTimer = null;
let requestSeq = 0;

const title = computed(() => (props.service === 'api' ? '后端部署' : '前端部署'));
const serviceLabel = computed(() => (props.service === 'api' ? '后端 api · app-api' : '前端 web · app-web'));
const busy = computed(() => loading.value);
const activeIndex = computed(() => envs.findIndex((item) => item.key === env.value));
const currentProject = computed(() => projects.value.find((item) => item.id === projectId.value) || null);
const selectablePending = computed(() => board.value.pending.filter((row) => row.gate.ok));
const selectedCount = computed(
  () => board.value.pending.filter((row) => selected.value.includes(row.id) && row.gate.ok).length
);
const allSelected = computed(
  () => selectablePending.value.length > 0
    && selectablePending.value.every((row) => selected.value.includes(row.id))
);

function statusMeta(status) {
  const map = {
    success: { label: '成功', cls: 'success' },
    failed: { label: '失败', cls: 'danger' },
    running: { label: '部署中', cls: 'running' },
    conflict: { label: '合并冲突', cls: 'danger' },
    wait_cr: { label: '待 CR', cls: 'warning' },
    ready: { label: '可部署', cls: 'info' },
    rejected: { label: 'CR 驳回', cls: 'danger' },
    locked: { label: '未开始', cls: 'muted' }
  };
  return map[status] || map.locked;
}

async function loadProjects() {
  try {
    const { data } = await api.get('/projects');
    const list = (data || []).filter((item) => item.service === props.service);
    projects.value = list;
    if (list.length && !list.some((item) => item.id === projectId.value)) {
      projectId.value = list[0].id;
    }
  } catch (e) {
    ElMessage.error(e.message);
  }
}

async function loadBoard(silent = false) {
  const seq = ++requestSeq;
  if (!silent) loading.value = true;
  try {
    const { data } = await api.get('/deploy/board', {
      params: { projectId: projectId.value, service: props.service, env: env.value }
    });
    if (seq !== requestSeq) return;
    board.value = data;
    const validIds = new Set(data.pending.filter((row) => row.gate.ok).map((row) => row.id));
    selected.value = selected.value.filter((id) => validIds.has(id));
  } catch (e) {
    if (seq === requestSeq && !silent) ElMessage.error(e.message);
  } finally {
    if (seq === requestSeq) {
      if (!silent) loading.value = false;
      schedulePolling();
    }
  }
}

function schedulePolling() {
  clearTimeout(pollTimer);
  if (board.value?.pipeline?.status === 'running') {
    pollTimer = setTimeout(() => loadBoard(true), 1200);
  }
}

function selectEnv(key) {
  if (key === env.value) return;
  env.value = key;
  selected.value = [];
  loadBoard();
}

function changeProject() {
  selected.value = [];
  loadBoard();
}

function toggleSelect(id) {
  const index = selected.value.indexOf(id);
  if (index >= 0) selected.value.splice(index, 1);
  else selected.value.push(id);
}

function toggleSelectAll(checked) {
  selected.value = checked ? selectablePending.value.map((row) => row.id) : [];
}

function openConfirm(ids) {
  if (board.value.pipeline?.status === 'running') {
    ElMessage.error('当前环境正在部署，不能重复部署');
    return;
  }
  const all = [...board.value.deployed, ...board.value.pending];
  confirmRows.value = ids
    .map((id) => all.find((row) => row.id === id))
    .filter(Boolean);
  if (confirmRows.value.length) confirmOpen.value = true;
}

function deploySelected() {
  const ids = selected.value.filter((id) =>
    board.value.pending.some((row) => row.id === id && row.gate.ok)
  );
  if (!ids.length) {
    ElMessage.error('没有可部署的分支');
    return;
  }
  openConfirm(ids);
}

function redeployAll() {
  const rows = board.value.deployed;
  const ids = rows.filter((row) => row.gate.ok).map((row) => row.id);
  if (!ids.length) {
    ElMessage.error(rows[0]?.gate.reason || '没有可重新部署的分支');
    return;
  }
  if (ids.length < rows.length) {
    ElMessage.warning(`已跳过 ${rows.length - ids.length} 个不满足门禁的分支`);
  }
  openConfirm(ids);
}

function retryFailed() {
  const failed = board.value.deployed.filter((row) => row.status === 'failed');
  const ids = failed.filter((row) => row.gate.ok).map((row) => row.id);
  if (!ids.length) {
    ElMessage.error(failed[0]?.gate.reason || '没有可重试的分支');
    return;
  }
  if (ids.length < failed.length) {
    ElMessage.warning(`已跳过 ${failed.length - ids.length} 个不满足门禁的分支`);
  }
  openConfirm(ids);
}

async function confirmDeploy() {
  if (!confirmRows.value.length) return;
  submitting.value = true;
  try {
    const ids = confirmRows.value.map((row) => row.id);
    const { data } = await api.post('/deploy/start', {
      projectId: projectId.value,
      service: props.service,
      env: env.value,
      ids
    });
    ElMessage.success(data.message || '已加入部署队列');
    confirmOpen.value = false;
    selected.value = [];
    await loadBoard();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    submitting.value = false;
  }
}

function handleTag(tag, row) {
  if (tag.action === 'cr') {
    crRequirement.value = row;
    crOpen.value = true;
  }
  if (tag.action === 'deploy') {
    openConfirm([row.id]);
  }
}

async function resolveConflict(row) {
  try {
    await api.post('/deploy/conflict/resolve', {
      projectId: projectId.value,
      service: props.service,
      env: env.value,
      id: row.id
    });
    ElMessage.success('冲突已标记解决，请确认重新部署');
    await loadBoard();
    openConfirm([row.id]);
  } catch (e) {
    ElMessage.error(e.message);
  }
}

async function abortDeployment() {
  const pipeline = board.value.pipeline;
  if (!pipeline || pipeline.status !== 'running') return;
  try {
    await api.post(`/deploy/${pipeline.batchId}/abort`);
    ElMessage.warning('部署已终止');
    await loadBoard();
  } catch (e) {
    ElMessage.error(e.message);
  }
}

function onCrSaved() {
  approvalStore.refresh();
  loadBoard();
}

function openDetail(id) {
  detailId.value = id;
  detailOpen.value = true;
}

function openRollback(row) {
  if (board.value.pipeline?.status === 'running') {
    ElMessage.error('当前环境正在部署，不能回滚');
    return;
  }
  rollbackRow.value = row;
  rollbackOpen.value = true;
}

async function confirmRollback() {
  if (!rollbackRow.value) return;
  submitting.value = true;
  try {
    const { data } = await api.post('/deploy/rollback', {
      projectId: projectId.value,
      service: props.service,
      env: 'prod',
      id: rollbackRow.value.id
    });
    ElMessage.success(data.message || '回滚已开始');
    rollbackOpen.value = false;
    await loadBoard();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    submitting.value = false;
  }
}

async function openPodLogs(pod) {
  codeTitle.value = `Pod 日志 · ${pod.name}`;
  codeContent.value = '';
  codeLoading.value = true;
  codeOpen.value = true;
  try {
    const { data } = await api.get('/deploy/pods/logs', {
      params: {
        name: pod.name,
        projectId: projectId.value,
        service: props.service,
        env: env.value
      }
    });
    codeContent.value = data.content;
  } catch (e) {
    codeContent.value = e.message;
  } finally {
    codeLoading.value = false;
  }
}

async function openPodYaml(pod) {
  codeTitle.value = `Pod YAML · ${pod.name}`;
  codeContent.value = '';
  codeLoading.value = true;
  codeOpen.value = true;
  try {
    const { data } = await api.get('/deploy/pods/yaml', {
      params: {
        name: pod.name,
        projectId: projectId.value,
        service: props.service,
        env: env.value
      }
    });
    codeContent.value = data.content;
  } catch (e) {
    codeContent.value = e.message;
  } finally {
    codeLoading.value = false;
  }
}

watch(
  () => props.service,
  () => {
    env.value = 'dev';
    selected.value = [];
    board.value = emptyBoard();
    loadProjects().then(() => loadBoard());
  }
);

onMounted(async () => {
  await loadProjects();
  if (projectId.value) await loadBoard();
});

onBeforeUnmount(() => {
  clearTimeout(pollTimer);
});
</script>
