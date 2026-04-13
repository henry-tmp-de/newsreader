<template>
  <div class="exercise-page">
    <el-card shadow="never" class="header-card">
      <el-button :icon="ArrowLeft" text @click="router.back()">返回文章</el-button>
      <h2 class="title">练习测验</h2>
      <p class="subtitle">基于所读文章，AI 为你生成个性化练习题</p>
    </el-card>

    <!-- 加载/生成状态 -->
    <div v-if="loading" class="center-loading">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 无题目时生成 -->
    <el-card v-else-if="exercises.length === 0" shadow="never" class="empty-card">
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
        class="exercise-card"
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
      <el-card shadow="never" class="regen-card">
        <el-button :loading="generating" @click="generateExercises">
          <el-icon><Refresh /></el-icon>
          重新生成练习题
        </el-button>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExercisesApi, generateExercisesApi, submitAnswerApi } from '@/api/exercise'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const exercises = ref([])
const answers = ref({})
const results = ref({})
const loading = ref(true)
const generating = ref(false)

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
  exercises.value = []
  answers.value = {}
  results.value = {}
  try {
    exercises.value = await generateExercisesApi(route.params.articleId)
    ElMessage.success('练习题生成成功')
  } catch (e) {
    ElMessage.error('生成失败，请重试')
  } finally {
    generating.value = false
  }
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
</script>

<style scoped>
.exercise-page { display: flex; flex-direction: column; gap: 16px; max-width: 800px; margin: 0 auto; }
.header-card { border-radius: 12px; }
.title { font-size: 22px; font-weight: 700; margin: 8px 0 4px; }
.subtitle { color: #909399; font-size: 14px; }
.progress { margin: 4px 0; }
.exercise-card { border-radius: 12px; transition: border-color .3s; }
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
</style>
