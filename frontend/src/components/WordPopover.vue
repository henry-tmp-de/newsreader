<template>
  <teleport to="body">
    <div
      ref="popoverRef"
      class="word-popover"
      :style="{ left: safeX + 'px', top: safeY + 'px', width: panelWidth + 'px' }"
      v-if="resultInfo || loading"
    >
      <div class="popover-header" @mousedown="startDrag">
        <div class="header-main">
          <span class="word">{{ text }}</span>
          <el-tag size="small" :type="mode === 'sentence' ? 'warning' : 'info'">{{ modeLabel }}</el-tag>
        </div>
        <el-button :icon="Close" circle size="small" text @mousedown.stop @click="$emit('close')" />
      </div>

      <div v-if="loading" class="loading">
        <el-skeleton :rows="2" animated />
      </div>

      <template v-else-if="resultInfo">
        <div class="definition">
          <div class="label">释义</div>
          <div class="value"><TypewriterText :text="resultInfo.definition" :speed="14" /></div>
        </div>
        <div class="translation" v-if="resultInfo.chinese">
          <div class="label">中文</div>
          <div class="value zh"><TypewriterText :text="resultInfo.chinese" :speed="10" /></div>
        </div>
        <div class="example" v-if="resultInfo.example">
          <div class="label">示例</div>
          <div class="value italic"><TypewriterText :text="resultInfo.example" :speed="14" /></div>
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
import { computed, onUnmounted, ref, watch } from 'vue'
import { lookupTextApi } from '@/api/learning'
import { Close, CircleCheck } from '@element-plus/icons-vue'
import TypewriterText from '@/components/TypewriterText.vue'

const props = defineProps({
  text: { type: String, required: true },
  mode: { type: String, default: 'word' },
  context: { type: String, default: '' },
  position: { type: Object, default: () => ({ x: 0, y: 0, top: 0 }) },
})
defineEmits(['close'])

const resultInfo = ref(null)
const loading = ref(false)
const safeX = ref(0)
const safeY = ref(0)
const panelWidth = ref(320)
const popoverRef = ref(null)
const dragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })
const manualPositionLocked = ref(false)
const modeLabel = computed(() => (props.mode === 'sentence' ? '句子' : '词汇'))

// 仅更新弹框位置，不触发 API
function updatePosition() {
  panelWidth.value = Math.min(320, Math.max(260, window.innerWidth - 24))
  // 横向：以选区中点为中心，左右边界防溢出
  const preferX = props.position.x - panelWidth.value / 2
  const maxX = window.innerWidth - panelWidth.value - 12
  safeX.value = Math.max(12, Math.min(preferX, maxX))
  // 纵向：选区在屏幕下半部 → 弹框显示在文字上方；上半部 → 显示在文字下方
  const PANEL_H = 280  // 弹框估算高度
  const GAP = 10
  const rangeTop = props.position.top ?? props.position.y
  if (rangeTop > window.innerHeight / 2) {
    // 文字在下半屏：弹框出现在文字上方
    safeY.value = Math.max(8, rangeTop - PANEL_H - GAP)
  } else {
    // 文字在上半屏：弹框出现在文字下方
    const targetY = props.position.y + GAP
    safeY.value = Math.min(targetY, window.innerHeight - PANEL_H - 8)
  }
}

// text 变化时才调 API（位置变化不触发 API）
watch(() => props.text, async (newText) => {
  if (!newText) return
  manualPositionLocked.value = false
  resultInfo.value = null
  loading.value = true
  updatePosition()
  try {
    resultInfo.value = await lookupTextApi({ text: newText, context: props.context, type: props.mode })
  } catch (e) {
    resultInfo.value = {
      definition: '翻译服务暂时不可用，请稍后重试。',
      chinese: '',
      example: '',
    }
  } finally {
    loading.value = false
  }
}, { immediate: true })

// 位置单独监听，只更新坐标，不请求 API
watch(() => [props.position.x, props.position.y, props.position.top], () => {
  if (manualPositionLocked.value) return
  updatePosition()
})

// 拖拽逻辑
function startDrag(e) {
  if (e.button !== 0) return
  dragging.value = true
  manualPositionLocked.value = true
  dragOffset.value = { x: e.clientX - safeX.value, y: e.clientY - safeY.value }
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', stopDrag)
  e.preventDefault()
}

function onDragMove(e) {
  if (!dragging.value) return
  const w = panelWidth.value
  const h = popoverRef.value?.offsetHeight || 280
  safeX.value = Math.max(8, Math.min(e.clientX - dragOffset.value.x, window.innerWidth - w - 8))
  safeY.value = Math.max(8, Math.min(e.clientY - dragOffset.value.y, window.innerHeight - h - 8))
}

function stopDrag() {
  dragging.value = false
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', stopDrag)
}

onUnmounted(() => { stopDrag() })
</script>

<style scoped>
.word-popover {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,.18);
  padding: 16px;
  border: 1px solid #e4e7ed;
}
.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  cursor: move;
  user-select: none;
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

@media (max-width: 768px) {
  .word-popover {
    padding: 12px;
    border-radius: 10px;
  }
}
</style>
