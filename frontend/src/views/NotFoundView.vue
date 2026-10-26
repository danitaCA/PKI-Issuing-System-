<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <div class="not-found-view">
    <div class="glass-panel not-found-card">
      <div class="status-code">404</div>
      <h2>Resource Not Found</h2>
      <p class="explanation">
        The requested URL path does not map to any recognized endpoint or resource in the PKI Issuing CA portal.
      </p>
      <button class="btn btn-primary" @click="router.push('/dashboard')">
        Back to Dashboard
      </button>
    </div>
  </div>
</template>

<style scoped>
.not-found-view {
  min-height: calc(100vh - 120px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.not-found-card {
  max-width: 480px;
  width: 100%;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 3rem 2rem;
}

.status-code {
  font-family: var(--font-mono);
  font-size: 4rem;
  font-weight: 800;
  color: var(--primary);
  line-height: 1;
}

.explanation {
  font-size: 0.875rem;
  color: var(--text-secondary);
  line-height: 1.6;
}
</style>
