<script setup lang="ts">
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
</script>

<template>
  <aside class="sidebar">
    <div class="nav-section">
      <span class="nav-header">Core Navigation</span>
      <router-link to="/dashboard" class="nav-item" active-class="active">
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path d="M10.707 2.293a1 1 0 00-1.414 0l-7 7a1 1 0 001.414 1.414L4 10.414V17a1 1 0 001 1h2a1 1 0 001-1v-2a1 1 0 011-1h2a1 1 0 011 1v2a1 1 0 001 1h2a1 1 0 001-1v-6.586l.293.293a1 1 0 001.414-1.414l-7-7z" />
        </svg>
        <span>Dashboard</span>
      </router-link>

      <router-link
        v-if="authStore.hasAnyRole(['ROLE_END_ENTITY', 'ROLE_RA_OPERATOR', 'ROLE_CA_ADMIN'])"
        to="/enrollment"
        class="nav-item"
        active-class="active"
      >
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path fill-rule="evenodd" d="M4 4a2 2 0 012-2h4.586A2 2 0 0112 2.586L15.414 6A2 2 0 0116 7.414V16a2 2 0 01-2 2H6a2 2 0 01-2-2V4z" clip-rule="evenodd" />
        </svg>
        <span>Enrollment Request</span>
      </router-link>

      <router-link
        v-if="authStore.hasAnyRole(['ROLE_RA_OPERATOR', 'ROLE_CA_ADMIN', 'ROLE_SECURITY_OFFICER', 'ROLE_AUDITOR'])"
        to="/ra/queue"
        class="nav-item"
        active-class="active"
      >
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z" />
          <path fill-rule="evenodd" d="M4 5a2 2 0 012-2 3 3 0 003 3h2a3 3 0 003-3 2 2 0 012 2v11a2 2 0 01-2 2H6a2 2 0 01-2-2V5zm3 4a1 1 0 000 2h.01a1 1 0 100-2H7zm3 0a1 1 0 000 2h3a1 1 0 100-2h-3zm-3 4a1 1 0 100 2h.01a1 1 0 100-2H7zm3 0a1 1 0 100 2h3a1 1 0 100-2h-3z" clip-rule="evenodd" />
        </svg>
        <span>RA Review Queue</span>
      </router-link>

      <router-link
        to="/certificates"
        class="nav-item"
        active-class="active"
      >
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path fill-rule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
        </svg>
        <span>Certificates Repository</span>
      </router-link>

      <router-link v-if="authStore.isCaAdmin" to="/admin/users" class="nav-item" active-class="active">
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path d="M9 6a3 3 0 11-6 0 3 3 0 016 0zM17 6a3 3 0 11-6 0 3 3 0 016 0zM12.93 17c.046-.327.07-.66.07-1a6.97 6.97 0 00-1.5-4.33A5 5 0 0119 16v1h-6.07zM6 11a5 5 0 015 5v1H1v-1a5 5 0 015-5z" />
        </svg>
        <span>User Management</span>
      </router-link>

      <router-link to="/profile" class="nav-item" active-class="active">
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path fill-rule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clip-rule="evenodd" />
        </svg>
        <span>My Identity</span>
      </router-link>
    </div>

    <!-- PKI Lifecycle Modules Roadmap Guidance -->
    <div class="nav-section">
      <span class="nav-header">PKI Architecture Roadmap</span>

      <div class="nav-item active" style="opacity: 0.85; pointer-events: none;" title="Active in Phase 4">
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon" style="color: var(--success);">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
        </svg>
        <span>SoftHSM Signing Active</span>
        <span class="phase-tag" style="color: var(--success);">Phase 4</span>
      </div>

      <div class="nav-item disabled" title="Available in Phase 6">
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clip-rule="evenodd" />
        </svg>
        <span>SoftHSM Token Enclave</span>
        <span class="phase-tag">Phase 6</span>
      </div>

      <div class="nav-item disabled" title="Available in Phase 7">
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clip-rule="evenodd" />
        </svg>
        <span>CRL & OCSP Responder</span>
        <span class="phase-tag">Phase 7</span>
      </div>

      <div class="nav-item disabled" title="Available in Phase 8">
        <svg viewBox="0 0 20 20" fill="currentColor" class="nav-icon">
          <path fill-rule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" clip-rule="evenodd" />
        </svg>
        <span>HMAC Audit Trail</span>
        <span class="phase-tag">Phase 8</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 260px;
  background: var(--bg-surface);
  border-right: 1px solid var(--border-color);
  padding: 1.5rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-height: calc(100vh - 64px);
}

.nav-section {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.nav-header {
  font-size: 0.6875rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-muted);
  letter-spacing: 0.08em;
  padding: 0 0.75rem 0.5rem;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 0.75rem;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
  text-decoration: none;
  transition: all var(--transition-fast);
}

.nav-item:hover:not(.disabled) {
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 600;
  border-left: 3px solid var(--primary);
}

.nav-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
  font-size: 0.8125rem;
}

.nav-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.phase-tag {
  margin-left: auto;
  font-size: 0.65rem;
  background: rgba(255, 255, 255, 0.06);
  padding: 0.1rem 0.4rem;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
}
</style>
