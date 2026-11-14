<template>
  <div>
    <div class="page-head">
      <div>
        <h1>审批待办</h1>
        <p>仅 test 首次部署需要审批；通过后平台自动合并到 test 并部署对应服务</p>
      </div>
    </div>

    <el-card shadow="never" class="card">
      <div class="toolbar">
        <el-input v-model="query" class="search" clearable placeholder="搜索需求编号、标题或分支">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="serviceFilter" style="width: 140px">
          <el-option label="全部服务" value="all" />
          <el-option label="后端 api" value="api" />
          <el-option label="前端 web" value="web" />
        </el-select>
        <span class="muted-text" style="margin-left:auto">{{ filtered.length }} 条待审批</span>
      </div>

      <el-table v-loading="approvalStore.loading" :data="filtered" row-key="crId" border>
        <el-table-column label="需求" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.id }}</el-link>
            <div class="title">{{ row.title }}</div>
          </template>
        </el-table-column>
        <el-table-column label="服务" width="110">
          <template #default="{ row }">
            <el-tag :type="row.service === 'api' ? 'info' : 'primary'" effect="light" round>
              {{ row.service === 'api' ? '后端 api' : '前端 web' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分支" min-width="170">
          <template #default="{ row }"><code>{{ row.branch || '-' }}</code></template>
        </el-table-column>
        <el-table-column label="提交" width="100">
          <template #default="{ row }"><code>{{ row.tipSha || '-' }}</code></template>
        </el-table-column>
        <el-table-column prop="requesterName" label="提交人" width="100" />
        <el-table-column prop="time" label="提交时间" width="110" />
        <el-table-column label="状态" width="100">
          <template #default><el-tag type="warning" effect="light" round>待 CR</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
            <el-button size="small" type="danger" plain :disabled="!row.canAct" @click="reject(row)">
              驳回
            </el-button>
            <el-button size="small" type="primary" :disabled="!row.canAct" @click="approve(row)">
              通过
            </el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无待审批事项" /></template>
      </el-table>

      <div v-if="!auth.isCrAdmin" class="muted-text" style="padding-top: 10px; font-size: 12px">
        当前是开发视角，仅 CR 管理员可操作审批。
      </div>
    </el-card>

    <RequirementDialog
      v-model="detailOpen"
      :requirement-id="detailId"
      :project-id="detailProjectId"
      :project="detailProject"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import api from '../api/client';
import RequirementDialog from '../components/RequirementDialog.vue';
import { useApprovalStore } from '../stores/approval';
import { useAuthStore } from '../stores/auth';

const approvalStore = useApprovalStore();
const auth = useAuthStore();
const query = ref('');
const serviceFilter = ref('all');
const detailOpen = ref(false);
const detailId = ref('');
const detailProjectId = ref('app-api');
const projects = ref([]);
const detailProject = computed(
  () => projects.value.find((item) => item.id === detailProjectId.value) || null
);

const filtered = computed(() => {
  const keyword = query.value.trim().toLowerCase();
  return approvalStore.items.filter((row) => {
    const matchesQuery =
      !keyword ||
      row.id.toLowerCase().includes(keyword) ||
      row.title.toLowerCase().includes(keyword) ||
      (row.branch || '').toLowerCase().includes(keyword);
    const matchesService = serviceFilter.value === 'all' || row.service === serviceFilter.value;
    return matchesQuery && matchesService;
  });
});

function openDetail(row) {
  detailProjectId.value = row.projectId || 'app-api';
  detailId.value = row.id;
  detailOpen.value = true;
}

async function loadProjects() {
  try {
    const { data } = await api.get('/projects');
    projects.value = data || [];
  } catch {
    projects.value = [];
  }
}

async function approve(row) {
  try {
    await ElMessageBox.confirm(
      `通过 ${row.id} 的 CR 后将自动部署到 test，是否继续？`,
      '通过审批',
      { confirmButtonText: '通过', cancelButtonText: '取消', type: 'warning' }
    );
    const { data } = await api.post(`/approvals/${row.crId}/approve`, {});
    ElMessage.success(data.message);
    await approvalStore.refresh();
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '操作失败');
  }
}

async function reject(row) {
  try {
    await ElMessageBox.confirm(
      `驳回 ${row.id} 的 CR 后，需要重新指定审批人才能再部署 test。`,
      '驳回审批',
      { confirmButtonText: '驳回', cancelButtonText: '取消', type: 'error' }
    );
    const { data } = await api.post(`/approvals/${row.crId}/reject`, {});
    ElMessage.success(data.message);
    await approvalStore.refresh();
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '操作失败');
  }
}

onMounted(() => {
  loadProjects();
  approvalStore.refresh();
});
</script>
