<template>
  <span class="typewriter-wrap">
    <span>{{ displayedText }}</span>
    <span v-if="showCursor && isTyping" class="cursor">|</span>
  </span>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  text: { type: String, default: '' },
  speed: { type: Number, default: 18 },
  showCursor: { type: Boolean, default: true },
})

const displayedText = ref('')
const isTyping = ref(false)
let timer = null

function startTyping(content) {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }

  if (!content) {
    displayedText.value = ''
    isTyping.value = false
    return
  }

  displayedText.value = ''
  isTyping.value = true
  let index = 0

  timer = window.setInterval(() => {
    if (index >= content.length) {
      isTyping.value = false
      window.clearInterval(timer)
      timer = null
      return
    }

    const step = content.length > 280 ? 2 : 1
    displayedText.value += content.slice(index, index + step)
    index += step
  }, Math.max(10, props.speed))
}

watch(
  () => props.text,
  (val) => startTyping(val || ''),
  { immediate: true }
)

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.typewriter-wrap {
  white-space: pre-wrap;
  word-break: break-word;
}

.cursor {
  display: inline-block;
  margin-left: 1px;
  color: currentColor;
  animation: blink 0.9s steps(1, end) infinite;
}

@keyframes blink {
  0%,
  49% {
    opacity: 1;
  }
  50%,
  100% {
    opacity: 0;
  }
}
</style>