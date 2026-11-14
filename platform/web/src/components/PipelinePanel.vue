<template>
  <div class="card section-card">
    <div class="panel-head">
      <h3>部署进度</h3>
      <span class="muted-text">开始于 {{ pipeline.startedAt }}</span>
    </div>
    <div class="panel-body">
      <div class="pipeline-head">
        <div>
          <strong>{{ statusTitle }}</strong>
          <span class="muted-text">{{ current ? `${current.id} · ${current.branch}` : '' }}</span>
        </div>
        <div class="pipeline-actions">
          <span class="pill" :class="statusTone">第 {{ currentIndex }}/{{ pipeline.requirements.length }} 个分支</span>
          <button
            v-if="pipeline.status === 'running'"
            class="btn small danger"
            type="button"
            @click="$emit('abort')"
          >
            终止部署
          </button>
        </div>
      </div>

      <div class="steps">
        <div v-for="step in steps" :key="step.seq" class="step" :class="step.status">
          <span class="step-mark">{{ mark(step.status) }}</span>
          <div class="step-text">
            <strong>{{ step.name }}</strong>
            <small>{{ stepText(step) }}</small>
          </div>
        </div>
      </div>

      <div v-if="pipeline.status === 'success'" class="success-banner">
        部署成功：{{ pipeline.requirements.map((item) => item.id).join('、') }}
        已发布到 {{ env }}（{{ pipeline.startedAt }} 开始）
      </div>
      <div v-else-if="pipeline.status === 'terminated'" class="error-banner">
        部署已终止，当前步骤已置为失败，环境已回到可部署状态。
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  pipeline: { type: Object, required: true },
  env: { type: String, default: '' }
});
defineEmits(['abort']);

const current = computed(() => {
  const list = props.pipeline.requirements || [];
  return list.find((item) => item.status === 'running' || item.status === 'pending')
    || list[list.length - 1]
    || null;
});

const currentIndex = computed(() => {
  const list = props.pipeline.requirements || [];
  const index = list.findIndex((item) => item === current.value);
  return index < 0 ? list.length : index + 1;
});

const steps = computed(() => current.value?.steps || []);

const statusTitle = computed(() => {
  if (props.pipeline.status === 'running') return '部署进行中';
  if (props.pipeline.status === 'terminated') return '部署已终止';
  return '部署完成';
});

const statusTone = computed(() => {
  if (props.pipeline.status === 'running') return 'running';
  if (props.pipeline.status === 'success') return 'success';
  return 'danger';
});

function mark(status) {
  if (status === 'success') return '✓';
  if (status === 'running') return '…';
  if (status === 'failed') return '×';
  return '';
}

function stepText(step) {
  if (step.status === 'running') return '进行中';
  if (step.status === 'success') return `完成 ${step.duration || ''}`.trim();
  if (step.status === 'failed') return '失败';
  return '等待';
}
</script>
