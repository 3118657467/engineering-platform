<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width || '560px'"
    append-to-body
    destroy-on-close
    class="platform-dialog"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: String, default: '' }
});
const emit = defineEmits(['update:modelValue']);
</script>
