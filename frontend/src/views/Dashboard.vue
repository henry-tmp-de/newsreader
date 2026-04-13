<template>
  <div class="dashboard" v-loading="loading">
    <section class="hero paper">
      <p class="kicker">LEARNING BRIEF</p>
      <h2 class="page-title">学习看板</h2>
      <p class="desc">把阅读行为、练习表现和词汇进度放在同一张仪表盘里，方便你快速复盘。</p>
    </section>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <el-card class="stat-card" shadow="never">
          <el-statistic title="已读文章" :value="stats.readArticles || 0">
            <template #suffix><span class="unit">篇</span></template>
          </el-statistic>
          <el-icon class="stat-icon" color="#409EFF" size="32"><Document /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="never">
          <el-statistic title="完成练习" :value="stats.totalExercises || 0">
            <template #suffix><span class="unit">题</span></template>
          </el-statistic>
          <el-icon class="stat-icon" color="#67C23A" size="32"><EditPen /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="never">
          <el-statistic title="答题正确率" :value="stats.accuracy || 0">
            <template #suffix>%</template>
          </el-statistic>
          <el-icon class="stat-icon" color="#E6A23C" size="32"><TrendCharts /></el-icon>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="never">
          <el-statistic title="词汇量" :value="stats.vocabularySize || 0">
            <template #suffix><span class="unit">词</span></template>
          </el-statistic>
          <el-icon class="stat-icon" color="#F56C6C" size="32"><Collection /></el-icon>
        </el-card>
      </el-col>
    </el-row>

    <!-- 待复习单词 -->
    <el-card shadow="never" class="review-card" v-if="stats.reviewWords?.length">
      <template #header>
        <div class="card-header">
          <span>今日待复习单词</span>
          <el-button text type="primary" @click="$router.push('/vocabulary')">查看全部</el-button>
        </div>
      </template>
      <div class="word-chips">
        <el-tag
          v-for="word in stats.reviewWords"
          :key="word.id"
          class="word-chip"
          :type="masteryType(word.masteryLevel)"
        >
          {{ word.word }}
          <span class="mastery">⭐ {{ word.masteryLevel }}/5</span>
        </el-tag>
      </div>
    </el-card>

    <!-- 学习建议 -->
    <el-card shadow="never" class="tips-card">
      <template #header><span>学习建议</span></template>
      <el-timeline>
        <el-timeline-item
          v-for="tip in learningTips"
          :key="tip.text"
          :type="tip.type"
          :icon="tip.icon"
        >
          {{ tip.text }}
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getStatsApi } from '@/api/learning'
import { useUserStore } from '@/stores/user'
import { Document, EditPen, TrendCharts, Collection } from '@element-plus/icons-vue'

const userStore = useUserStore()
const stats = ref({})
const loading = ref(true)

const learningTips = computed(() => {
  const tips = []
  const accuracy = stats.value.accuracy || 0
  const read = stats.value.readArticles || 0

  if (read < 3) tips.push({ text: '多读英文新闻是提升语感的最佳方式，建议每天阅读 2-3 篇', type: 'primary' })
  if (accuracy < 60) tips.push({ text: '练习正确率较低，建议先阅读文章摘要再做题', type: 'warning' })
  if (accuracy >= 80) tips.push({ text: '练习正确率很高！可以尝试阅读更难级别的文章', type: 'success' })
  if ((stats.value.vocabularySize || 0) < 20) tips.push({ text: '多使用划词查询功能，积累专业词汇', type: 'info' })
  if ((stats.value.reviewWords?.length || 0) > 0) tips.push({ text: `你有 ${stats.value.reviewWords?.length} 个单词需要复习，建议今日完成`, type: 'danger' })
  if (tips.length === 0) tips.push({ text: '保持每日阅读习惯，你的英语水平正在稳步提升！', type: 'success' })

  return tips
})

function masteryType(level) {
  if (level >= 4) return 'success'
  if (level >= 2) return 'warning'
  return 'danger'
}

onMounted(async () => {
  try {
    stats.value = await getStatsApi()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 20px; }
.paper {
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 24px;
  background: rgba(250, 246, 237, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 20px 60px rgba(20, 27, 34, 0.08);
  padding: 18px;
}
.kicker { font-size: 12px; letter-spacing: .2em; color: #a33a2b; margin-bottom: 8px; }
.page-title { font-size: 28px; font-weight: 700; margin-bottom: 2px; color: #18222d; }
.desc { color: #64707d; }
.stat-row { margin-bottom: 0; }
.stat-card {
  border-radius: 18px;
  position: relative;
  overflow: hidden;
  min-height: 100px;
  border: 1px solid rgba(24, 34, 45, 0.08);
  background: rgba(255, 253, 247, 0.95);
}
.stat-icon { position: absolute; right: 20px; top: 50%; transform: translateY(-50%); opacity: 0.15; }
.unit { font-size: 14px; color: #909399; }
.review-card, .tips-card {
  border-radius: 18px;
  border: 1px solid rgba(24, 34, 45, 0.08);
  background: rgba(255, 253, 247, 0.95);
}
.card-header { display: flex; align-items: center; justify-content: space-between; }
.word-chips { display: flex; flex-wrap: wrap; gap: 10px; }
.word-chip { cursor: pointer; font-size: 14px; padding: 6px 12px; }
.mastery { margin-left: 4px; font-size: 11px; opacity: 0.8; }
</style>
