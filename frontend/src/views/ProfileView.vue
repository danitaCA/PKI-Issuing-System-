<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '../stores/auth';
import RoleBadge from '../components/RoleBadge.vue';
import StatusBadge from '../components/StatusBadge.vue';

const authStore = useAuthStore();

const tokenPayload = computed(() => {
  if (!authStore.accessToken) return null;
  try {
    const parts = authStore.accessToken.split('.');
    if (parts.length === 3) {
      const decoded = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(decoded);
    }
  } catch {
    return null;
  }
  return null;
});
</script>

<template>
  <div class="profile-view">
    <div class="view-header">
      <h1>My Security Identity & Credentials</h1>
      <p class="header-subtitle">
        Review your authenticated subject identity, active RBAC roles, and cryptographically verified claims.
      </p>
    </div>

    <div class="profile-grid">
      <!-- Identity Card -->
      <div class="glass-panel info-card">
        <div class="card-header">
          <span class="card-icon">👤</span>
          <h3>Subject Identity</h3>
        </div>
        <div class="info-list">
          <div class="info-item">
            <span class="item-label">Account ID</span>
            <span class="item-value font-mono">#{{ authStore.user?.id }}</span>
          </div>
          <div class="info-item">
            <span class="item-label">Username</span>
            <span class="item-value font-mono">{{ authStore.user?.username }}</span>
          </div>
          <div class="info-item">
            <span class="item-label">Full Name</span>
            <span class="item-value">{{ authStore.user?.fullName }}</span>
          </div>
          <div class="info-item">
            <span class="item-label">Corporate Email</span>
            <span class="item-value font-mono">{{ authStore.user?.email }}</span>
          </div>
          <div class="info-item">
            <span class="item-label">Account Status</span>
            <span class="item-value">
              <StatusBadge v-if="authStore.user" :status="authStore.user.status" />
            </span>
          </div>
        </div>
      </div>

      <!-- Roles & Permissions Card -->
      <div class="glass-panel info-card">
        <div class="card-header">
          <span class="card-icon">🛡️</span>
          <h3>Assigned RBAC Roles</h3>
        </div>
        <div class="roles-container">
          <div v-for="role in authStore.roles" :key="role" class="role-desc-card">
            <div class="role-badge-row">
              <RoleBadge :role="role" />
            </div>
            <p class="role-explanation" v-if="role === 'ROLE_CA_ADMIN'">
              Authorized for full administrative user provisioning, policy maintenance, and system governance.
            </p>
            <p class="role-explanation" v-else-if="role === 'ROLE_RA_OPERATOR'">
              Authorized for subscriber identity verification and certificate request lifecycle approvals.
            </p>
            <p class="role-explanation" v-else-if="role === 'ROLE_SECURITY_OFFICER'">
              Authorized for cryptographic key lifecycle, dual-control signing ceremonies, and HSM policy.
            </p>
            <p class="role-explanation" v-else-if="role === 'ROLE_AUDITOR'">
              Authorized for non-repudiable audit log inspection and compliance verification (read-only).
            </p>
            <p class="role-explanation" v-else>
              Authorized for certificate enrollment, CSR upload, and subscriber certificate retrieval.
            </p>
          </div>
        </div>
      </div>

      <!-- JWT Claims Inspector Card -->
      <div class="glass-panel info-card full-width">
        <div class="card-header">
          <span class="card-icon">🔑</span>
          <h3>Active JWT Token Claims (RFC 7519)</h3>
        </div>
        <p class="jwt-subtext">
          Decoded client-side from the verified JSON Web Token issued by the Spring Security authentication service.
        </p>

        <div v-if="tokenPayload" class="jwt-claims-table-wrapper">
          <table class="jwt-claims-table">
            <thead>
              <tr>
                <th>Claim Key</th>
                <th>Standard Description</th>
                <th>Verified Value</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="font-mono claim-key">sub</td>
                <td>Subject (Username)</td>
                <td class="font-mono text-primary">{{ tokenPayload.sub }}</td>
              </tr>
              <tr>
                <td class="font-mono claim-key">iss</td>
                <td>Token Issuer</td>
                <td class="font-mono">{{ tokenPayload.iss }}</td>
              </tr>
              <tr>
                <td class="font-mono claim-key">roles</td>
                <td>Granted Authorities Claim</td>
                <td class="font-mono text-primary">{{ JSON.stringify(tokenPayload.roles) }}</td>
              </tr>
              <tr>
                <td class="font-mono claim-key">iat</td>
                <td>Issued At Timestamp</td>
                <td class="font-mono">{{ new Date(tokenPayload.iat * 1000).toLocaleString() }}</td>
              </tr>
              <tr>
                <td class="font-mono claim-key">exp</td>
                <td>Expiration Timestamp</td>
                <td class="font-mono text-warning">{{ new Date(tokenPayload.exp * 1000).toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="text-muted">
          No JWT payload available in active session.
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.view-header h1 {
  font-size: 1.75rem;
  letter-spacing: -0.02em;
}

.header-subtitle {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin-top: 0.25rem;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 1.5rem;
}

.info-card {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.info-card.full-width {
  grid-column: 1 / -1;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.card-icon {
  font-size: 1.25rem;
}

.card-header h3 {
  font-size: 1.125rem;
  font-weight: 600;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  font-size: 0.875rem;
}

.info-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.item-label {
  color: var(--text-muted);
}

.item-value {
  color: var(--text-primary);
  font-weight: 500;
}

.roles-container {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.role-desc-card {
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--border-color);
  padding: 0.875rem;
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.role-explanation {
  font-size: 0.75rem;
  color: var(--text-secondary);
  line-height: 1.4;
}

.jwt-subtext {
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.jwt-claims-table-wrapper {
  overflow-x: auto;
}

.jwt-claims-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}

.jwt-claims-table th {
  text-align: left;
  padding: 0.625rem 0.875rem;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-color);
  font-size: 0.75rem;
  text-transform: uppercase;
}

.jwt-claims-table td {
  padding: 0.625rem 0.875rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.claim-key {
  color: var(--primary);
  font-weight: 600;
}

.font-mono {
  font-family: var(--font-mono);
}

.text-primary {
  color: var(--text-primary);
}

.text-warning {
  color: var(--warning);
}

.text-muted {
  color: var(--text-muted);
}
</style>
