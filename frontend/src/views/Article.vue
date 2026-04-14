<template>
  <div class="article-page">
    <template v-if="loading">
      <section class="paper article-skeleton">
        <el-skeleton :rows="8" animated />
      </section>
    </template>
    <template v-else-if="article">
      <div class="layout-grid">
        <main class="article-main">
          <section class="paper article-header">
            <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
            <div class="meta">
              <el-tag size="small">{{ article.category }}</el-tag>
              <el-tag :type="difficultyType" size="small">{{ article.difficulty }}</el-tag>
              <span class="source">{{ article.source }}</span>
              <span class="date">{{ formatDate(article.publishedAt) }}</span>
            </div>
            <h1 class="title">{{ article.title }}</h1>
            <div class="keywords" v-if="article.keywords">
              <span class="kw-label">关键词：</span>
              <el-tag v-for="kw in keywordList" :key="kw" size="small" class="kw-tag">{{ kw }}</el-tag>
            </div>
          </section>

          <section v-if="article.summary" class="paper summary-card">
            <div class="summary-title"><el-icon><InfoFilled /></el-icon> AI 摘要</div>
            <p class="summary-text">{{ article.summary }}</p>
          </section>

          <section class="paper content-card">
            <div class="selection-tip">支持划词或划句翻译，结果将记录在“我的词汇”中。</div>
            <div
              ref="articleContentRef"
              class="article-content"
              @mouseup="handleTextSelection"
              @touchend.passive="handleTextSelection"
            >
              <p v-for="(para, i) in paragraphs" :key="i" class="paragraph">{{ para }}</p>
            </div>

            <WordPopover
              v-if="selectedText"
              :text="selectedText"
              :mode="selectionMode"
              :context="selectedContext"
              :position="popoverPos"
              @close="selectedText = ''"
            />
          </section>

          <section class="paper action-card">
            <div class="actions">
              <el-button type="primary" :icon="Document" @click="goToExercise">开始练习</el-button>
              <el-button :icon="Check" @click="recordComplete" :loading="recording">标记已读</el-button>
              <el-link :href="article.url" target="_blank" type="primary">
                查看原文 <el-icon><Link /></el-icon>
              </el-link>
            </div>
          </section>
        </main>

        <aside class="paper copilot-panel">
          <div class="copilot-title">Article Copilot</div>
          <p class="copilot-subtitle">围绕当前文章提问：主旨、细节、难句、作者观点都可以。</p>

          <div class="chat-messages" ref="chatScrollRef">
            <div
              v-for="(msg, idx) in chatMessages"
              :key="idx"
              :class="['msg', msg.role === 'user' ? 'user' : 'assistant']"
            >
              <div class="bubble">{{ msg.content }}</div>
            </div>
            <div v-if="chatLoading" class="msg assistant">
              <div class="bubble">正在思考中...</div>
            </div>
          </div>

          <div class="chat-input-wrap">
            <el-input
              v-model="chatInput"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="例如：这篇文章的核心论点是什么？"
              @keydown.enter.exact.prevent="sendChat"
            />
            <el-button type="primary" :loading="chatLoading" @click="sendChat">发送</el-button>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticleDetailApi } from '@/api/news'
import { articleChatApi, recordActionApi } from '@/api/learning'
import WordPopover from '@/components/WordPopover.vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, Check, Link, InfoFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const article = ref(null)
const loading = ref(true)
const recording = ref(false)

const selectedText = ref('')
const selectionMode = ref('word')
const selectedContext = ref('')
const popoverPos = ref({ x: 0, y: 0 })
const articleContentRef = ref(null)

const chatInput = ref('')
const chatLoading = ref(false)
const chatScrollRef = ref(null)
const chatMessages = ref([
  { role: 'assistant', content: '你好，我是你的阅读助手。你可以直接问我这篇文章的任何问题。' },
])

const keywordList = computed(() =>
  article.value?.keywords ? article.value.keywords.split(',').map(k => k.trim()).filter(Boolean) : []
)
const paragraphs = computed(() => article.value?.content?.split('\n').filter(p => p.trim()) || [])
const difficultyType = computed(() => {
  const map = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }
  return map[article.value?.difficulty] || 'info'
})

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : ''
}

function handleTextSelection(e) {
  const isTouch = e?.type?.startsWith('touch')
  if (isTouch) {
    // 移动端在 touchend 后选区信息可能延迟更新，稍后读取更稳定。
    window.setTimeout(() => applySelection(e), 120)
    return
  }
  applySelection(e)
}

function applySelection(e) {
  const selection = window.getSelection()
  const text = selection?.toString().trim() || ''
  const range = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null

  if (!isSelectionInsideArticle(range)) {
    selectedText.value = ''
    return
  }

  if (!text || text.length > 220) {
    selectedText.value = ''
    return
  }

  const isWord = /^[A-Za-z][A-Za-z'-]{0,28}$/.test(text)
  const isSentence = /[A-Za-z]/.test(text) && text.split(/\s+/).length >= 2

  if (!isWord && !isSentence) {
    selectedText.value = ''
    return
  }

  selectedText.value = text
  selectionMode.value = isWord ? 'word' : 'sentence'
  selectedContext.value = selection.anchorNode?.parentElement?.innerText?.trim() || text
  popoverPos.value = resolvePopoverPosition(e, range)
}

function isSelectionInsideArticle(range) {
  if (!range || !articleContentRef.value) return false
  const node = range.commonAncestorContainer
  const target = node?.nodeType === Node.TEXT_NODE ? node.parentNode : node
  return !!target && articleContentRef.value.contains(target)
}

function resolvePopoverPosition(e, range) {
  if (e?.changedTouches?.[0]) {
    return { x: e.changedTouches[0].clientX, y: e.changedTouches[0].clientY }
  }
  if (typeof e?.clientX === 'number' && typeof e?.clientY === 'number') {
    return { x: e.clientX, y: e.clientY }
  }
  const rect = range?.getBoundingClientRect?.()
  if (rect) {
    return {
      x: rect.left + rect.width / 2,
      y: rect.bottom,
    }
  }
  return { x: window.innerWidth / 2, y: window.innerHeight / 2 }
}

let selectionTimer = null
function onSelectionChange() {
  if (!articleContentRef.value) return
  if (selectionTimer) window.clearTimeout(selectionTimer)
  selectionTimer = window.setTimeout(() => applySelection(), 120)
}

async function sendChat() {
  const question = chatInput.value.trim()
  if (!question || !article.value) return

  chatMessages.value.push({ role: 'user', content: question })
  chatInput.value = ''
  chatLoading.value = true
  scrollToBottom()

  try {
    const history = chatMessages.value.slice(-10).map(m => ({ role: m.role, content: m.content }))
    const res = await articleChatApi({ articleId: article.value.id, question, history })
    chatMessages.value.push({ role: 'assistant', content: res.answer || '暂时没有得到有效回答。' })
  } catch (err) {
    ElMessage.error('AI 问答失败，请稍后重试')
  } finally {
    chatLoading.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatScrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function recordComplete() {
  recording.value = true
  try {
    await recordActionApi({ articleId: article.value.id, actionType: 'ARTICLE_COMPLETE' })
    ElMessage.success('已标记为已读')
  } finally {
    recording.value = false
  }
}

function goToExercise() {
  router.push(`/exercise/${article.value.id}`)
}

let startTime = Date.now()
onUnmounted(async () => {
  document.removeEventListener('selectionchange', onSelectionChange)
  if (selectionTimer) window.clearTimeout(selectionTimer)
  if (article.value) {
    const duration = Math.round((Date.now() - startTime) / 1000)
    await recordActionApi({ articleId: article.value.id, actionType: 'READ', duration })
  }
})

onMounted(async () => {
  document.addEventListener('selectionchange', onSelectionChange)
  try {
    article.value = await getArticleDetailApi(route.params.id)
    await recordActionApi({ articleId: route.params.id, actionType: 'READ' })
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.article-page {
  --paper: rgba(250, 246, 237, 0.92);
  --line: rgba(24, 34, 45, 0.1);
  --ink: #18222d;
  --muted: #64707d;
  min-height: calc(100vh - 140px);
}

.article-skeleton {
  min-height: calc(100vh - 150px);
}

.layout-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.article-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.paper {
  border: 1px solid var(--line);
  border-radius: 24px;
  background: var(--paper);
  backdrop-filter: blur(8px);
  box-shadow: 0 20px 60px rgba(20, 27, 34, 0.08);
  padding: 20px;
}

.meta { display: flex; align-items: center; gap: 8px; margin: 12px 0; }
.source, .date { color: var(--muted); font-size: 13px; }
.title { font-size: 30px; font-weight: 700; line-height: 1.25; margin-top: 8px; color: var(--ink); }
.keywords { margin-top: 12px; }
.kw-label { color: var(--muted); font-size: 13px; }
.kw-tag { margin: 2px; }

.summary-title { display: flex; align-items: center; gap: 6px; font-weight: 600; margin-bottom: 8px; color: #a33a2b; }
.summary-text { color: #3b4652; line-height: 1.8; }
.selection-tip { font-size: 13px; color: var(--muted); margin-bottom: 12px; }
.article-content { line-height: 2; }
.paragraph { margin-bottom: 16px; font-size: 17px; color: #1f2d3d; text-align: justify; }
.paragraph::selection { background: #ffd9b6; }

.actions { display: flex; align-items: center; gap: 16px; }

.copilot-panel {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: calc(100vh - 110px);
}

.copilot-title { font-size: 18px; font-weight: 700; color: var(--ink); }
.copilot-subtitle { color: var(--muted); font-size: 13px; }

.chat-messages {
  flex: 1;
  min-height: 280px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.msg.assistant { justify-content: flex-start; }

.bubble {
  max-width: 95%;
  padding: 10px 12px;
  border-radius: 14px;
  line-height: 1.6;
  font-size: 14px;
}

.msg.user .bubble { background: #a33a2b; color: #fff; border-bottom-right-radius: 4px; }
.msg.assistant .bubble { background: #f1ede4; color: #26313d; border-bottom-left-radius: 4px; }

.chat-input-wrap { display: flex; flex-direction: column; gap: 8px; }

@media (max-width: 1100px) {
  .layout-grid { grid-template-columns: 1fr; }
  .copilot-panel { position: static; max-height: none; }
}

@media (max-width: 768px) {
  .paper {
    border-radius: 16px;
    padding: 14px;
  }

  .title {
    font-size: 24px;
  }

  .meta,
  .actions {
    flex-wrap: wrap;
  }

  .paragraph {
    font-size: 16px;
    line-height: 1.85;
  }
}
</style>
