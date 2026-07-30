<template>
    <a-layout-footer class="footer">
        <div class="footer-content">
            <p class="copyright">
                <span>Copyright © 2026 lilac</span>
            </p>
        </div>

    </a-layout-footer>

</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
    background: var(--color-surface);
    text-align: center;
    padding: 24px 20px;
    margin-top: 0;
    border-top: 1px solid var(--color-border);
}

.copyright {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 14px;
}
</style>
