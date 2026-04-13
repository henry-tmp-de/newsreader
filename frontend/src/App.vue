<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

:root {
  color-scheme: light;
}

body {
  font-family: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background:
    radial-gradient(circle at top left, rgba(163, 58, 43, 0.1), transparent 24%),
    radial-gradient(circle at top right, rgba(24, 34, 45, 0.08), transparent 26%),
    linear-gradient(180deg, #f6f0e6, #edf2f6 46%, #f7f3ea 100%);
  color: #303133;
  min-height: 100vh;
}

#app {
  min-height: 100vh;
}
</style>
