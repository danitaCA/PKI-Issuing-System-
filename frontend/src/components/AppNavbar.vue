<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import RoleBadge from './RoleBadge.vue';

const router = useRouter();
const authStore = useAuthStore();
const isDark = ref(true);

onMounted(() => {
  const savedTheme = localStorage.getItem('pki_theme') || 'dark';
  isDark.value = savedTheme === 'dark';
  document.documentElement.setAttribute('data-theme', savedTheme);
});

function toggleTheme() {
  isDark.value = !isDark.value;
  const theme = isDark.value ? 'dark' : 'light';
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem('pki_theme', theme);
}

async function handleLogout() {
  await authStore.logout();
  router.push('/login');
}
</script>

<template>
  <header class="navbar">
    <div class="nav-brand">
      <router-link to="/dashboard" class="brand-link">
        <div class="brand-icon">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/>
          </svg>
        </div>
        <div class="brand-text">
          <span class="brand-title">PKI Issuing CA</span>
          <span class="brand-subtitle">Phase 2 Security Baseline</span>
        </div>
      </router-link>
      <span class="env-tag">Dev</span>
    </div>

    <div v-if="authStore.isAuthenticated" class="nav-user-actions">
      <div class="user-info">
        <div class="user-name-line">
          <span class="user-fullname">{{ authStore.user?.fullName }}</span>
          <RoleBadge v-if="authStore.primaryRole !== 'GUEST'" :role="authStore.primaryRole" />
        </div>
        <span class="user-handle">@{{ authStore.user?.username }}</span>
      </div>

      <button class="theme-toggle" @click="toggleTheme" :title="isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'">
        <svg v-if="isDark" viewBox="0 0 20 20" fill="currentColor" class="theme-icon">
          <path fill-rule="evenodd" d="M10 2a1 1 0 011 1v1a1 1 0 11-2 0V3a1 1 0 011-1zm4 8a4 4 0 11-8 0 4 4 0 018 0zm-.464 4.95l.707.707a1 1 0 001.414-1.414l-.707-.707a1 1 0 00-1.414 1.414zm2.12-10.607a1 1 0 010 1.414l-.706.707a1 1 0 11-1.414-1.414l.707-.707a1 1 0 011.414 0zM17 11a1 1 0 100-2h-1a1 1 0 100 2h1zm-7 4a1 1 0 011 1v1a1 1 0 11-2 0v-1a1 1 0 011-1zM5.05 6.464A1 1 0 106.465 5.05l-.708-.707a1 1 0 00-1.414 1.414l.707.707zm1.414 8.486l-.707.707a1 1 0 01-1.414-1.414l.707-.707a1 1 0 011.414 1.414zM4 11a1 1 0 100-2H3a1 1 0 000 2h1z" clip-rule="evenodd" />
        </svg>
        <svg v-else viewBox="0 0 20 20" fill="currentColor" class="theme-icon">
          <path d="M17.293 13.293A8 8 0 016.707 2.707a8.001 8.001 0 1010.586 10.586z" />
        </svg>
      </button>

      <button class="btn btn-secondary btn-sm" @click="handleLogout">
        <svg viewBox="0 0 20 20" fill="currentColor" class="btn-icon">
          <path fill-rule="evenodd" d="M3 3a1 1 0 00-1 1v12a1 1 0 102 0V4a1 1 0 00-1-1zm10.293 9.293a1 1 0 001.414 1.414l3-3a1 1 0 000-1.414l-3-3a1 1 0 10-1.414 1.414L14.586 9H7a1 1 0 100 2h7.586l-1.293 1.293z" clip-rule="evenodd" />
        </svg>
        <span>Logout</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  height: 64px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: blur(12px);
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
}

.brand-icon {
  width: 34px;
  height: 34px;
  background: var(--accent-gradient);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.4);
}

.brand-icon svg {
  width: 20px;
  height: 20px;
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-title {
  font-weight: 700;
  font-size: 1rem;
  color: var(--text-primary);
  line-height: 1.2;
}

.brand-subtitle {
  font-size: 0.7rem;
  color: var(--text-muted);
}

.env-tag {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  background: rgba(59, 130, 246, 0.15);
  color: var(--primary);
  border: 1px solid rgba(59, 130, 246, 0.3);
  padding: 0.15rem 0.5rem;
  border-radius: var(--radius-full);
}

.nav-user-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.user-name-line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.user-fullname {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
}

.user-handle {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.theme-toggle {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-color);
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.theme-toggle:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-primary);
}

.theme-icon, .btn-icon {
  width: 16px;
  height: 16px;
}
</style>
