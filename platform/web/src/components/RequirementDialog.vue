<template>
  <BaseModal
    v-model="visible"
    :title="requirementId ? `${detail?.id || ''} · ${detail?.title || '需求详情'}` : '新建需求'"
    width="min(780px, 100%)"
  >
    <div v-if="!requirementId" class="form-stack">
      <label class="field">需求标题
        <input v-model="form.title" maxlength="200" placeholder="例如：订单列表支持导出">
      </label>
      <label class="field">需求描述
        <textarea v-model="form.description" placeholder="补充验收口径、影响范围等（可选）"></textarea>
      </label>
      <div class="field">相关人员
        <div class="chip-row">
          <button
            v-for="item in approvers"
            :key="item.username"
            type="button"
            class="pill chip-btn"
            :class="form.related.includes(item.username) ? 'info' : 'muted'"
            @click="toggleRelated(item.username)"
          >
            {{ item.displayName }}
          </button>
        </div>
        <p class="muted-text" style="margin:8px 0 0; font-size:12px">
          负责人默认是当前登录人；相关人员可查看这条需求。
        </p>
      </div>
    </div>

    <div v-else-if="loading" class="page-loading">加载需求中…</div>

    <div v-else-if="detail">
      <div class="detail-desc">{{ detail.description || '暂无描述' }}</div>

      <div class="detail-meta">
        <span class="pill" :class="detail.status.cls"><i></i>{{ detail.status.label }}</span>
        <span>负责人 {{ detail.ownerName }}</span>
        <span>创建于 {{ detail.createdAt }}</span>
        <span v-if="detail.branch">分支 <code>{{ detail.branch }}</code></span>
        <span v-else>分支未创建</span>
      </div>

      <div class="detail-panels">
        <div class="panel">
          <div class="panel-head"><h3>开发分支</h3></div>
          <div class="panel-body">
            <template v-if="detail.branchStatus === 'created'">
              <div class="branch-box">
                <div>
                  <div class="muted-text" style="font-size:12px">当前开发分支 · {{ serviceLabel }}</div>
                  <code>{{ detail.branch }}</code>
                </div>
                <div style="margin-left:auto; font-size:12px; color:var(--text-3)">
                  最新提交 <code>{{ detail.tips?.[serviceKey] || '-' }}</code>
                </div>
              </div>
              <div class="clone-box">git fetch origin && git checkout {{ detail.branch }}（仓库 {{ project?.id || serviceKey }}）</div>
              <div class="toolbar" style="padding:14px 0 0; border:0">
                <button class="btn small" type="button" @click="copyClone">复制克隆命令</button>
              </div>

              <div class="cr-line">
                <span>CR（test 首次部署）</span>
                <span v-if="detail.cr?.status === 'approved'" class="pill success"><i></i>已CR · {{ detail.cr.approverName }}</span>
                <span v-else-if="detail.cr?.status === 'pending'" class="pill warning"><i></i>待 CR · {{ detail.cr.approverName }}</span>
                <span v-else-if="detail.cr?.status === 'rejected'" class="pill danger"><i></i>CR 驳回</span>
                <span v-else class="pill muted"><i></i>未指定</span>
                <span class="muted-text" style="font-size:12px">{{ serviceLabel }}</span>
                <button class="link" type="button" @click="crOpen = true">
                  {{ detail.cr?.status === 'pending' ? '重新指定' : '指定 CR' }}
                </button>
              </div>
            </template>

            <template v-else>
              <p class="hint-line">
                还没有开发分支。分支从 main 拉出，开发提交都在这条分支上进行。
              </p>

              <label class="field" style="margin-top:14px">创建新分支
                <div class="field-inline">
                  <span class="suffix-preview"><code>{{ today }}-</code></span>
                  <input v-model="suffix" placeholder="bugfix-ledger" style="min-width:180px">
                  <button class="btn primary small" type="button" :disabled="saving" @click="createBranch">
                    创建分支
                  </button>
                </div>
                <span class="suffix-preview" style="margin-top:8px; display:block">
                  预览：<code>{{ branchPreview }}</code>
                </span>
              </label>

              <label class="field" style="margin-top:10px">或绑定已有分支
                <div class="field-inline">
                  <input v-model="existingBranch" placeholder="feature/REQ-006">
                  <button class="btn small" type="button" :disabled="saving" @click="bindBranch">
                    绑定分支
                  </button>
                </div>
              </label>
            </template>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><h3>接入信息</h3></div>
          <div class="panel-body rule-list">
            <div><b>仓库</b><code>{{ project?.repoUrl || '-' }}</code></div>
            <div><b>分支映射</b>dev → dev；test → test；uat → uat；prod → main</div>
            <div><b>CR 审批人</b>张工 / 李工（test 首次部署时生效）</div>
            <div><b>部署入口</b>在「后端部署」「前端部署」菜单操作，本页只负责需求与分支。</div>
          </div>
        </div>
      </div>

      <div class="panel" style="margin-top:16px">
        <div class="panel-head"><h3>环境状态</h3></div>
        <div class="panel-body env-matrix">
          <div v-for="(envs, service) in detail.services" :key="service" class="env-matrix-row">
            <strong>{{ service }}</strong>
            <div class="env-matrix-cells">
              <span v-for="env in envKeys" :key="env" class="pill" :class="statusMeta(envs[env].status).cls">
                <i></i>{{ env }} · {{ statusMeta(envs[env].status).label }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="page-loading">需求不存在或已被删除</div>

    <template #footer>
      <button v-if="!requirementId" class="btn" type="button" @click="visible = false">取消</button>
      <button v-if="!requirementId" class="btn primary" type="button" :disabled="saving" @click="createRequirement">
        {{ saving ? '创建中…' : '创建需求' }}
      </button>
      <button v-else class="btn primary" type="button" @click="visible = false">关闭</button>
    </template>
  </BaseModal>

  <CrDialog
    v-model="crOpen"
    :requirement="detail"
    :service="serviceKey"
    :project-id="projectId"
    @saved="onCrSaved"
  />
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import BaseModal from './BaseModal.vue';
import CrDialog from './CrDialog.vue';
import api from '../api/client';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  requirementId: { type: String, default: '' },
  projectId: { type: String, default: 'app-api' },
  project: { type: Object, default: null }
});
const emit = defineEmits(['update:modelValue', 'saved']);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
});

const statusMeta = (status) => {
  const map = {
    success: { label: '成功', cls: 'success' },
    failed: { label: '失败', cls: 'danger' },
    conflict: { label: '合并冲突', cls: 'danger' },
    wait_cr: { label: '待 CR', cls: 'warning' },
    ready: { label: '可部署', cls: 'info' },
    rejected: { label: 'CR 驳回', cls: 'danger' },
    running: { label: '部署中', cls: 'running' },
    locked: { label: '未开始', cls: 'muted' }
  };
  return map[status] || map.locked;
};

const envKeys = ['dev', 'test', 'uat', 'prod'];
const detail = ref(null);
const loading = ref(false);
const saving = ref(false);
const suffix = ref('');
const existingBranch = ref('');
const crOpen = ref(false);
const approvers = ref([]);

const serviceKey = computed(() => props.project?.service
  || Object.keys(detail.value?.services || {})[0]
  || 'api');
const serviceLabel = computed(() => (serviceKey.value === 'api'
  ? '后端 api · app-api'
  : '前端 web · app-web'));

const form = reactive({ title: '', description: '', related: [] });

const today = computed(() => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}${month}${day}`;
});
const branchPreview = computed(() => (suffix.value ? `${today.value}-${suffix.value}` : `${today.value}-…`));

function resetCreateForm() {
  form.title = '';
  form.description = '';
  form.related = [];
}

function toggleRelated(username) {
  const index = form.related.indexOf(username);
  if (index >= 0) form.related.splice(index, 1);
  else form.related.push(username);
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return;
    suffix.value = '';
    existingBranch.value = '';
    if (!props.requirementId) {
      resetCreateForm();
      approvers.value = [];
      try {
        const { data } = await api.get('/auth/approvers');
        approvers.value = data;
      } catch (e) {
        ElMessage.error(e.message);
      }
      return;
    }
    await loadDetail();
  }
);

async function loadDetail() {
  loading.value = true;
  try {
    const { data } = await api.get(`/requirements/${props.requirementId}`, {
      params: { projectId: props.projectId }
    });
    detail.value = data;
  } catch (e) {
    detail.value = null;
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
}

async function createRequirement() {
  if (!form.title.trim()) {
    ElMessage.error('请填写需求标题');
    return;
  }
  saving.value = true;
  try {
    const { data } = await api.post('/requirements', {
      projectId: props.projectId,
      title: form.title,
      description: form.description,
      related: form.related
    });
    ElMessage.success(`需求 ${data.id} 已创建`);
    emit('saved', data);
    visible.value = false;
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}

async function createBranch() {
  if (!suffix.value.trim()) {
    ElMessage.error('请填写分支后缀');
    return;
  }
  saving.value = true;
  try {
    const { data } = await api.post(
      `/requirements/${props.requirementId}/branch`,
      { suffix: suffix.value.trim() },
      { params: { projectId: props.projectId } }
    );
    detail.value = data;
    ElMessage.success('开发分支已创建');
    emit('saved', data);
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}

async function bindBranch() {
  if (!existingBranch.value.trim()) {
    ElMessage.error('请填写已有分支名');
    return;
  }
  saving.value = true;
  try {
    const { data } = await api.post(
      `/requirements/${props.requirementId}/branch/bind`,
      { branch: existingBranch.value.trim() },
      { params: { projectId: props.projectId } }
    );
    detail.value = data;
    ElMessage.success('分支已绑定');
    emit('saved', data);
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}

async function copyClone() {
  const command = `git fetch origin && git checkout ${detail.value.branch}`;
  try {
    await navigator.clipboard.writeText(command);
    ElMessage.success('已复制');
  } catch {
    ElMessage.error('复制失败，请手动选择');
  }
}

function onCrSaved(data) {
  detail.value = data;
  emit('saved', data);
}
</script>
