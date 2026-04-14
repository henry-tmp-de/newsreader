<template>
  <div class="progress-page" v-loading="loading">
    <section class="paper hero">
      <p class="kicker">LEVEL ROADMAP</p>
      <h2>学习进度</h2>
      <p class="desc">基于你的阅读、练习、词句积累估算升级进度，帮助你更直观地规划下一个阶段。</p>
    </section>

    <section class="paper current-level">
      <div class="row">
        <div>
          <div class="label">当前阶段</div>
          <div class="value">{{ currentLevelLabel }}</div>
        </div>
        <div>
          <div class="label">下一阶段</div>
          <div class="value">{{ nextLevelLabel }}</div>
        </div>
      </div>
      <el-progress
        :percentage="progressPercent"
        :stroke-width="18"
        status="success"
      />
      <div class="hint">预计还需 {{ remainingPoints }} 积分，按当前节奏约 {{ etaDays }} 天可升级。</div>
    </section>

    <section class="paper metrics">
      <div class="metric-item">
        <span>阅读文章</span>
        <strong>{{ stats.readArticles || 0 }}</strong>
      </div>
      <div class="metric-item">
        <span>完成练习</span>
        <strong>{{ stats.totalExercises || 0 }}</strong>
      </div>
      <div class="metric-item">
        <span>词句积累</span>
        <strong>{{ stats.vocabularySize || 0 }}</strong>
      </div>
      <div class="metric-item">
        <span>练习正确率</span>
        <strong>{{ stats.accuracy || 0 }}%</strong>
      </div>
      <div class="metric-item accent">
        <span>综合成长积分</span>
        <strong>{{ growthScore }}</strong>
      </div>
    </section>

    <section class="paper suggestions">
      <h3>升级建议</h3>
      <ul>
        <li>每天至少精读 2 篇新闻，并在文章页向 AI 提 2 个问题。</li>
        <li>每次阅读至少划 3 个词或 1 个长句，提升词句库存。</li>
        <li>将练习正确率稳定到 80% 以上，升级速度会明显提升。</li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getStatsApi } from '@/api/learning'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const stats = ref({})
const loading = ref(true)

const levelMap = {
  BEGINNER: { label: 'BEGINNER', target: 260, next: 'INTERMEDIATE' },
  INTERMEDIATE: { label: 'INTERMEDIATE', target: 620, next: 'ADVANCED' },
  ADVANCED: { label: 'ADVANCED', target: 620, next: 'MASTERED' },
}

const growthScore = computed(() => {
  const read = stats.value.readArticles || 0
  const ex = stats.value.totalExercises || 0
  const vocab = stats.value.vocabularySize || 0
  const acc = stats.value.accuracy || 0
  return Math.round(read * 4 + ex * 2 + vocab * 1.5 + acc * 0.8)
})

const currentCfg = computed(() => levelMap[userStore.level] || levelMap.BEGINNER)
const currentLevelLabel = computed(() => currentCfg.value.label)
const nextLevelLabel = computed(() => currentCfg.value.next)

const progressPercent = computed(() => {
  if (userStore.level === 'ADVANCED') return 100
  return Math.min(100, Math.round((growthScore.value / currentCfg.value.target) * 100))
})

const remainingPoints = computed(() => {
  if (userStore.level === 'ADVANCED') return 0
  return Math.max(0, currentCfg.value.target - growthScore.value)
})

const etaDays = computed(() => {
  if (remainingPoints.value === 0) return 0
  const dailyGain = Math.max(8, Math.round((stats.value.readArticles || 0) * 0.6 + (stats.value.totalExercises || 0) * 0.25 + (stats.value.vocabularySize || 0) * 0.2) || 12)
  return Math.ceil(remainingPoints.value / dailyGain)
})

onMounted(async () => {
  try {
    if (!userStore.userInfo) {
      await userStore.fetchProfile()
    }
    stats.value = await getStatsApi()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.progress-page { display: flex; flex-direction: column; gap: 18px; }
.paper {
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 24px;
  background: rgba(250, 246, 237, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 20px 60px rgba(20, 27, 34, 0.08);
  padding: 18px;
}
.kicker { font-size: 12px; letter-spacing: .2em; color: #a33a2b; margin-bottom: 8px; }
.hero h2 { font-size: 30px; margin-bottom: 8px; color: #18222d; }
.desc { color: #64707d; }
.row { display: flex; gap: 30px; margin-bottom: 12px; }
.label { color: #64707d; font-size: 13px; }
.value { font-size: 22px; font-weight: 700; color: #18222d; }
.hint { margin-top: 10px; color: #7a5d48; }
.metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.metric-item { background: rgba(255,253,247,.85); border: 1px solid rgba(24,34,45,.08); border-radius: 12px; padding: 10px; display: flex; flex-direction: column; gap: 6px; }
.metric-item span { color: #6a7583; font-size: 13px; }
.metric-item strong { font-size: 22px; color: #1d2b39; }
.metric-item.accent { background: rgba(163,58,43,.08); }
.suggestions h3 { margin-bottom: 10px; }
.suggestions ul { margin: 0; padding-left: 20px; color: #4a5562; line-height: 1.9; }
@media (max-width: 980px) {
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 640px) {
  .row {
    flex-direction: column;
    gap: 12px;
  }

  .hero h2 {
    font-size: 24px;
  }

  .metrics {
    grid-template-columns: 1fr;
  }
}
</style>
