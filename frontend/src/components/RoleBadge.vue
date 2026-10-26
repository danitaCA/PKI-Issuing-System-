<script setup lang="ts">
import { computed } from 'vue';
import type { RoleName } from '../api/auth';

const props = defineProps<{
  role: RoleName | string;
}>();

const badgeClass = computed(() => {
  switch (props.role) {
    case 'ROLE_CA_ADMIN': return 'badge-role-admin';
    case 'ROLE_RA_OPERATOR': return 'badge-role-ra';
    case 'ROLE_SECURITY_OFFICER': return 'badge-role-sec';
    case 'ROLE_AUDITOR': return 'badge-role-auditor';
    case 'ROLE_END_ENTITY': return 'badge-role-ee';
    default: return 'badge-secondary';
  }
});

const label = computed(() => {
  switch (props.role) {
    case 'ROLE_CA_ADMIN': return 'CA Admin';
    case 'ROLE_RA_OPERATOR': return 'RA Operator';
    case 'ROLE_SECURITY_OFFICER': return 'Security Officer';
    case 'ROLE_AUDITOR': return 'Auditor';
    case 'ROLE_END_ENTITY': return 'End Entity';
    default: return props.role.replace('ROLE_', '');
  }
});
</script>

<template>
  <span :class="['badge', badgeClass]">
    {{ label }}
  </span>
</template>
