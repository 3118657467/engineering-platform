<template>
  <BaseModal v-model="visible" title="确认部署" width="min(560px, 100%)">
    <div class="kv">
      <span>服务</span><span><span class="pill" :class="service === 'api' ? 'info' : 'primary'">{{ serviceLabel }}</span></span>
      <span>目标环境</span><span><code>{{ env }}</code></span>
      <span>分支数</span><span>{{ rows.length }} 个</span>
    </div>

    <div class="confirm-list">
      <div v-for="row in rows" :key="row.id">
        <code>{{ row.branch }}</code>
        <span>{{ row.id }} · {{ row.title }}</span>
      </div>
    </div>

    <div class="notice">确认后加入部署队列。若该环境已有部署进行中，将无法提交。</div>

    <template #footer>
      <button class="btn" type="button" @click="visible = false">取消</button>
      <button class="btn primary" type="button" :disabled="submitting" @click="$emit('confirm')">
        {{ submitting ? '提交中…' : '确认部署' }}
      </button>
    </template>
  </BaseModal>
</template>

<script setup>
import { computed } from 'vue';
import BaseModal from './BaseModal.vue';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  rows: { type: Array, default: () => [] },
  service: { type: String, default: 'api' },
  env: { type: String, default: 'dev' },
  submitting: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue', 'confirm']);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
});
const serviceLabel = computed(() =>
  props.service === 'api' ? '后端 api · app-api' : '前端 web · app-web'
);
</script>
