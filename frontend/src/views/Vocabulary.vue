<template>
  <div class="vocabulary-page">
    <section class="hero paper page-header">
      <div>
        <p class="kicker">VOCABULARY NOTEBOOK</p>
        <h2>我的词句</h2>
      </div>
      <el-input
        v-model="search"
        placeholder="搜索词汇或句子..."
        :prefix-icon="Search"
        clearable
        style="width: 240px"
      />
    </section>

    <div v-loading="loading" class="paper table-wrap">
      <el-table :data="filteredList" stripe border style="width: 100%; border-radius: 12px; overflow: hidden">
        <el-table-column prop="word" label="单词" width="160">
          <template #default="{ row }">
            <span class="word-text">{{ row.word }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.entryType === 'SENTENCE' ? 'warning' : 'info'">
              {{ row.entryType === 'SENTENCE' ? '句子' : '词汇' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="definition" label="释义（英文）" min-width="200" />
        <el-table-column prop="chinese" label="中文" min-width="180" />
        <el-table-column prop="contextText" label="上下文" min-width="220" show-overflow-tooltip />
        <el-table-column label="掌握程度" width="160">
          <template #default="{ row }">
            <el-rate v-model="row.masteryLevel" :max="5" disabled />
          </template>
        </el-table-column>
        <el-table-column label="下次复习" width="160">
          <template #default="{ row }">
            <el-tag :type="reviewTagType(row.nextReviewAt)" size="small">
              {{ formatReview(row.nextReviewAt) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewCount" label="复习次数" width="100" align="center" />
        <el-table-column label="添加时间" width="140">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && vocabularyList.length === 0" description="还没有收藏内容，阅读文章时划词或划句即可自动保存" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getVocabularyApi } from '@/api/learning'
import { Search } from '@element-plus/icons-vue'

const vocabularyList = ref([])
const loading = ref(true)
const search = ref('')

const filteredList = computed(() =>
  search.value
    ? vocabularyList.value.filter(v =>
        (v.word || '').toLowerCase().includes(search.value.toLowerCase()) ||
        (v.definition || '').toLowerCase().includes(search.value.toLowerCase()) ||
        (v.chinese || '').includes(search.value)
      )
    : vocabularyList.value
)

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '-'
}

function formatReview(d) {
  if (!d) return '待安排'
  const diff = new Date(d) - new Date()
  if (diff < 0) return '立即复习'
  const days = Math.ceil(diff / 86400000)
  return days === 0 ? '今天' : `${days}天后`
}

function reviewTagType(d) {
  if (!d) return 'info'
  const diff = new Date(d) - new Date()
  if (diff < 0) return 'danger'
  if (diff < 86400000) return 'warning'
  return 'success'
}

onMounted(async () => {
  try {
    vocabularyList.value = await getVocabularyApi()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.vocabulary-page { display: flex; flex-direction: column; gap: 20px; }
.paper {
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 24px;
  background: rgba(250, 246, 237, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 20px 60px rgba(20, 27, 34, 0.08);
}
.page-header { display: flex; align-items: center; justify-content: space-between; }
.hero { padding: 18px 20px; }
.kicker { font-size: 12px; letter-spacing: .2em; color: #a33a2b; margin-bottom: 8px; }
.page-header h2 { font-size: 28px; font-weight: 700; color: #18222d; }
.table-wrap { padding: 14px; }
.word-text { font-weight: 600; color: #a33a2b; font-size: 15px; }
</style>
