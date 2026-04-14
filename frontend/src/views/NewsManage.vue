<template>
  <div class="manage-page" v-loading="loading" element-loading-background="rgba(247, 242, 231, 0.72)">
    <section class="paper hero">
      <p class="kicker">NEWS CONTROL CENTER</p>
      <h2>新闻管理</h2>
      <p class="desc">在这里控制新闻抓取范围、数量，并清理不感兴趣内容，让新闻库保持高质量。</p>
    </section>

    <section class="paper fetch-panel">
      <div class="panel-header">自定义更新新闻</div>
      <el-form :inline="true" class="fetch-form">
        <el-form-item label="板块">
          <el-select v-model="selectedCategories" multiple collapse-tags collapse-tags-tooltip class="category-select" placeholder="选择板块">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="每板块数量">
          <el-input-number v-model="pageSize" :min="1" :max="50" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="fetching" @click="handleCustomFetch">开始更新</el-button>
        </el-form-item>
      </el-form>
      <p class="hint">不选板块时默认更新全部板块（technology/science/health/business/sports）。</p>
    </section>

    <section class="paper list-panel">
      <div class="table-actions">
        <div class="left">
          <el-button type="danger" plain :disabled="selectedIds.length === 0" @click="deleteSelected">
            删除选中（{{ selectedIds.length }}）
          </el-button>
          <el-button type="danger" @click="clearAll">清空新闻库</el-button>
        </div>
        <el-input v-model="keyword" placeholder="搜索标题/摘要" clearable class="keyword-input" @keyup.enter="loadArticles(true)" @clear="loadArticles(true)" />
      </div>

      <div class="table-scroll">
        <el-table :data="articles" @selection-change="onSelectChange" row-key="id" stripe>
        <el-table-column type="selection" width="50" />
        <el-table-column prop="title" label="标题" min-width="320" show-overflow-tooltip />
        <el-table-column prop="category" label="板块" width="100" />
        <el-table-column prop="source" label="来源" width="150" show-overflow-tooltip />
        <el-table-column label="时间" width="130">
          <template #default="{ row }">{{ formatDate(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" text @click="deleteOne(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="pageNum"
        v-model:page-size="tablePageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="handlePageChange"
      />
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  clearArticlesApi,
  deleteArticleApi,
  deleteArticlesBatchApi,
  fetchNewsCustomApi,
  getArticlesApi,
} from '@/api/news'

const loading = ref(false)
const fetching = ref(false)
const selectedCategories = ref([])
const pageSize = ref(10)
const categories = [
  { label: '科技', value: 'technology' },
  { label: '科学', value: 'science' },
  { label: '健康', value: 'health' },
  { label: '商业', value: 'business' },
  { label: '体育', value: 'sports' },
]

const articles = ref([])
const selectedIds = ref([])
const total = ref(0)
const pageNum = ref(1)
const tablePageSize = ref(12)
const keyword = ref('')

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '-'
}

async function loadArticles(reset = false) {
  if (reset) pageNum.value = 1
  loading.value = true
  try {
    const data = await getArticlesApi({
      pageNum: pageNum.value,
      pageSize: tablePageSize.value,
      keyword: keyword.value || undefined,
    })
    articles.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handlePageChange() {
  loadArticles(false)
}

async function handleCustomFetch() {
  fetching.value = true
  try {
    const result = await fetchNewsCustomApi({
      categories: selectedCategories.value,
      pageSize: pageSize.value,
    })
    ElMessage.success(`更新完成：抓取 ${result.fetched}，新增 ${result.inserted}，重复 ${result.duplicated}`)
    await loadArticles(true)
  } catch (e) {
    ElMessage.error(e?.message || '更新失败，请稍后重试')
  } finally {
    fetching.value = false
  }
}

function onSelectChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function deleteOne(id) {
  try {
    await ElMessageBox.confirm('确认删除这条新闻？', '提示', { type: 'warning' })
    await deleteArticleApi(id)
    ElMessage.success('已删除')
    await loadArticles()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

async function deleteSelected() {
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条新闻？`, '提示', { type: 'warning' })
    await deleteArticlesBatchApi(selectedIds.value)
    ElMessage.success('批量删除完成')
    selectedIds.value = []
    await loadArticles()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '批量删除失败')
    }
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('将清空整个新闻库，是否继续？', '危险操作', { type: 'warning' })
    await clearArticlesApi()
    ElMessage.success('新闻库已清空')
    selectedIds.value = []
    await loadArticles(true)
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '清空失败')
    }
  }
}

onMounted(() => loadArticles(true))
</script>

<style scoped>
.manage-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: calc(100vh - 140px);
}
.paper {
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 24px;
  background: rgba(250, 246, 237, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 20px 60px rgba(20, 27, 34, 0.08);
  padding: 18px;
}
.kicker { font-size: 12px; letter-spacing: .2em; color: #a33a2b; margin-bottom: 8px; }
.hero h2 { font-size: 30px; color: #18222d; margin-bottom: 8px; }
.desc { color: #64707d; }
.panel-header { font-size: 18px; font-weight: 700; margin-bottom: 12px; }
.fetch-form { display: flex; flex-wrap: wrap; gap: 8px; }
.hint { color: #7b8792; font-size: 12px; margin-top: 6px; }
.table-actions { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.left { display: flex; gap: 10px; }
.category-select { width: 380px; }
.keyword-input { width: 280px; }
.table-scroll { width: 100%; overflow-x: auto; }
.pagination { margin-top: 14px; display: flex; justify-content: center; }
@media (max-width: 980px) {
  .table-actions { flex-direction: column; }
  .left { flex-wrap: wrap; }
  .category-select,
  .keyword-input { width: 100%; }
}
</style>
