<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';
import { apiClient } from '../api/client';
import RoleBadge from '../components/RoleBadge.vue';
import StatusBadge from '../components/StatusBadge.vue';

const authStore = useAuthStore();

interface HealthData {
  status: string;
  database: string;
  timestamp: string;
  version: string;
}

const health = ref<HealthData | null>(null);
const healthLoading = ref(true);
const healthError = ref<string | null>(null);

onMounted(async () => {
  try {
    const res = await apiClient.get<HealthData>('/system/health');
    health.value = res.data;
  } catch {
    healthError.value = 'Failed to fetch live health metrics from backend service.';
  } finally {
    healthLoading.value = false;
  }
});
</script>

<template>
  <div class="dashboard-page">
    <!-- Welcome Persona Banner -->
    <div class="welcome-banner glass-panel">
      <div class="banner-content">
        <div class="banner-text">
          <div class="persona-tag">
            <span>Security Persona:</span>
            <RoleBadge v-if="authStore.primaryRole !== 'GUEST'" :role="authStore.primaryRole" />
          </div>
          <h1 class="user-greeting">Welcome back, {{ authStore.user?.fullName }}</h1>
          <p class="role-desc" v-if="authStore.isCaAdmin">
            You hold the <strong>CA Administrator</strong> role. You have authorization to manage system accounts, configure system policies, and oversee trust anchor operations.
          </p>
          <p class="role-desc" v-else-if="authStore.isRaOperator">
            You hold the <strong>RA Operator</strong> role. You are authorized to review certificate requests, validate identity proofs, and approve CSRs in Phase 3.
          </p>
          <p class="role-desc" v-else-if="authStore.isSecurityOfficer">
            You hold the <strong>Security Officer</strong> role. You supervise cryptographic policy enforcement, dual-control operations, and HSM key lifecycle configurations.
          </p>
          <p class="role-desc" v-else-if="authStore.isAuditor">
            You hold the <strong>Auditor</strong> role. You have read-only access across the CA system to inspect immutable audit trails and verify compliance.
          </p>
          <p class="role-desc" v-else>
            You hold the <strong>End Entity</strong> role. You may submit CSRs for enrollment and manage your issued X.509 client and TLS certificates.
          </p>
        </div>

        <div class="banner-status">
          <div class="status-chip">
            <span class="chip-label">Account Status</span>
            <StatusBadge v-if="authStore.user" :status="authStore.user.status" />
          </div>
          <div class="status-chip">
            <span class="chip-label">Identifier</span>
            <code class="chip-id">#{{ authStore.user?.id }}</code>
          </div>
        </div>
      </div>
    </div>

    <!-- Metrics Grid -->
    <div class="dashboard-grid">
      <!-- System Health Card -->
      <div class="glass-panel metric-card">
        <div class="metric-header">
          <span class="metric-title">Backend & Database Status</span>
          <span class="metric-badge" :class="health?.status === 'UP' ? 'healthy' : 'warning'">
            {{ healthLoading ? 'Checking...' : (health?.status || 'OFFLINE') }}
          </span>
        </div>
        <div v-if="healthLoading" class="metric-loading">
          <div class="pulse-loader"></div>
          <span>Pinging backend health endpoint...</span>
        </div>
        <div v-else-if="health" class="metric-body">
          <div class="metric-row">
            <span class="row-label">MySQL Database</span>
            <span class="row-val text-success">CONNECTED ({{ health.database }})</span>
          </div>
          <div class="metric-row">
            <span class="row-label">System Version</span>
            <span class="row-val font-mono">{{ health.version }}</span>
          </div>
          <div class="metric-row">
            <span class="row-label">Server Timestamp</span>
            <span class="row-val font-mono">{{ new Date(health.timestamp).toLocaleTimeString() }}</span>
          </div>
        </div>
        <div v-else class="metric-error">
          {{ healthError }}
        </div>
      </div>

      <!-- Security Controls Card -->
      <div class="glass-panel metric-card">
        <div class="metric-header">
          <span class="metric-title">Phase 2 Security Baseline</span>
          <span class="metric-badge healthy">ACTIVE</span>
        </div>
        <div class="metric-body">
          <div class="metric-row">
            <span class="row-label">Password Hashing</span>
            <span class="row-val">BCrypt (Work Factor 12)</span>
          </div>
          <div class="metric-row">
            <span class="row-label">Authentication Token</span>
            <span class="row-val">RFC 7519 HMAC-SHA256 JWT</span>
          </div>
          <div class="metric-row">
            <span class="row-label">Account Lockout</span>
            <span class="row-val">5 Attempts / 30 Min Lock</span>
          </div>
          <div class="metric-row">
            <span class="row-label">RBAC Model</span>
            <span class="row-val">5 Primary Roles Enforced</span>
          </div>
        </div>
      </div>

      <!-- Role-Based Quick Actions Card -->
      <div class="glass-panel metric-card">
        <div class="metric-header">
          <span class="metric-title">Permitted Actions</span>
        </div>
        <div class="quick-actions">
          <router-link
            v-if="authStore.isCaAdmin"
            to="/admin/users"
            class="btn btn-primary btn-sm action-btn"
          >
            <svg viewBox="0 0 20 20" fill="currentColor" class="action-icon">
              <path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z" />
            </svg>
            <span>Manage User Directory</span>
          </router-link>

          <router-link to="/profile" class="btn btn-secondary btn-sm action-btn">
            <svg viewBox="0 0 20 20" fill="currentColor" class="action-icon">
              <path fill-rule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clip-rule="evenodd" />
            </svg>
            <span>View Identity & Permissions</span>
          </router-link>

          <div v-if="!authStore.isCaAdmin" class="action-note">
            <span class="note-icon">ℹ️</span>
            <span>Administrative user management is restricted to CA_ADMIN via server-side Spring Security method authorization.</span>
          </div>
        </div>
      </div>
    </div>

    <!-- CA Hierarchy & Roadmap Blueprint Preview -->
    <div class="roadmap-card glass-panel">
      <div class="roadmap-header">
        <div class="roadmap-title-line">
          <span class="roadmap-pill">10-Phase CA Roadmap</span>
          <h2>PKI Issuing CA Development Progression</h2>
        </div>
        <span class="current-phase-badge">Phase 2: Auth & RBAC (Complete)</span>
      </div>

      <div class="phases-timeline">
        <div class="phase-node completed">
          <div class="node-circle">✓</div>
          <span class="node-name">Phase 1</span>
          <span class="node-desc">Design & Architecture</span>
        </div>
        <div class="phase-connector active"></div>
        <div class="phase-node active">
          <div class="node-circle">2</div>
          <span class="node-name">Phase 2</span>
          <span class="node-desc">Auth, DB & 5-Role RBAC</span>
        </div>
        <div class="phase-connector"></div>
        <div class="phase-node upcoming">
          <div class="node-circle">3</div>
          <span class="node-name">Phase 3</span>
          <span class="node-desc">CSR & RA Vetting</span>
        </div>
        <div class="phase-connector"></div>
        <div class="phase-node upcoming">
          <div class="node-circle">4</div>
          <span class="node-name">Phase 4</span>
          <span class="node-desc">Profiles & Policy Engine</span>
        </div>
        <div class="phase-connector"></div>
        <div class="phase-node upcoming">
          <div class="node-circle">5</div>
          <span class="node-name">Phase 5</span>
          <span class="node-desc">SoftHSM & Signing</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.welcome-banner {
  background: linear-gradient(135deg, rgba(17, 24, 39, 0.9) 0%, rgba(30, 41, 59, 0.8) 100%);
  border-left: 4px solid var(--primary);
}

.banner-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1.5rem;
  flex-wrap: wrap;
}

.banner-text {
  max-width: 720px;
}

.persona-tag {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-muted);
  font-weight: 600;
  text-transform: uppercase;
  margin-bottom: 0.5rem;
}

.user-greeting {
  font-size: 1.75rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin-bottom: 0.5rem;
}

.role-desc {
  font-size: 0.875rem;
  color: var(--text-secondary);
  line-height: 1.6;
}

.banner-status {
  display: flex;
  gap: 1rem;
}

.status-chip {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
  background: rgba(0, 0, 0, 0.25);
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.chip-label {
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
}

.chip-id {
  font-family: var(--font-mono);
  font-size: 0.8125rem;
  color: var(--primary);
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.metric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.metric-badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-full);
}

.metric-badge.healthy {
  background: var(--success-bg);
  color: var(--success);
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.metric-badge.warning {
  background: var(--warning-bg);
  color: var(--warning);
}

.metric-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.8125rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  padding-bottom: 0.5rem;
}

.metric-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.row-label {
  color: var(--text-muted);
}

.row-val {
  color: var(--text-primary);
  font-weight: 500;
}

.text-success {
  color: var(--success);
}

.font-mono {
  font-family: var(--font-mono);
  font-size: 0.75rem;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.action-btn {
  width: 100%;
  justify-content: flex-start;
  padding: 0.625rem 0.875rem;
}

.action-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.action-note {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.03);
  padding: 0.625rem;
  border-radius: var(--radius-sm);
  line-height: 1.4;
}

.note-icon {
  font-size: 0.875rem;
}

.roadmap-card {
  margin-top: 0.5rem;
  padding: 1.75rem;
}

.roadmap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.roadmap-pill {
  font-size: 0.6875rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--primary);
  letter-spacing: 0.05em;
}

.current-phase-badge {
  font-size: 0.75rem;
  font-weight: 600;
  background: rgba(59, 130, 246, 0.15);
  color: var(--primary);
  border: 1px solid rgba(59, 130, 246, 0.3);
  padding: 0.3rem 0.75rem;
  border-radius: var(--radius-full);
}

.phases-timeline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  overflow-x: auto;
  padding: 0.5rem 0;
}

.phase-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.375rem;
  min-width: 110px;
}

.node-circle {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.8125rem;
  transition: all var(--transition-fast);
}

.phase-node.completed .node-circle {
  background: var(--success-bg);
  color: var(--success);
  border: 2px solid var(--success);
}

.phase-node.active .node-circle {
  background: var(--primary);
  color: #ffffff;
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.5);
  border: 2px solid #ffffff;
}

.phase-node.upcoming .node-circle {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-muted);
  border: 2px solid var(--border-color);
}

.node-name {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-primary);
}

.node-desc {
  font-size: 0.6875rem;
  color: var(--text-muted);
}

.phase-connector {
  flex: 1;
  height: 2px;
  background: var(--border-color);
  margin: 0 0.5rem;
  margin-bottom: 2rem;
}

.phase-connector.active {
  background: var(--primary);
}
</style>
