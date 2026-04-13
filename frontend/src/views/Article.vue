<template>
  <div class="article-page" v-loading="loading">
    <template v-if="article">
      <!-- 文章头部 -->
      <el-card class="article-header" shadow="never">
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
      </el-card>

      <!-- 摘要 -->
      <el-card v-if="article.summary" class="summary-card" shadow="never">
        <div class="summary-title"><el-icon><InfoFilled /></el-icon> AI 摘要</div>
        <p class="summary-text">{{ article.summary }}</p>
      </el-card>

      <!-- 正文 -->
      <el-card class="content-card" shadow="never">
        <div
          class="article-content"
          @mouseup="handleTextSelection"
        >
          <p v-for="(para, i) in paragraphs" :key="i" class="paragraph">{{ para }}</p>
        </div>

        <!-- 划词浮窗 -->
        <WordPopover
          v-if="selectedWord"
          :word="selectedWord"
          :context="selectedContext"
          :position="popoverPos"
          @close="selectedWord = ''"
        />
      </el-card>

      <!-- 操作栏 -->
      <el-card class="action-card" shadow="never">
        <div class="actions">
          <el-button type="primary" :icon="Document" @click="goToExercise">
            开始练习
          </el-button>
          <el-button :icon="Check" @click="recordComplete" :loading="recording">
            标记已读
          </el-button>
          <el-link :href="article.url" target="_blank" type="primary">
            查看原文 <el-icon><Link /></el-icon>
          </el-link>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticleDetailApi } from '@/api/news'
import { recordActionApi } from '@/api/learning'
import WordPopover from '@/components/WordPopover.vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, Check, Link, InfoFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const article = ref(null)
const loading = ref(true)
const recording = ref(false)
const selectedWord = ref('')
const selectedContext = ref('')
const popoverPos = ref({ x: 0, y: 0 })

const keywordList = computed(() =>
  article.value?.keywords ? article.value.keywords.split(',').map(k => k.trim()).filter(Boolean) : []
)
const paragraphs = computed(() =>
  article.value?.content?.split('\n').filter(p => p.trim()) || []
)
const difficultyType = computed(() => {
  const map = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }
  return map[article.value?.difficulty] || 'info'
})

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : ''
}

function handleTextSelection(e) {
  const selection = window.getSelection()
  const word = selection?.toString().trim()
  if (word && word.length > 0 && word.length < 30 && /^[a-zA-Z\s'-]+$/.test(word)) {
    selectedWord.value = word
    selectedContext.value = selection.anchorNode?.textContent?.trim() || ''
    popoverPos.value = { x: e.clientX, y: e.clientY }
  } else {
    selectedWord.value = ''
  }
}

async function recordComplete() {
  recording.value = true
  try {
    await recordActionApi({
      articleId: article.value.id,
      actionType: 'ARTICLE_COMPLETE',
    })
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
  if (article.value) {
    const duration = Math.round((Date.now() - startTime) / 1000)
    await recordActionApi({ articleId: article.value.id, actionType: 'READ', duration })
  }
})

onMounted(async () => {
  try {
    article.value = await getArticleDetailApi(route.params.id)
    await recordActionApi({ articleId: route.params.id, actionType: 'READ' })
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.article-page { display: flex; flex-direction: column; gap: 16px; max-width: 900px; margin: 0 auto; }
.article-header { border-radius: 12px; }
.meta { display: flex; align-items: center; gap: 8px; margin: 12px 0; }
.source, .date { color: #909399; font-size: 13px; }
.title { font-size: 24px; font-weight: 700; line-height: 1.4; margin-top: 8px; }
.keywords { margin-top: 12px; }
.kw-label { color: #909399; font-size: 13px; }
.kw-tag { margin: 2px; }
.summary-card { border-radius: 12px; border-left: 4px solid #409EFF !important; }
.summary-title { display: flex; align-items: center; gap: 6px; font-weight: 600; margin-bottom: 8px; color: #409EFF; }
.summary-text { color: #606266; line-height: 1.8; }
.content-card { border-radius: 12px; position: relative; }
.article-content { line-height: 2; }
.paragraph { margin-bottom: 16px; font-size: 16px; color: #303133; text-align: justify; }
.paragraph::selection { background: #b3d8ff; }
.action-card { border-radius: 12px; }
.actions { display: flex; align-items: center; gap: 16px; }
</style>
