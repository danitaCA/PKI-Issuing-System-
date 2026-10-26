<script setup lang="ts">
import type { ProblemDetail } from '../api/auth';

defineProps<{
  problem?: ProblemDetail | string | null;
}>();

defineEmits<{
  (e: 'dismiss'): void;
}>();
</script>

<template>
  <div v-if="problem" class="problem-alert" role="alert">
    <div class="alert-header">
      <div class="alert-title">
        <svg class="alert-icon" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
        </svg>
        <span>{{ typeof problem === 'string' ? problem : (problem.title || 'Error') }}</span>
      </div>
      <button type="button" class="dismiss-btn" @click="$emit('dismiss')">&times;</button>
    </div>

    <div v-if="typeof problem !== 'string'" class="alert-body">
      <p class="alert-detail">{{ problem.detail }}</p>
      <div v-if="problem.errorCode" class="alert-code">
        Code: <code>{{ problem.errorCode }}</code>
      </div>

      <ul v-if="problem.invalidParams && problem.invalidParams.length" class="invalid-params">
        <li v-for="(param, index) in problem.invalidParams" :key="index">
          <strong>{{ param.field }}:</strong> {{ param.reason }}
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.problem-alert {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.35);
  border-radius: var(--radius-md);
  padding: 1rem;
  margin-bottom: 1.25rem;
  color: #fca5a5;
  animation: fadeIn 0.2s ease-in;
}

.alert-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.alert-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  font-size: 0.875rem;
  color: #fee2e2;
}

.alert-icon {
  width: 1.25rem;
  height: 1.25rem;
  color: var(--danger);
}

.dismiss-btn {
  background: transparent;
  border: none;
  color: #fca5a5;
  font-size: 1.25rem;
  cursor: pointer;
  line-height: 1;
}

.alert-body {
  margin-top: 0.5rem;
  font-size: 0.8125rem;
}

.alert-detail {
  margin-bottom: 0.25rem;
}

.alert-code {
  font-size: 0.75rem;
  color: #f87171;
}

.alert-code code {
  background: rgba(0, 0, 0, 0.3);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-sm);
}

.invalid-params {
  margin-top: 0.5rem;
  padding-left: 1.25rem;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
