<template>
  <div class="home-page">
    <section class="hero-panel">
      <div class="hero-copy">
        <p class="eyebrow">NEWSREADER DAILY</p>
        <h1>把英语新闻阅读，整理成一份更适合持续输入的晨报。</h1>
        <p class="hero-desc">
          聚合国际资讯、按难度分层，并接入 DeepSeek 做摘要、关键词和练习增强，让阅读、理解与复盘连成一个流程。
        </p>

        <div class="hero-actions">
          <el-button type="primary" size="large" :icon="Refresh" @click="fetchNews" :loading="fetching">
            更新今日新闻
          </el-button>
          <el-button size="large" :icon="Setting" @click="openApiKeyDialog">
            配置 Key
          </el-button>
        </div>
      </div>

      <div class="hero-aside">
        <div class="metric-card">
          <span class="metric-label">当前文章</span>
          <strong>{{ total }}</strong>
          <span class="metric-note">已沉淀到阅读库</span>
        </div>
        <div class="metric-card accent">
          <span class="metric-label">本页可读</span>
          <strong>{{ articles.length }}</strong>
          <span class="metric-note">按当前筛选条件展示</span>
        </div>
        <div class="metric-card compact">
          <span class="metric-label">服务状态</span>
          <div class="service-status-list">
            <span :class="['status-pill', hasNewsApiKey ? 'ready' : 'pending']">NewsAPI {{ hasNewsApiKey ? '已就绪' : '待配置' }}</span>
            <span :class="['status-pill', hasDeepseekApiKey ? 'ready' : 'pending']">DeepSeek {{ hasDeepseekApiKey ? '已就绪' : '可选' }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="control-panel">
      <div class="control-header">
        <div>
          <p class="section-kicker">编辑台</p>
          <h2>筛选你今天想读的新闻切片</h2>
        </div>
        <el-button text @click="resetFilters">清空筛选</el-button>
      </div>

      <div class="control-grid">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索标题、主题或来源"
          clearable
          :prefix-icon="Search"
          @keyup.enter="loadArticles(true)"
          @clear="loadArticles(true)"
        />

        <el-select v-model="filterCategory" placeholder="新闻栏目" clearable @change="loadArticles(true)">
          <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>

        <el-select v-model="filterDifficulty" placeholder="阅读难度" clearable @change="loadArticles(true)">
          <el-option label="简单 (Easy)" value="EASY" />
          <el-option label="中等 (Medium)" value="MEDIUM" />
          <el-option label="困难 (Hard)" value="HARD" />
        </el-select>
      </div>

      <div class="quick-filters">
        <button
          v-for="c in categories"
          :key="c.value"
          type="button"
          :class="['quick-filter', { active: filterCategory === c.value }]"
          @click="applyCategory(c.value)"
        >
          {{ c.label }}
        </button>
      </div>

      <div class="control-footer">
        <span class="status-line">NewsAPI：{{ hasNewsApiKey ? '已配置' : '未配置' }}</span>
        <span class="status-line">DeepSeek：{{ hasDeepseekApiKey ? '已配置' : '未配置（可选）' }}</span>
        <span class="status-line">已启用筛选：{{ activeFilterCount }}</span>
      </div>
    </section>

    <section class="news-section">
      <div class="news-header">
        <div>
          <p class="section-kicker">精选版面</p>
          <h2>按新闻阅读逻辑组织的文章流</h2>
        </div>
        <span class="news-summary">共 {{ total }} 篇，当前显示 {{ articles.length }} 篇</span>
      </div>

      <div v-loading="loading" class="articles-grid">
        <ArticleCard
          v-for="article in articles"
          :key="article.id"
          :article="article"
          @click="goToArticle(article.id)"
        />
        <el-empty v-if="!loading && articles.length === 0" description="暂无文章，请先拉取新闻或调整筛选条件" />
      </div>
    </section>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      class="pagination"
      @current-change="loadArticles"
    />

    <el-dialog v-model="apiKeyDialogVisible" title="配置 NewsAPI 与 DeepSeek Key" width="560px">
      <el-form label-position="top" :model="apiKeysForm">
        <el-form-item label="NewsAPI Key（必填，用于拉取新闻）">
          <el-input v-model="apiKeysForm.newsApiKey" placeholder="请输入 NewsAPI Key" clearable show-password />
        </el-form-item>
        <el-form-item label="DeepSeek API Key（可选，用于摘要、关键词、难度增强与练习生成）">
          <el-input v-model="apiKeysForm.deepseekApiKey" placeholder="请输入 DeepSeek API Key" clearable show-password />
        </el-form-item>
        <p class="dialog-tip">DeepSeek 使用 OpenAI 兼容接口，但这里已经改为直接按 DeepSeek 配置与命名保存。</p>
      </el-form>
      <template #footer>
        <el-button @click="apiKeyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingApiKeys" @click="saveApiKeys">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getArticlesApi, fetchNewsApi } from '@/api/news'
import { getApiKeyStatusApi, saveApiKeysApi } from '@/api/system'
import ArticleCard from '@/components/ArticleCard.vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Setting } from '@element-plus/icons-vue'

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
const hasNewsApiKey = ref(false)
const hasDeepseekApiKey = ref(false)
const apiKeyDialogVisible = ref(false)
const savingApiKeys = ref(false)
const apiKeysForm = reactive({
  newsApiKey: '',
  deepseekApiKey: '',
})

const activeFilterCount = computed(() => [searchKeyword.value, filterCategory.value, filterDifficulty.value].filter(Boolean).length)

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
  if (!hasNewsApiKey.value) {
    ElMessage.warning('请先配置 NewsAPI Key')
    apiKeyDialogVisible.value = true
    return
  }

  fetching.value = true
  try {
    const result = await fetchNewsApi()
    ElMessage.success(
      `拉取完成：抓取 ${result.fetched} 条，新增 ${result.inserted} 条，重复 ${result.duplicated} 条，内容不足跳过 ${result.skippedNoContent} 条`
    )
    setTimeout(() => loadArticles(true), 3000)
  } finally {
    fetching.value = false
  }
}

async function loadApiKeyStatus() {
  const status = await getApiKeyStatusApi()
  hasNewsApiKey.value = !!status.hasNewsApiKey
  hasDeepseekApiKey.value = !!status.hasDeepseekApiKey
}

function applyCategory(category) {
  filterCategory.value = filterCategory.value === category ? '' : category
  loadArticles(true)
}

function resetFilters() {
  searchKeyword.value = ''
  filterCategory.value = ''
  filterDifficulty.value = ''
  loadArticles(true)
}

function openApiKeyDialog() {
  apiKeyDialogVisible.value = true
}

async function saveApiKeys() {
  if (!apiKeysForm.newsApiKey && !apiKeysForm.deepseekApiKey) {
    ElMessage.warning('请至少填写一个 Key')
    return
  }
  savingApiKeys.value = true
  try {
    await saveApiKeysApi(apiKeysForm)
    ElMessage.success('API Key 保存成功')
    apiKeyDialogVisible.value = false
    apiKeysForm.newsApiKey = ''
    apiKeysForm.deepseekApiKey = ''
    await loadApiKeyStatus()
  } finally {
    savingApiKeys.value = false
  }
}

function goToArticle(id) {
  router.push(`/article/${id}`)
}

onMounted(async () => {
  await loadApiKeyStatus()
  await loadArticles()
})
</script>

<style scoped>
.home-page {
  --paper: rgba(250, 246, 237, 0.92);
  --paper-strong: #fffdf7;
  --ink: #18222d;
  --muted: #64707d;
  --line: rgba(24, 34, 45, 0.1);
  --accent: #a33a2b;
  --accent-soft: rgba(163, 58, 43, 0.12);
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero-panel,
.control-panel,
.news-section {
  border: 1px solid var(--line);
  border-radius: 28px;
  background: var(--paper);
  backdrop-filter: blur(12px);
  box-shadow: 0 24px 80px rgba(20, 27, 34, 0.08);
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
  gap: 20px;
  padding: 32px;
  background:
    radial-gradient(circle at top right, rgba(163, 58, 43, 0.16), transparent 28%),
    linear-gradient(135deg, rgba(255, 253, 247, 0.98), rgba(246, 240, 228, 0.9));
}

.eyebrow,
.section-kicker {
  font-size: 12px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--accent);
  margin-bottom: 14px;
}

.hero-copy h1,
.control-header h2,
.news-header h2 {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', Georgia, serif;
  color: var(--ink);
}

.hero-copy h1 {
  font-size: clamp(32px, 4.2vw, 52px);
  line-height: 1.08;
  max-width: 12ch;
}

.hero-desc {
  margin-top: 18px;
  max-width: 680px;
  font-size: 16px;
  line-height: 1.9;
  color: var(--muted);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}

.hero-aside {
  display: grid;
  gap: 14px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 22px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.74);
  border: 1px solid rgba(24, 34, 45, 0.08);
}

.metric-card.accent {
  background: linear-gradient(160deg, #1f2d3a, #314659);
  color: #f8f4eb;
}

.metric-label {
  font-size: 13px;
  color: inherit;
  opacity: 0.72;
}

.metric-card strong {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', Georgia, serif;
  font-size: 42px;
  line-height: 1;
}

.metric-note {
  font-size: 13px;
  color: inherit;
  opacity: 0.72;
}

.service-status-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-pill.ready {
  color: #15543d;
  background: rgba(53, 153, 114, 0.14);
}

.status-pill.pending {
  color: #8d4e1f;
  background: rgba(214, 152, 80, 0.16);
}

.control-panel,
.news-section {
  padding: 26px;
}

.control-header,
.news-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.control-header h2,
.news-header h2 {
  font-size: 28px;
  line-height: 1.2;
}

.control-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) repeat(2, minmax(180px, 0.8fr));
  gap: 14px;
}

.quick-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.quick-filter {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink);
  border-radius: 999px;
  padding: 10px 15px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-filter.active,
.quick-filter:hover {
  border-color: rgba(163, 58, 43, 0.3);
  background: var(--accent-soft);
  color: var(--accent);
}

.control-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--line);
}

.status-line,
.news-summary {
  font-size: 13px;
  color: var(--muted);
}

.articles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 22px;
  min-height: 220px;
}

.pagination {
  display: flex;
  justify-content: center;
}

.dialog-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #7a8691;
  line-height: 1.7;
}

@media (max-width: 1080px) {
  .hero-panel {
    grid-template-columns: 1fr;
  }

  .control-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-panel,
  .control-panel,
  .news-section {
    padding: 20px;
    border-radius: 22px;
  }

  .control-header,
  .news-header {
    flex-direction: column;
  }

  .hero-copy h1 {
    max-width: none;
  }

  .articles-grid {
    grid-template-columns: 1fr;
  }
}
</style>
