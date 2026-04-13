<template>
  <el-card
    shadow="hover"
    class="article-card"
    @click="$emit('click')"
  >
    <div class="card-top">
      <span class="source-chip">{{ article.source || 'Global Desk' }}</span>
      <div class="top-tags">
        <el-tag size="small" class="category-tag">{{ article.category }}</el-tag>
        <el-tag :type="difficultyType" size="small">{{ article.difficulty }}</el-tag>
      </div>
    </div>

    <h3 class="title">{{ article.title }}</h3>

    <p class="summary" v-if="article.summary">{{ article.summary }}</p>
    <p class="summary placeholder" v-else>{{ article.content?.substring(0, 120) }}...</p>

    <div class="keywords" v-if="article.keywords">
      <el-tag
        v-for="kw in keywords"
        :key="kw"
        size="small"
        type="info"
        effect="plain"
        class="kw-tag"
      >
        {{ kw }}
      </el-tag>
    </div>

    <div class="card-footer">
      <span class="source">阅读卡片</span>
      <span class="date">{{ formatDate(article.publishedAt) }}</span>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ article: { type: Object, required: true } })
defineEmits(['click'])

const difficultyType = computed(() => {
  const map = { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }
  return map[props.article.difficulty] || 'info'
})

const keywords = computed(() =>
  props.article.keywords ? props.article.keywords.split(',').slice(0, 4).map(k => k.trim()) : []
)

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : ''
}
</script>

<style scoped>
.article-card {
  border-radius: 22px;
  border: 1px solid rgba(24, 34, 45, 0.08);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(251, 247, 239, 0.96));
  cursor: pointer;
  transition: transform .22s ease, box-shadow .22s ease, border-color .22s ease;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.article-card:hover {
  transform: translateY(-4px);
  border-color: rgba(163, 58, 43, 0.2);
  box-shadow: 0 18px 38px rgba(16, 24, 32, 0.12) !important;
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 16px;
}
.top-tags {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
.source-chip {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #8c3b2e;
}
.category-tag { text-transform: capitalize; }
.title {
  font-family: 'Source Han Serif SC', 'Noto Serif SC', Georgia, serif;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
  margin-bottom: 12px;
  color: #18222d;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.summary {
  font-size: 14px;
  color: #56616d;
  line-height: 1.85;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
  margin-bottom: 16px;
}
.keywords { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 16px; }
.kw-tag { font-size: 11px; }
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #7a8691;
  font-size: 12px;
  padding-top: 14px;
  border-top: 1px solid rgba(24, 34, 45, 0.08);
}
</style>
