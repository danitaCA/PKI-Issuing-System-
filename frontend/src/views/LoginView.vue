<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { authApi, type ProblemDetail } from '../api/auth';
import ProblemAlert from '../components/ProblemAlert.vue';
import type { AxiosError } from 'axios';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const activeTab = ref<'login' | 'register'>('login');
const username = ref('');
const password = ref('');
const errorProblem = ref<ProblemDetail | null>(null);
const successMessage = ref<string | null>(null);

// Registration Form State
const regUsername = ref('');
const regEmail = ref('');
const regFullName = ref('');
const regPassword = ref('');
const regConfirmPassword = ref('');
const isSubmitting = ref(false);

const demoPersonas = [
  { role: 'CA_ADMIN', username: 'admin', label: 'Admin (CA_ADMIN)' },
  { role: 'RA_OPERATOR', username: 'ra_operator', label: 'RA Operator' },
  { role: 'SECURITY_OFFICER', username: 'sec_officer', label: 'Security Officer' },
  { role: 'AUDITOR', username: 'auditor', label: 'Auditor' },
  { role: 'END_ENTITY', username: 'end_entity', label: 'End Entity' }
];

function selectPersona(personaUsername: string) {
  username.value = personaUsername;
  password.value = 'ChangeMe123!';
  errorProblem.value = null;
}

async function handleLogin() {
  errorProblem.value = null;
  successMessage.value = null;

  if (!username.value || !password.value) {
    errorProblem.value = {
      type: 'about:blank',
      title: 'Validation Error',
      status: 400,
      detail: 'Please provide both username and password.'
    };
    return;
  }

  isSubmitting.value = true;
  try {
    const success = await authStore.login({
      username: username.value,
      password: password.value
    });

    if (success) {
      const redirectUrl = (route.query.redirect as string) || '/dashboard';
      router.push(redirectUrl);
    } else {
      errorProblem.value = {
        type: 'https://pki-ca.local/errors/authentication-failed',
        title: 'Authentication Failed',
        status: 401,
        detail: authStore.error || 'Invalid credentials or account locked.'
      };
    }
  } finally {
    isSubmitting.value = false;
  }
}

async function handleRegister() {
  errorProblem.value = null;
  successMessage.value = null;

  if (regPassword.value !== regConfirmPassword.value) {
    errorProblem.value = {
      type: 'about:blank',
      title: 'Validation Error',
      status: 400,
      detail: 'Passwords do not match.'
    };
    return;
  }

  isSubmitting.value = true;
  try {
    await authApi.register({
      username: regUsername.value,
      email: regEmail.value,
      fullName: regFullName.value,
      password: regPassword.value
    });

    successMessage.value = 'Registration successful! You may now sign in with your credentials.';
    activeTab.value = 'login';
    username.value = regUsername.value;
    password.value = '';
    regUsername.value = '';
    regEmail.value = '';
    regFullName.value = '';
    regPassword.value = '';
    regConfirmPassword.value = '';
  } catch (err: unknown) {
    const axiosError = err as AxiosError<ProblemDetail>;
    if (axiosError.response?.data) {
      errorProblem.value = axiosError.response.data;
    } else {
      errorProblem.value = {
        type: 'about:blank',
        title: 'Registration Error',
        status: 500,
        detail: 'An unexpected error occurred during registration.'
      };
    }
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <div class="login-wrapper">
    <div class="login-container">
      <!-- Brand & Header -->
      <div class="login-brand">
        <div class="brand-badge-icon">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/>
          </svg>
        </div>
        <h1 class="login-title">PKI Issuing CA Portal</h1>
        <p class="login-subtitle">Enterprise Certificate Authority Management System</p>
      </div>

      <!-- Quick Persona Switcher for Evaluation -->
      <div class="persona-card glass-panel">
        <div class="persona-header">
          <span class="persona-icon">⚡</span>
          <span class="persona-text">Quick Persona Switcher (Evaluation Preset)</span>
        </div>
        <div class="persona-buttons">
          <button
            v-for="p in demoPersonas"
            :key="p.username"
            type="button"
            class="persona-btn"
            :class="{ active: username === p.username }"
            @click="selectPersona(p.username)"
          >
            {{ p.label }}
          </button>
        </div>
      </div>

      <!-- Main Auth Card -->
      <div class="auth-card glass-panel">
        <div class="tabs-header">
          <button 
            type="button" 
            class="tab-btn" 
            :class="{ active: activeTab === 'login' }"
            @click="activeTab = 'login'"
          >
            Sign In
          </button>
          <button 
            type="button" 
            class="tab-btn" 
            :class="{ active: activeTab === 'register' }"
            @click="activeTab = 'register'"
          >
            Register (End Entity)
          </button>
        </div>

        <ProblemAlert :problem="errorProblem" @dismiss="errorProblem = null" />

        <div v-if="successMessage" class="success-alert">
          <svg viewBox="0 0 20 20" fill="currentColor" class="alert-icon">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
          </svg>
          <span>{{ successMessage }}</span>
        </div>

        <!-- Login Form -->
        <form v-if="activeTab === 'login'" @submit.prevent="handleLogin" id="login-form">
          <div class="form-group">
            <label for="login-username" class="form-label">Username</label>
            <input
              id="login-username"
              v-model="username"
              type="text"
              class="form-input"
              placeholder="e.g. admin or alice"
              autocomplete="username"
              required
            />
          </div>

          <div class="form-group">
            <label for="login-password" class="form-label">Password</label>
            <input
              id="login-password"
              v-model="password"
              type="password"
              class="form-input"
              placeholder="••••••••••••"
              autocomplete="current-password"
              required
            />
          </div>

          <button
            type="submit"
            id="login-submit-btn"
            class="btn btn-primary btn-block"
            :disabled="isSubmitting"
          >
            <span v-if="isSubmitting">Authenticating...</span>
            <span v-else>Authenticate Session</span>
          </button>
        </form>

        <!-- Register Form -->
        <form v-else @submit.prevent="handleRegister" id="register-form">
          <div class="form-group">
            <label for="reg-username" class="form-label">Username</label>
            <input
              id="reg-username"
              v-model="regUsername"
              type="text"
              class="form-input"
              placeholder="3-50 alphanumeric characters"
              required
            />
          </div>

          <div class="form-group">
            <label for="reg-fullname" class="form-label">Full Name</label>
            <input
              id="reg-fullname"
              v-model="regFullName"
              type="text"
              class="form-input"
              placeholder="e.g. Jane Doe"
              required
            />
          </div>

          <div class="form-group">
            <label for="reg-email" class="form-label">Corporate Email</label>
            <input
              id="reg-email"
              v-model="regEmail"
              type="email"
              class="form-input"
              placeholder="user@example.com"
              required
            />
          </div>

          <div class="form-group">
            <label for="reg-password" class="form-label">Password</label>
            <input
              id="reg-password"
              v-model="regPassword"
              type="password"
              class="form-input"
              placeholder="Min 10 chars, uppercase, lowercase, number, symbol"
              required
            />
          </div>

          <div class="form-group">
            <label for="reg-confirm-password" class="form-label">Confirm Password</label>
            <input
              id="reg-confirm-password"
              v-model="regConfirmPassword"
              type="password"
              class="form-input"
              placeholder="Re-type password"
              required
            />
          </div>

          <button
            type="submit"
            id="register-submit-btn"
            class="btn btn-primary btn-block"
            :disabled="isSubmitting"
          >
            <span v-if="isSubmitting">Registering...</span>
            <span v-else>Create End Entity Account</span>
          </button>
        </form>

        <div class="security-footnote">
          <svg viewBox="0 0 20 20" fill="currentColor" class="footnote-icon">
            <path fill-rule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
          </svg>
          <span>Enforced via BCrypt work factor 12, RFC 7519 HMAC-SHA256 JWT, & 5-Attempt Account Lockout.</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem;
}

.login-container {
  width: 100%;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.login-brand {
  text-align: center;
}

.brand-badge-icon {
  width: 52px;
  height: 52px;
  background: var(--accent-gradient);
  border-radius: var(--radius-lg);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  margin-bottom: 0.75rem;
  box-shadow: 0 4px 20px rgba(59, 130, 246, 0.4);
}

.brand-badge-icon svg {
  width: 28px;
  height: 28px;
}

.login-title {
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.login-subtitle {
  font-size: 0.8125rem;
  color: var(--text-secondary);
  margin-top: 0.25rem;
}

.persona-card {
  padding: 1rem;
  border-radius: var(--radius-md);
  background: rgba(17, 24, 39, 0.6);
}

.persona-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--primary);
  margin-bottom: 0.625rem;
}

.persona-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.persona-btn {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 0.7rem;
  font-weight: 500;
  padding: 0.25rem 0.625rem;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.persona-btn:hover {
  background: rgba(59, 130, 246, 0.15);
  color: var(--primary);
  border-color: rgba(59, 130, 246, 0.3);
}

.persona-btn.active {
  background: var(--primary);
  color: #ffffff;
  border-color: var(--primary);
}

.auth-card {
  padding: 2rem;
}

.tabs-header {
  display: flex;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 1.5rem;
}

.tab-btn {
  flex: 1;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  padding: 0.625rem 1rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tab-btn:hover {
  color: var(--text-primary);
}

.tab-btn.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
}

.btn-block {
  width: 100%;
  margin-top: 0.5rem;
}

.success-alert {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--success-bg);
  color: var(--success);
  border: 1px solid rgba(16, 185, 129, 0.3);
  padding: 0.75rem 1rem;
  border-radius: var(--radius-md);
  font-size: 0.8125rem;
  margin-bottom: 1.25rem;
}

.alert-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.security-footnote {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);
  font-size: 0.7rem;
  color: var(--text-muted);
  line-height: 1.4;
}

.footnote-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--text-muted);
}
</style>
