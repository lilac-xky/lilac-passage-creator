<template>
    <a-layout class="basic-layout">
        <!-- 顶部导航栏 -->
        <GlobalHeader />
        <!-- 主要内容区域 -->
        <a-layout-content class="main-content">
            <router-view />
        </a-layout-content>

        <!-- 底部版权信息 -->
        <GlobalFooter />
    </a-layout>

</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<style scoped>
.basic-layout {
    min-height: 100vh;
    background: var(--color-page);
}

.main-content {
    width: 100%;
    padding: 0;
    flex: 1;
    background: var(--color-page);
    margin: 0;
}
</style>
