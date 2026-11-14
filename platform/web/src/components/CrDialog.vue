<template>
  <BaseModal v-model="visible" title="指定 CR 人员" width="min(520px, 100%)">
    <div class="kv">
      <span>需求</span><span>{{ requirement?.id }} · {{ requirement?.title }}</span>
      <span>服务</span><span><span class="pill" :class="service === 'api' ? 'info' : 'primary'">{{ serviceLabel }}</span></span>
      <span>目标环境</span><span><code>test</code></span>
      <span>分支</span><span><code>{{ requirement?.branch || '-' }}</code></span>
      <span>当前状态</span>
      <span>
        <span v-if="requirement?.cr?.status === 'pending'" class="pill warning"><i></i>待审批</span>
        <span v-else-if="requirement?.cr?.status === 'approved'" class="pill success"><i></i>已通过</span>
        <span v-else class="pill muted"><i></i>未指定</span>
      </span>
    </div>

    <label class="field" style="margin-bottom:14px">审批人
      <select v-model="approver">
        <option v-for="item in approvers" :key="item.username" :value="item.username">
          {{ item.displayName }}
        </option>
      </select>
    </label>

    <label class="field">备注
      <textarea v-model="comment" placeholder="给审批人的说明（可选）"></textarea>
    </label>

    <div class="notice">提交后该需求进入「审批待办」，CR 通过后才能部署到 test。</div>

    <template #footer>
      <button class="btn" type="button" @click="visible = false">取消</button>
      <button class="btn primary" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '提交 CR' }}
      </button>
    </template>
  </BaseModal>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import BaseModal from './BaseModal.vue';
import api from '../api/client';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  requirement: { type: Object, default: null },
  service: { type: String, default: 'api' },
  projectId: { type: String, default: 'app-api' }
});
const emit = defineEmits(['update:modelValue', 'saved']);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
});
const serviceLabel = computed(() =>
  props.service === 'api' ? '后端 api · app-api' : '前端 web · app-web'
);

const approvers = ref([]);
const approver = ref('');
const comment = ref('');
const submitting = ref(false);

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return;
    comment.value = '';
    try {
      const { data } = await api.get('/auth/approvers');
      approvers.value = data;
      approver.value = props.requirement?.cr?.approver || data[0]?.username || '';
    } catch (e) {
      ElMessage.error(e.message);
    }
  }
);

async function submit() {
  if (!approver.value) {
    ElMessage.error('请选择审批人');
    return;
  }
  submitting.value = true;
  try {
    const { data } = await api.post(
      `/requirements/${props.requirement.id}/cr`,
      { service: props.service, approver: approver.value, comment: comment.value },
      { params: { projectId: props.projectId } }
    );
    ElMessage.success('已提交 CR');
    emit('saved', data);
    visible.value = false;
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    submitting.value = false;
  }
}
</script>
