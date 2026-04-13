<template>
  <div class="vocabulary-page">
    <div class="page-header">
      <h2>我的词汇</h2>
      <el-input
        v-model="search"
        placeholder="搜索单词..."
        :prefix-icon="Search"
        clearable
        style="width: 240px"
      />
    </div>

    <div v-loading="loading">
      <el-table :data="filteredList" stripe border style="width: 100%; border-radius: 12px; overflow: hidden">
        <el-table-column prop="word" label="单词" width="160">
          <template #default="{ row }">
            <span class="word-text">{{ row.word }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="definition" label="释义（英文）" min-width="200" />
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

      <el-empty v-if="!loading && vocabularyList.length === 0" description="还没有收藏单词，阅读文章时选中单词查询即可自动保存" />
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
    ? vocabularyList.value.filter(v => v.word.includes(search.value.toLowerCase()))
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
.page-header { display: flex; align-items: center; justify-content: space-between; }
.page-header h2 { font-size: 20px; font-weight: 700; }
.word-text { font-weight: 600; color: #409EFF; font-size: 16px; }
</style>
