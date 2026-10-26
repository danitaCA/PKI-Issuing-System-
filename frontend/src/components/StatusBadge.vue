<script setup lang="ts">
import { computed } from 'vue';
import type { UserStatus } from '../api/auth';

const props = defineProps<{
  status: UserStatus | string;
}>();

const badgeClass = computed(() => {
  switch (props.status) {
    case 'ACTIVE': return 'badge-active';
    case 'LOCKED': return 'badge-locked';
    case 'DISABLED': return 'badge-disabled';
    default: return 'badge-disabled';
  }
});
</script>

<template>
  <span :class="['badge', badgeClass]">
    {{ status }}
  </span>
</template>
