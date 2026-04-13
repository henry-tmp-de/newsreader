<template>
  <teleport to="body">
    <div
      class="word-popover"
      :style="{ left: safeX + 'px', top: safeY + 'px' }"
      v-if="wordInfo || loading"
    >
      <div class="popover-header">
        <span class="word">{{ word }}</span>
        <el-button :icon="Close" circle size="small" text @click="$emit('close')" />
      </div>

      <div v-if="loading" class="loading">
        <el-skeleton :rows="2" animated />
      </div>

      <template v-else-if="wordInfo">
        <div class="definition">
          <div class="label">释义</div>
          <div class="value">{{ wordInfo.definition }}</div>
        </div>
        <div class="translation" v-if="wordInfo.chinese">
          <div class="label">中文</div>
          <div class="value zh">{{ wordInfo.chinese }}</div>
        </div>
        <div class="example" v-if="wordInfo.example">
          <div class="label">例句</div>
          <div class="value italic">{{ wordInfo.example }}</div>
        </div>
        <div class="saved-tip">
          <el-icon color="#67C23A"><CircleCheck /></el-icon>
          <span>已加入词汇本</span>
        </div>
      </template>
    </div>
  </teleport>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { lookupWordApi } from '@/api/learning'
import { Close, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  word: { type: String, required: true },
  context: { type: String, default: '' },
  position: { type: Object, default: () => ({ x: 0, y: 0 }) },
})
defineEmits(['close'])

const wordInfo = ref(null)
const loading = ref(false)
const safeX = ref(0)
const safeY = ref(0)

watch(() => props.word, async (w) => {
  if (!w) return
  wordInfo.value = null
  loading.value = true
  // 计算安全位置（避免超出屏幕）
  safeX.value = Math.min(props.position.x, window.innerWidth - 340)
  safeY.value = props.position.y + 16
  try {
    wordInfo.value = await lookupWordApi({ word: w, context: props.context })
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
.word { font-size: 20px; font-weight: 700; color: #409EFF; }
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
