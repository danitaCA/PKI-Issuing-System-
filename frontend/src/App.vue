<script setup lang="ts">
import { onMounted, onUnmounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from './stores/auth';
import AppNavbar from './components/AppNavbar.vue';
import AppSidebar from './components/AppSidebar.vue';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const isAuthRoute = computed(() => route.path === '/login');

function onAuthExpired() {
  authStore.logout();
  router.push({ path: '/login', query: { expired: 'true' } });
}

onMounted(() => {
  window.addEventListener('auth:expired', onAuthExpired);
});

onUnmounted(() => {
  window.removeEventListener('auth:expired', onAuthExpired);
});
</script>

<template>
  <div class="app-root">
    <!-- Unauthenticated Fullscreen Layout (e.g. Login) -->
    <template v-if="isAuthRoute">
      <router-view />
    </template>

    <!-- Authenticated Shell Layout with Navbar and Sidebar -->
    <template v-else>
      <AppNavbar />
      <div class="app-shell">
        <AppSidebar />
        <main class="app-main">
          <div class="content-container">
            <router-view />
          </div>
        </main>
      </div>
    </template>
  </div>
</template>

<style scoped>
.app-root {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-shell {
  display: flex;
  flex: 1;
}

.app-main {
  flex: 1;
  background-color: var(--bg-base);
  min-height: calc(100vh - 64px);
  padding: 2rem 2.5rem;
  overflow-y: auto;
}

.content-container {
  max-width: 1280px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .app-shell {
    flex-direction: column;
  }
  .app-main {
    padding: 1rem;
  }
}
</style>
