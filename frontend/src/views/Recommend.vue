<template>
  <div class="recommend-page">
    <section class="profile-panel">
      <div class="profile-head">
        <div>
          <p class="kicker">PROFILE ENGINE</p>
          <h2>用户画像与 i+1 推荐</h2>
          <p class="desc">基于阅读时长、练习准确率、查词频次和兴趣偏好，动态生成可理解输入推荐。</p>
        </div>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="refreshAll">刷新推荐</el-button>
      </div>

      <div class="metrics" v-if="profile">
        <div class="metric-item">
          <span>能力分</span>
          <strong>{{ profile.abilityScore }}</strong>
        </div>
        <div class="metric-item">
          <span>目标难度</span>
          <strong>{{ profile.targetDifficulty }}</strong>
        </div>
        <div class="metric-item">
          <span>练习正确率</span>
          <strong>{{ profile.exerciseAccuracy }}%</strong>
        </div>
        <div class="metric-item">
          <span>平均阅读时长</span>
          <strong>{{ profile.avgReadDurationSec }}s</strong>
        </div>
        <div class="metric-item">
          <span>主动查词</span>
          <strong>{{ profile.lookupCount }}</strong>
        </div>
        <div class="metric-item">
          <span>14天活跃</span>
          <strong>{{ profile.activeDays14 }}天</strong>
        </div>
      </div>

      <div class="interest-editor">
        <el-select
          v-model="interestValues"
          multiple
          filterable
          allow-create
          default-first-option
          collapse-tags
          collapse-tags-tooltip
          placeholder="输入并回车添加兴趣偏好，如 technology, ai, finance"
          style="width: 100%"
        >
          <el-option v-for="item in defaultInterests" :key="item" :label="item" :value="item" />
        </el-select>
        <el-button :loading="savingInterests" @click="saveInterests">保存兴趣偏好</el-button>
      </div>
    </section>

    <section class="recommend-list" v-loading="loading">
      <div class="list-head">
        <div>
          <p class="kicker">SMART FEED</p>
          <h3>智能推荐文章</h3>
        </div>
        <el-tag type="success">{{ recommendations.length }} 篇</el-tag>
      </div>

      <div class="cards">
        <article class="rec-card" v-for="item in recommendations" :key="item.id" @click="goArticle(item.id)">
          <div class="card-top">
            <span class="cat">{{ item.category || 'general' }}</span>
            <el-tag size="small" type="warning">匹配度 {{ item.score }}</el-tag>
          </div>
          <h4>{{ item.title }}</h4>
          <p>{{ item.summary || '暂无摘要，点击进入阅读完整内容。' }}</p>
          <div class="reasons">
            <el-tag v-for="reason in item.reasonTags || []" :key="reason" size="small" effect="plain">{{ reason }}</el-tag>
          </div>
          <div class="feedback-actions">
            <el-button
              size="small"
              type="success"
              plain
              :loading="feedbackLoadingIds.includes(item.id)"
              @click.stop="feedbackArticle(item, 'LIKE')"
            >
              感兴趣
            </el-button>
            <el-button
              size="small"
              type="info"
              plain
              :loading="feedbackLoadingIds.includes(item.id)"
              @click.stop="feedbackArticle(item, 'DISLIKE')"
            >
              不感兴趣
            </el-button>
          </div>
          <div class="foot">
            <span>{{ item.source || 'News Source' }}</span>
            <span>{{ formatDate(item.publishedAt) }}</span>
          </div>
        </article>
      </div>

      <el-empty v-if="!loading && recommendations.length === 0" description="暂无推荐文章，请先到新闻管理抓取新闻" />
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getRecommendationProfileApi,
  getRecommendedArticlesApi,
  submitRecommendationFeedbackApi,
  updateRecommendationInterestsApi,
} from '@/api/recommendation'

const router = useRouter()
const loading = ref(false)
const savingInterests = ref(false)
const profile = ref(null)
const recommendations = ref([])
const interestValues = ref([])
const feedbackLoadingIds = ref([])

const defaultInterests = [
  'technology',
  'science',
  'health',
  'business',
  'sports',
  'ai',
  'economy',
  'startup',
  'education',
]

async function loadProfile() {
  const data = await getRecommendationProfileApi()
  profile.value = data
  interestValues.value = Array.isArray(data?.interests) ? [...data.interests] : []
}

async function loadRecommendations() {
  recommendations.value = await getRecommendedArticlesApi({ size: 12 })
}

async function refreshAll() {
  loading.value = true
  try {
    await Promise.all([loadProfile(), loadRecommendations()])
  } finally {
    loading.value = false
  }
}

async function saveInterests() {
  savingInterests.value = true
  try {
    const normalized = [...new Set((interestValues.value || []).map(v => String(v).trim().toLowerCase()).filter(Boolean))]
    await updateRecommendationInterestsApi(normalized)
    ElMessage.success('兴趣偏好已更新')
    await refreshAll()
  } finally {
    savingInterests.value = false
  }
}

async function feedbackArticle(item, feedbackType) {
  if (!item?.id) return
  if (feedbackLoadingIds.value.includes(item.id)) return
  feedbackLoadingIds.value.push(item.id)
  try {
    await submitRecommendationFeedbackApi(item.id, feedbackType)
    if (feedbackType === 'DISLIKE') {
      recommendations.value = recommendations.value.filter(v => v.id !== item.id)
      ElMessage.success('已标记为不感兴趣，后续会减少推荐')
    } else {
      ElMessage.success('已标记为感兴趣，后续会优先推荐同类内容')
    }
    await loadProfile()
  } finally {
    feedbackLoadingIds.value = feedbackLoadingIds.value.filter(id => id !== item.id)
  }
}

function goArticle(id) {
  router.push(`/article/${id}`)
}

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : ''
}

onMounted(refreshAll)
</script>

<style scoped>
.recommend-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.profile-panel,
.recommend-list {
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 24px;
  background: rgba(250, 246, 237, 0.92);
  backdrop-filter: blur(10px);
  box-shadow: 0 20px 60px rgba(20, 27, 34, 0.08);
  padding: 24px;
}
.profile-head,
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.kicker {
  font-size: 12px;
  letter-spacing: 0.2em;
  color: #a33a2b;
}
h2,
h3,
h4 {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', Georgia, serif;
  color: #18222d;
}
.desc {
  margin-top: 8px;
  color: #5f6b77;
}
.metrics {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}
.metric-item {
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(24, 34, 45, 0.08);
  border-radius: 14px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-item span {
  font-size: 12px;
  color: #6b7681;
}
.metric-item strong {
  font-size: 21px;
}
.interest-editor {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}
.cards {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}
.rec-card {
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 18px;
  background: linear-gradient(180deg, #fffdf8, #f8f1e4);
  padding: 16px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.rec-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 14px 34px rgba(17, 25, 33, 0.12);
}
.card-top,
.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cat {
  text-transform: capitalize;
  color: #7b4b38;
  font-size: 12px;
}
.rec-card h4 {
  margin: 10px 0 8px;
  font-size: 18px;
  line-height: 1.4;
}
.rec-card p {
  color: #586472;
  font-size: 13px;
  line-height: 1.7;
  min-height: 64px;
}
.reasons {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.feedback-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.foot {
  margin-top: 10px;
  font-size: 12px;
  color: #7e8894;
}
@media (max-width: 768px) {
  .profile-head,
  .list-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .profile-panel,
  .recommend-list {
    padding: 16px;
    border-radius: 16px;
  }

  .interest-editor {
    grid-template-columns: 1fr;
  }

  .feedback-actions {
    flex-wrap: wrap;
  }
}
</style>
