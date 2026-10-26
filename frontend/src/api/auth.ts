import { apiClient } from './client';

export type RoleName = 
  | 'ROLE_CA_ADMIN' 
  | 'ROLE_RA_OPERATOR' 
  | 'ROLE_SECURITY_OFFICER' 
  | 'ROLE_AUDITOR' 
  | 'ROLE_END_ENTITY';

export type UserStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED';

export interface UserSummary {
  id: number;
  username: string;
  email: string;
  fullName: string;
  status: UserStatus;
  roles: RoleName[];
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  refreshToken: string;
  user: UserSummary;
}

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  fullName: string;
  status: UserStatus;
  roles: RoleName[];
  permissions: string[];
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  errorCode?: string;
  invalidParams?: Array<{ field: string; reason: string }>;
}

export const authApi = {
  async login(credentials: { username: string; password: string }): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/login', credentials);
    return response.data;
  },

  async refresh(refreshToken: string): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/refresh', { refreshToken });
    return response.data;
  },

  async getMe(): Promise<UserProfile> {
    const response = await apiClient.get<UserProfile>('/auth/me');
    return response.data;
  },

  async logout(): Promise<void> {
    await apiClient.post('/auth/logout');
  },

  async register(data: { username: string; email: string; password: string; fullName: string }): Promise<UserSummary> {
    const response = await apiClient.post<UserSummary>('/auth/register', data);
    return response.data;
  }
};
