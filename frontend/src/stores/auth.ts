import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { authApi, type UserProfile, type RoleName, type ProblemDetail } from '../api/auth';
import type { AxiosError } from 'axios';

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserProfile | null>(null);
  const accessToken = ref<string | null>(sessionStorage.getItem('pki_access_token'));
  const refreshToken = ref<string | null>(sessionStorage.getItem('pki_refresh_token'));
  const loading = ref(false);
  const error = ref<string | null>(null);

  const isAuthenticated = computed(() => !!accessToken.value && !!user.value);
  const roles = computed<RoleName[]>(() => user.value?.roles || []);
  
  const hasRole = (role: RoleName) => roles.value.includes(role);
  const hasAnyRole = (checkRoles: RoleName[]) => checkRoles.some(r => roles.value.includes(r));
  
  const isCaAdmin = computed(() => hasRole('ROLE_CA_ADMIN'));
  const isRaOperator = computed(() => hasRole('ROLE_RA_OPERATOR'));
  const isSecurityOfficer = computed(() => hasRole('ROLE_SECURITY_OFFICER'));
  const isAuditor = computed(() => hasRole('ROLE_AUDITOR'));
  const isEndEntity = computed(() => hasRole('ROLE_END_ENTITY'));

  const primaryRole = computed<RoleName | 'GUEST'>(() => {
    if (isCaAdmin.value) return 'ROLE_CA_ADMIN';
    if (isRaOperator.value) return 'ROLE_RA_OPERATOR';
    if (isSecurityOfficer.value) return 'ROLE_SECURITY_OFFICER';
    if (isAuditor.value) return 'ROLE_AUDITOR';
    if (isEndEntity.value) return 'ROLE_END_ENTITY';
    return 'GUEST';
  });

  async function login(credentials: { username: string; password: string }) {
    loading.value = true;
    error.value = null;
    try {
      const data = await authApi.login(credentials);
      accessToken.value = data.accessToken;
      refreshToken.value = data.refreshToken;
      sessionStorage.setItem('pki_access_token', data.accessToken);
      sessionStorage.setItem('pki_refresh_token', data.refreshToken);

      await fetchProfile();
      return true;
    } catch (err: unknown) {
      const axiosError = err as AxiosError<ProblemDetail>;
      error.value = axiosError.response?.data?.detail || 'Authentication failed. Please check credentials.';
      return false;
    } finally {
      loading.value = false;
    }
  }

  async function fetchProfile() {
    if (!accessToken.value) return;
    try {
      const profile = await authApi.getMe();
      user.value = profile;
    } catch {
      logout();
    }
  }

  async function logout() {
    try {
      if (accessToken.value) {
        await authApi.logout();
      }
    } catch {
      // Ignored on logout
    } finally {
      user.value = null;
      accessToken.value = null;
      refreshToken.value = null;
      sessionStorage.removeItem('pki_access_token');
      sessionStorage.removeItem('pki_refresh_token');
    }
  }

  async function initAuth() {
    if (accessToken.value) {
      await fetchProfile();
    }
  }

  return {
    user,
    accessToken,
    refreshToken,
    loading,
    error,
    isAuthenticated,
    roles,
    primaryRole,
    hasRole,
    hasAnyRole,
    isCaAdmin,
    isRaOperator,
    isSecurityOfficer,
    isAuditor,
    isEndEntity,
    login,
    logout,
    fetchProfile,
    initAuth
  };
});
