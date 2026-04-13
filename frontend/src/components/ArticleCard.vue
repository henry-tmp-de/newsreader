<template>
  <el-card
    shadow="hover"
    class="article-card"
    @click="$emit('click')"
  >
    <div class="card-top">
      <el-tag size="small" class="category-tag">{{ article.category }}</el-tag>
      <el-tag :type="difficultyType" size="small">{{ article.difficulty }}</el-tag>
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
      <span class="source">{{ article.source }}</span>
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
  border-radius: 12px;
  cursor: pointer;
  transition: transform .2s, box-shadow .2s;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.article-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,.12) !important; }
.card-top { display: flex; gap: 6px; margin-bottom: 10px; }
.category-tag { text-transform: capitalize; }
.title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  margin-bottom: 10px;
  color: #303133;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.summary {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
  margin-bottom: 12px;
}
.keywords { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 12px; }
.kw-tag { font-size: 11px; }
.card-footer { display: flex; justify-content: space-between; align-items: center; color: #909399; font-size: 12px; }
</style>
