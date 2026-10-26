<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import RoleBadge from '../components/RoleBadge.vue';

const router = useRouter();
const authStore = useAuthStore();
</script>

<template>
  <div class="forbidden-view">
    <div class="glass-panel forbidden-card">
      <div class="status-code">403</div>
      <div class="shield-icon">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-1 6h2v6h-2V7zm0 8h2v2h-2v-2z"/>
        </svg>
      </div>
      <h2>Access Denied (RBAC Enforced)</h2>
      <p class="explanation">
        Your authenticated persona does not have the administrative privileges required to access this resource.
      </p>

      <div class="current-role-box" v-if="authStore.user">
        <span class="role-box-label">Your Active Persona Roles:</span>
        <div class="role-badges">
          <RoleBadge v-for="r in authStore.roles" :key="r" :role="r" />
        </div>
      </div>

      <div class="action-buttons">
        <button class="btn btn-primary" @click="router.push('/dashboard')">
          Return to Dashboard
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.forbidden-view {
  min-height: calc(100vh - 120px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.forbidden-card {
  max-width: 520px;
  width: 100%;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem 2rem;
}

.status-code {
  font-family: var(--font-mono);
  font-size: 3.5rem;
  font-weight: 800;
  color: var(--danger);
  line-height: 1;
  letter-spacing: -0.05em;
  opacity: 0.85;
}

.shield-icon {
  width: 64px;
  height: 64px;
  color: var(--danger);
  margin: 1rem 0;
}

.forbidden-card h2 {
  font-size: 1.5rem;
  margin-bottom: 0.5rem;
}

.explanation {
  font-size: 0.875rem;
  color: var(--text-secondary);
  line-height: 1.6;
}

.current-role-box {
  margin: 1.5rem 0;
  padding: 1rem;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  align-items: center;
}

.role-box-label {
  font-size: 0.75rem;
  color: var(--text-muted);
  text-transform: uppercase;
  font-weight: 600;
}

.role-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  justify-content: center;
}

.action-buttons {
  margin-top: 1rem;
}
</style>
