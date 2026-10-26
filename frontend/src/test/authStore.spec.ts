import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useAuthStore } from '../stores/auth';
import { authApi } from '../api/auth';

vi.mock('../api/auth', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    getMe: vi.fn(),
    refresh: vi.fn(),
    register: vi.fn()
  }
}));

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('initializes with unauthenticated state', () => {
    const authStore = useAuthStore();
    expect(authStore.isAuthenticated).toBe(false);
    expect(authStore.user).toBeNull();
    expect(authStore.roles).toEqual([]);
    expect(authStore.primaryRole).toBe('GUEST');
    expect(authStore.isCaAdmin).toBe(false);
  });

  it('successfully logs in and populates user profile and roles', async () => {
    const authStore = useAuthStore();

    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: 'mock-jwt-token',
      tokenType: 'Bearer',
      expiresInSeconds: 900,
      refreshToken: 'mock-refresh-token',
      user: {
        id: 1,
        username: 'admin',
        email: 'admin@pki.local',
        fullName: 'CA Administrator',
        status: 'ACTIVE',
        roles: ['ROLE_CA_ADMIN'],
        createdAt: '2026-10-20T12:00:00Z',
        updatedAt: '2026-10-20T12:00:00Z'
      }
    });

    vi.mocked(authApi.getMe).mockResolvedValueOnce({
      id: 1,
      username: 'admin',
      email: 'admin@pki.local',
      fullName: 'CA Administrator',
      status: 'ACTIVE',
      roles: ['ROLE_CA_ADMIN'],
      permissions: ['pki:user:create', 'pki:ca:manage']
    });

    const success = await authStore.login({ username: 'admin', password: 'Password123!' });

    expect(success).toBe(true);
    expect(authStore.isAuthenticated).toBe(true);
    expect(authStore.accessToken).toBe('mock-jwt-token');
    expect(authStore.refreshToken).toBe('mock-refresh-token');
    expect(authStore.isCaAdmin).toBe(true);
    expect(authStore.isRaOperator).toBe(false);
    expect(authStore.primaryRole).toBe('ROLE_CA_ADMIN');
    expect(sessionStorage.getItem('pki_access_token')).toBe('mock-jwt-token');
  });

  it('correctly evaluates multiple roles and primary role hierarchy', () => {
    const authStore = useAuthStore();
    authStore.user = {
      id: 2,
      username: 'dual_role',
      email: 'dual@pki.local',
      fullName: 'Dual Operator',
      status: 'ACTIVE',
      roles: ['ROLE_RA_OPERATOR', 'ROLE_END_ENTITY'],
      permissions: ['pki:csr:review', 'pki:csr:submit']
    };
    authStore.accessToken = 'some-token';

    expect(authStore.isAuthenticated).toBe(true);
    expect(authStore.hasRole('ROLE_RA_OPERATOR')).toBe(true);
    expect(authStore.hasRole('ROLE_END_ENTITY')).toBe(true);
    expect(authStore.hasRole('ROLE_CA_ADMIN')).toBe(false);
    expect(authStore.hasAnyRole(['ROLE_CA_ADMIN', 'ROLE_RA_OPERATOR'])).toBe(true);
    expect(authStore.primaryRole).toBe('ROLE_RA_OPERATOR');
  });

  it('clears state and storage on logout', async () => {
    const authStore = useAuthStore();
    sessionStorage.setItem('pki_access_token', 'token-to-clear');
    sessionStorage.setItem('pki_refresh_token', 'refresh-to-clear');
    authStore.accessToken = 'token-to-clear';
    authStore.refreshToken = 'refresh-to-clear';
    authStore.user = {
      id: 3,
      username: 'auditor',
      email: 'auditor@pki.local',
      fullName: 'Audit Inspector',
      status: 'ACTIVE',
      roles: ['ROLE_AUDITOR'],
      permissions: ['pki:audit:read']
    };

    await authStore.logout();

    expect(authStore.isAuthenticated).toBe(false);
    expect(authStore.user).toBeNull();
    expect(authStore.accessToken).toBeNull();
    expect(authStore.refreshToken).toBeNull();
    expect(sessionStorage.getItem('pki_access_token')).toBeNull();
    expect(sessionStorage.getItem('pki_refresh_token')).toBeNull();
  });
});
