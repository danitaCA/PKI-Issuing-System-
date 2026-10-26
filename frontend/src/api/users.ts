import { apiClient } from './client';
import type { UserSummary, RoleName, UserStatus } from './auth';

export interface PagedResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
}

export interface CreateUserPayload {
  username: string;
  email: string;
  password: string;
  fullName: string;
  roles: RoleName[];
}

export interface UpdateUserPayload {
  email?: string;
  fullName?: string;
  status?: UserStatus;
  roles?: RoleName[];
  newPassword?: string;
}

export const usersApi = {
  async listUsers(page = 0, size = 20, sort = 'id,desc'): Promise<PagedResponse<UserSummary>> {
    const response = await apiClient.get<PagedResponse<UserSummary>>('/admin/users', {
      params: { page, size, sort }
    });
    return response.data;
  },

  async getUserById(id: number): Promise<UserSummary> {
    const response = await apiClient.get<UserSummary>(`/admin/users/${id}`);
    return response.data;
  },

  async createUser(payload: CreateUserPayload): Promise<UserSummary> {
    const response = await apiClient.post<UserSummary>('/admin/users', payload);
    return response.data;
  },

  async updateUser(id: number, payload: UpdateUserPayload): Promise<UserSummary> {
    const response = await apiClient.put<UserSummary>(`/admin/users/${id}`, payload);
    return response.data;
  },

  async lockUser(id: number, durationMinutes = 30, reason = 'Administrative Lock'): Promise<UserSummary> {
    const response = await apiClient.post<UserSummary>(`/admin/users/${id}/lock`, {
      durationMinutes,
      reason
    });
    return response.data;
  },

  async unlockUser(id: number): Promise<UserSummary> {
    const response = await apiClient.post<UserSummary>(`/admin/users/${id}/unlock`);
    return response.data;
  }
};
