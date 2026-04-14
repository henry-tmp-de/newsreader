<template>
  <div class="exercise-page">
    <el-card shadow="never" class="header-card paper">
      <el-button :icon="ArrowLeft" text @click="router.back()">返回文章</el-button>
      <p class="kicker">PRACTICE MODE</p>
      <h2 class="title">练习测验</h2>
      <p class="subtitle">基于所读文章，AI 为你生成个性化练习题</p>
    </el-card>

    <!-- 加载/生成状态 -->
    <div v-if="loading" class="center-loading">
      <el-skeleton :rows="6" animated />
    </div>

    <el-card v-if="generating" shadow="never" class="paper generating-card">
      <div class="generating-head">
        <el-icon class="rotating"><Loading /></el-icon>
        <div>
          <div class="generating-title">AI 正在生成练习题</div>
          <div class="generating-hint">{{ generationHint }}</div>
        </div>
      </div>
      <el-progress :percentage="generationProgress" :stroke-width="16" striped striped-flow />
      <div class="progress-meta">
        <span>预计 10-25 秒，网络波动时可能更久</span>
        <span>{{ generationProgress }}%</span>
      </div>
      <div class="dot-wave" aria-hidden="true">
        <span></span><span></span><span></span>
      </div>
    </el-card>

    <!-- 无题目时生成 -->
    <el-card v-else-if="exercises.length === 0" shadow="never" class="empty-card paper">
      <el-empty description="暂无练习题">
        <el-button type="primary" :loading="generating" @click="generateExercises">
          <el-icon><Magic /></el-icon>
          AI 生成练习题
        </el-button>
      </el-empty>
    </el-card>

    <!-- 练习题列表 -->
    <template v-else>
      <el-progress
        :percentage="progressPercent"
        :format="(p) => `${answeredCount}/${exercises.length}`"
        class="progress"
      />

      <el-card
        v-for="(ex, index) in exercises"
        :key="ex.id"
        shadow="never"
        class="exercise-card paper"
        :class="{ answered: results[ex.id] !== undefined }"
      >
        <div class="question-header">
          <el-tag size="small" :type="typeTagType(ex.type)">{{ ex.type }}</el-tag>
          <span class="q-num">第 {{ index + 1 }} 题</span>
        </div>
        <p class="question">{{ ex.question }}</p>

        <el-radio-group
          v-model="answers[ex.id]"
          :disabled="results[ex.id] !== undefined"
          class="options"
        >
          <el-radio
            v-for="opt in parseOptions(ex.options)"
            :key="opt"
            :label="opt[0]"
            class="option"
            :class="getOptionClass(ex, opt[0])"
          >
            {{ opt }}
          </el-radio>
        </el-radio-group>

        <div class="submit-row" v-if="results[ex.id] === undefined">
          <el-button
            type="primary"
            size="small"
            :disabled="!answers[ex.id]"
            @click="submitAnswer(ex)"
          >
            提交答案
          </el-button>
        </div>

        <el-alert
          v-if="results[ex.id]"
          :type="results[ex.id].correct ? 'success' : 'error'"
          :title="results[ex.id].correct ? '答对了！' : '答错了'"
          :description="results[ex.id].feedback"
          show-icon
          class="feedback-alert"
        />
      </el-card>

      <!-- 重新生成 -->
      <el-card shadow="never" class="regen-card paper">
        <el-button :loading="generating" @click="generateExercises">
          <el-icon><Refresh /></el-icon>
          重新生成练习题
        </el-button>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExercisesApi, generateExercisesApi, submitAnswerApi } from '@/api/exercise'
import { ArrowLeft, Loading, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const exercises = ref([])
const answers = ref({})
const results = ref({})
const loading = ref(true)
const generating = ref(false)
const generationProgress = ref(0)
const generationHint = ref('准备题目结构...')
let generationTimer = null

const generationStages = [
  { p: 10, text: '准备题目结构...' },
  { p: 28, text: '分析文章重点...' },
  { p: 52, text: '生成题干与选项...' },
  { p: 76, text: '校验答案与解析...' },
  { p: 90, text: '整理最终结果...' },
]

const answeredCount = computed(() => Object.keys(results.value).length)
const progressPercent = computed(() =>
  exercises.value.length > 0 ? Math.round(answeredCount.value / exercises.value.length * 100) : 0
)

function parseOptions(optStr) {
  try {
    return JSON.parse(optStr)
  } catch {
    return []
  }
}

function typeTagType(type) {
  const map = { VOCABULARY: 'warning', COMPREHENSION: '', GRAMMAR: 'success', TRANSLATION: 'info' }
  return map[type] || ''
}

function getOptionClass(ex, letter) {
  const result = results.value[ex.id]
  if (!result) return ''
  if (letter === ex.correctAnswer) return 'correct-option'
  if (letter === answers.value[ex.id] && !result.correct) return 'wrong-option'
  return ''
}

async function loadExercises() {
  loading.value = true
  try {
    exercises.value = await getExercisesApi(route.params.articleId)
  } finally {
    loading.value = false
  }
}

async function generateExercises() {
  generating.value = true
  answers.value = {}
  results.value = {}
  startGenerationProgress()
  try {
    const generated = await generateExercisesApi(route.params.articleId)
    exercises.value = generated
    finishGenerationProgress(true)
    ElMessage.success(`练习题生成成功，共 ${generated.length} 题`)
  } catch (e) {
    finishGenerationProgress(false)
    ElMessage.error('生成失败，请重试')
  } finally {
    generating.value = false
  }
}

function startGenerationProgress() {
  generationProgress.value = 6
  generationHint.value = generationStages[0].text
  const startedAt = Date.now()
  if (generationTimer) clearInterval(generationTimer)
  generationTimer = setInterval(() => {
    const elapsed = Date.now() - startedAt
    const next = elapsed < 5000
      ? generationProgress.value + 2
      : elapsed < 12000
        ? generationProgress.value + 1
        : generationProgress.value + 0.4
    generationProgress.value = Math.min(92, Math.round(next))

    const stage = [...generationStages].reverse().find(s => generationProgress.value >= s.p) || generationStages[0]
    generationHint.value = stage.text
  }, 360)
}

function finishGenerationProgress(success) {
  if (generationTimer) {
    clearInterval(generationTimer)
    generationTimer = null
  }
  generationProgress.value = success ? 100 : 0
  generationHint.value = success ? '生成完成，正在加载题目...' : '生成中断，请重试'
}

async function submitAnswer(ex) {
  const answer = answers.value[ex.id]
  if (!answer) return
  try {
    const feedback = await submitAnswerApi({ exerciseId: ex.id, answer })
    const correct = ex.correctAnswer.toUpperCase() === answer.toUpperCase()
    results.value = { ...results.value, [ex.id]: { correct, feedback } }
  } catch {
    ElMessage.error('提交失败，请重试')
  }
}

onMounted(loadExercises)

onUnmounted(() => {
  if (generationTimer) clearInterval(generationTimer)
})
</script>

<style scoped>
.exercise-page { display: flex; flex-direction: column; gap: 16px; max-width: 800px; margin: 0 auto; }
.paper {
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 20px;
  background: rgba(250, 246, 237, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 20px 60px rgba(20, 27, 34, 0.08);
}
.kicker { font-size: 12px; letter-spacing: .2em; color: #a33a2b; margin-top: 8px; }
.header-card { border-radius: 20px; }
.title { font-size: 22px; font-weight: 700; margin: 8px 0 4px; }
.subtitle { color: #909399; font-size: 14px; }
.progress { margin: 4px 0; }
.exercise-card { border-radius: 20px; transition: border-color .3s; }
.exercise-card.answered { border-left: 4px solid #67c23a; }
.question-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.q-num { color: #909399; font-size: 13px; }
.question { font-size: 16px; font-weight: 500; margin-bottom: 16px; line-height: 1.6; }
.options { display: flex; flex-direction: column; gap: 10px; }
.option { margin-left: 0 !important; padding: 10px 14px; border-radius: 8px; border: 1px solid #e4e7ed; width: 100%; }
.option:hover { border-color: #409EFF; }
.correct-option { background: #f0f9eb; border-color: #67c23a !important; }
.wrong-option { background: #fef0f0; border-color: #f56c6c !important; }
.submit-row { margin-top: 16px; }
.feedback-alert { margin-top: 16px; }
.empty-card, .regen-card { text-align: center; padding: 20px; }
.center-loading { padding: 20px 0; }
.generating-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
}
.generating-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.generating-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2d3d;
}
.generating-hint {
  margin-top: 2px;
  font-size: 13px;
  color: #7a8694;
}
.progress-meta {
  display: flex;
  justify-content: space-between;
  color: #8a95a1;
  font-size: 12px;
}
.rotating {
  animation: rotating 1s linear infinite;
  color: #a33a2b;
}
.dot-wave {
  display: flex;
  gap: 6px;
}
.dot-wave span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(163, 58, 43, 0.7);
  animation: wave 1.1s infinite ease-in-out;
}
.dot-wave span:nth-child(2) { animation-delay: 0.14s; }
.dot-wave span:nth-child(3) { animation-delay: 0.28s; }

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes wave {
  0%, 80%, 100% { transform: translateY(0); opacity: .45; }
  40% { transform: translateY(-4px); opacity: 1; }
}

@media (max-width: 768px) {
  .exercise-page {
    max-width: 100%;
  }

  .title {
    font-size: 20px;
  }

  .question {
    font-size: 15px;
  }

  .option {
    padding: 8px 10px;
  }
}
</style>
