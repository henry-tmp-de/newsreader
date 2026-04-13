<template>
  <teleport to="body">
    <div
      class="word-popover"
      :style="{ left: safeX + 'px', top: safeY + 'px' }"
      v-if="resultInfo || loading"
    >
      <div class="popover-header">
        <div class="header-main">
          <span class="word">{{ text }}</span>
          <el-tag size="small" :type="mode === 'sentence' ? 'warning' : 'info'">{{ modeLabel }}</el-tag>
        </div>
        <el-button :icon="Close" circle size="small" text @click="$emit('close')" />
      </div>

      <div v-if="loading" class="loading">
        <el-skeleton :rows="2" animated />
      </div>

      <template v-else-if="resultInfo">
        <div class="definition">
          <div class="label">释义</div>
          <div class="value">{{ resultInfo.definition }}</div>
        </div>
        <div class="translation" v-if="resultInfo.chinese">
          <div class="label">中文</div>
          <div class="value zh">{{ resultInfo.chinese }}</div>
        </div>
        <div class="example" v-if="resultInfo.example">
          <div class="label">示例</div>
          <div class="value italic">{{ resultInfo.example }}</div>
        </div>
        <div class="saved-tip">
          <el-icon color="#67C23A"><CircleCheck /></el-icon>
          <span>已加入词汇记录</span>
        </div>
      </template>
    </div>
  </teleport>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { lookupTextApi } from '@/api/learning'
import { Close, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  text: { type: String, required: true },
  mode: { type: String, default: 'word' },
  context: { type: String, default: '' },
  position: { type: Object, default: () => ({ x: 0, y: 0 }) },
})
defineEmits(['close'])

const resultInfo = ref(null)
const loading = ref(false)
const safeX = ref(0)
const safeY = ref(0)
const modeLabel = computed(() => (props.mode === 'sentence' ? '句子' : '词汇'))

watch(() => props.text, async (w) => {
  if (!w) return
  resultInfo.value = null
  loading.value = true
  // 计算安全位置（避免超出屏幕）
  safeX.value = Math.min(props.position.x, window.innerWidth - 340)
  safeY.value = props.position.y + 16
  try {
    resultInfo.value = await lookupTextApi({ text: w, context: props.context, type: props.mode })
  } finally {
    loading.value = false
  }
}, { immediate: true })
</script>

<style scoped>
.word-popover {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,.18);
  padding: 16px;
  width: 320px;
  border: 1px solid #e4e7ed;
}
.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.header-main { display: flex; align-items: center; gap: 8px; max-width: 270px; }
.word { font-size: 16px; font-weight: 700; color: #409EFF; overflow-wrap: anywhere; }
.label { font-size: 11px; color: #909399; text-transform: uppercase; margin-bottom: 2px; }
.value { font-size: 14px; color: #303133; line-height: 1.6; }
.zh { font-size: 16px; color: #606266; font-weight: 500; }
.italic { font-style: italic; color: #606266; }
.definition, .translation, .example {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.example { border-bottom: none; }
.saved-tip {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  font-size: 12px;
  color: #67C23A;
}
.loading { padding: 8px 0; }
</style>
