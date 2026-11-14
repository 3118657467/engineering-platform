<template>
  <div>
    <div class="page-head">
      <div>
        <h1>需求管理</h1>
        <p>只显示我创建或与我相关的需求；点需求即可创建或绑定开发分支</p>
      </div>
      <button class="btn primary" type="button" @click="openCreate">+ 新建需求</button>
    </div>

    <div class="card">
      <div class="toolbar">
        <select v-model="projectId" class="project-select" @change="onProjectChange">
          <option v-for="item in projects" :key="item.id" :value="item.id">
            {{ item.name }}{{ item.service ? ' · ' + item.service : '' }}
          </option>
        </select>
        <input v-model="query" class="search" placeholder="搜索需求编号、标题或分支">
        <select v-model="statusFilter">
          <option value="all">全部状态</option>
          <option value="none">待绑定分支</option>
          <option value="progress">进行中</option>
          <option value="waitcr">待 CR</option>
          <option value="failed">有失败</option>
          <option value="online">已上线</option>
        </select>
        <span class="muted-text" style="margin-left:auto">{{ filtered.length }} 条需求</span>
      </div>

      <div v-if="loading" class="page-loading">加载需求中…</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th style="width:32%">需求</th>
              <th style="width:20%">开发分支</th>
              <th style="width:12%">负责人</th>
              <th style="width:14%">创建时间</th>
              <th style="width:12%">状态</th>
              <th style="width:10%"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in filtered"
              :key="item.id"
              class="req-clickable"
              @click="openDetail(item.id)"
            >
              <td>
                <div class="req-cell">
                  <button class="link" type="button" @click.stop="openDetail(item.id)">{{ item.id }}</button>
                  <span class="req-title">{{ item.title }}</span>
                </div>
              </td>
              <td>
                <code v-if="item.branch">{{ item.branch }}</code>
                <span v-else class="muted-text">未绑定</span>
              </td>
              <td>{{ item.ownerName }}</td>
              <td>{{ item.createdAt }}</td>
              <td>
                <span class="pill" :class="item.status.cls"><i></i>{{ item.status.label }}</span>
              </td>
              <td class="ops">
                <button
                  v-if="item.branchStatus === 'created'"
                  class="btn small"
                  type="button"
                  @click.stop="openDetail(item.id)"
                >
                  查看
                </button>
                <button
                  v-else
                  class="btn small primary"
                  type="button"
                  @click.stop="openDetail(item.id)"
                >
                  创建/绑定分支
                </button>
              </td>
            </tr>
            <tr v-if="!filtered.length">
              <td colspan="6" class="empty">没有与我相关的需求</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <RequirementDialog
      v-model="dialogOpen"
      :requirement-id="detailId"
      :project-id="projectId"
      :project="currentProject"
      @saved="onSaved"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import api from '../api/client';
import RequirementDialog from '../components/RequirementDialog.vue';

const projects = ref([]);
const projectId = ref('app-api');
const requirements = ref([]);
const loading = ref(false);
const query = ref('');
const statusFilter = ref('all');
const dialogOpen = ref(false);
const detailId = ref('');

const currentProject = computed(() => projects.value.find((item) => item.id === projectId.value) || null);

const filtered = computed(() => {
  const keyword = query.value.trim().toLowerCase();
  return requirements.value.filter((item) => {
    const matchesQuery =
      !keyword ||
      item.id.toLowerCase().includes(keyword) ||
      item.title.toLowerCase().includes(keyword) ||
      (item.branch || '').toLowerCase().includes(keyword);
    let matchesStatus = true;
    if (statusFilter.value === 'none') matchesStatus = item.branchStatus !== 'created';
    if (statusFilter.value === 'progress') matchesStatus = item.status.label === '进行中';
    if (statusFilter.value === 'waitcr') matchesStatus = item.status.label === '待 CR';
    if (statusFilter.value === 'failed') matchesStatus = item.status.label === '有失败';
    if (statusFilter.value === 'online') matchesStatus = item.status.label === '已上线';
    return matchesQuery && matchesStatus;
  });
});

async function loadProjects() {
  try {
    const { data } = await api.get('/projects');
    projects.value = data;
    if (data.length && !data.some((item) => item.id === projectId.value)) {
      projectId.value = data[0].id;
    }
  } catch (e) {
    ElMessage.error(e.message);
  }
}

async function loadRequirements() {
  loading.value = true;
  try {
    const { data } = await api.get('/requirements', { params: { projectId: projectId.value } });
    requirements.value = data;
  } catch (e) {
    requirements.value = [];
    ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
}

function onProjectChange() {
  dialogOpen.value = false;
  detailId.value = '';
  loadRequirements();
}

function openCreate() {
  detailId.value = '';
  dialogOpen.value = true;
}

function openDetail(id) {
  detailId.value = id;
  dialogOpen.value = true;
}

function onSaved() {
  loadRequirements();
}

onMounted(async () => {
  await loadProjects();
  await loadRequirements();
});
</script>
