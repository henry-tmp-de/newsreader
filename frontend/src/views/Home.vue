<template>
  <div class="home">
    <!-- 搜索与筛选 -->
    <el-card class="filter-card" shadow="never">
      <el-row :gutter="16" align="middle">
        <el-col :span="8">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文章标题..."
            clearable
            :prefix-icon="Search"
            @keyup.enter="loadArticles(true)"
            @clear="loadArticles(true)"
          />
        </el-col>
        <el-col :span="5">
          <el-select v-model="filterCategory" placeholder="选择分类" clearable @change="loadArticles(true)">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-select v-model="filterDifficulty" placeholder="难度筛选" clearable @change="loadArticles(true)">
            <el-option label="简单 (Easy)" value="EASY" />
            <el-option label="中等 (Medium)" value="MEDIUM" />
            <el-option label="困难 (Hard)" value="HARD" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-button type="primary" :icon="Refresh" @click="fetchNews" :loading="fetching">
            拉取最新新闻
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 文章列表 -->
    <div v-loading="loading" class="articles-grid">
      <ArticleCard
        v-for="article in articles"
        :key="article.id"
        :article="article"
        @click="goToArticle(article.id)"
      />
      <el-empty v-if="!loading && articles.length === 0" description="暂无文章，请拉取最新新闻" />
    </div>

    <!-- 分页 -->
    <el-pagination
      v-if="total > 0"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      class="pagination"
      @current-change="loadArticles"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getArticlesApi, fetchNewsApi } from '@/api/news'
import ArticleCard from '@/components/ArticleCard.vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'

const router = useRouter()
const articles = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)
const loading = ref(false)
const fetching = ref(false)
const searchKeyword = ref('')
const filterCategory = ref('')
const filterDifficulty = ref('')

const categories = [
  { label: '科技', value: 'technology' },
  { label: '科学', value: 'science' },
  { label: '健康', value: 'health' },
  { label: '商业', value: 'business' },
  { label: '体育', value: 'sports' },
]

async function loadArticles(reset = false) {
  if (reset) pageNum.value = 1
  loading.value = true
  try {
    const res = await getArticlesApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value || undefined,
      category: filterCategory.value || undefined,
      difficulty: filterDifficulty.value || undefined,
    })
    articles.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function fetchNews() {
  fetching.value = true
  try {
    await fetchNewsApi()
    ElMessage.success('正在拉取最新新闻，请稍后刷新页面')
    setTimeout(() => loadArticles(true), 3000)
  } finally {
    fetching.value = false
  }
}

function goToArticle(id) {
  router.push(`/article/${id}`)
}

onMounted(() => loadArticles())
</script>

<style scoped>
.home { display: flex; flex-direction: column; gap: 20px; }
.filter-card { border-radius: 12px; }
.articles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
  min-height: 200px;
}
.pagination { display: flex; justify-content: center; }
</style>
