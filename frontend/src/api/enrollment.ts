import { apiClient } from './client';
import type { PagedResponse } from './users';

export type RequestStatus =
  | 'PENDING_REVIEW'
  | 'PENDING_SECOND_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'ISSUED'
  | 'FAILED';

export type ApprovalAction = 'APPROVED' | 'REJECTED';
export type ReviewStage = 'INITIAL_REVIEW' | 'SECOND_APPROVAL';

export interface CertificateProfile {
  id: number;
  profileCode: string;
  profileName: string;
  profileType: string;
  defaultValidityDays: number;
  maxValidityDays: number;
  allowedKeyTypes: string[];
  keyUsages: string[];
  extendedKeyUsages: string[];
  requireSan: boolean;
  allowWildcards: boolean;
  requiresDualApproval: boolean;
  isActive: boolean;
}

export interface SanEntries {
  dns: string[];
  ip: string[];
  email: string[];
  uri: string[];
}

export interface RequestApproval {
  id: number;
  reviewerId: number;
  reviewerUsername: string;
  action: ApprovalAction;
  reviewStage: ReviewStage;
  reviewerComment?: string;
  clientIp: string;
  reviewedAt: string;
}

export interface EnrollmentRequest {
  requestId: number;
  requesterId: number;
  requesterUsername: string;
  profileCode: string;
  profileName: string;
  status: RequestStatus;
  subjectDn: string;
  sanEntries: SanEntries;
  rawSanEntries: string[];
  csrPem: string;
  keyAlgorithm: string;
  keyLength: number;
  fingerprint: string;
  validityDays: number;
  rejectionReason?: string;
  rejectionComment?: string;
  requiresDualApproval: boolean;
  submittedAt: string;
  updatedAt: string;
  approvals: RequestApproval[];
}

export interface EnrollmentSubmission {
  profileCode: string;
  validityDays?: number;
  csrPem: string;
}

export interface ApproveRequestPayload {
  comment?: string;
}

export interface RejectRequestPayload {
  rejectionReason: string;
  comment?: string;
}

export const enrollmentApi = {
  async getProfiles(): Promise<CertificateProfile[]> {
    const { data } = await apiClient.get<CertificateProfile[]>('/profiles');
    return data;
  },

  async submitEnrollment(payload: EnrollmentSubmission): Promise<EnrollmentRequest> {
    const { data } = await apiClient.post<EnrollmentRequest>('/enrollment/requests', payload);
    return data;
  },

  async getRequests(params?: {
    status?: RequestStatus;
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<PagedResponse<EnrollmentRequest>> {
    const { data } = await apiClient.get<PagedResponse<EnrollmentRequest>>('/enrollment/requests', { params });
    return data;
  },

  async getRequestDetails(id: number): Promise<EnrollmentRequest> {
    const { data } = await apiClient.get<EnrollmentRequest>(`/enrollment/requests/${id}`);
    return data;
  },

  async cancelRequest(id: number): Promise<EnrollmentRequest> {
    const { data } = await apiClient.post<EnrollmentRequest>(`/enrollment/requests/${id}/cancel`);
    return data;
  },

  async approveRequest(id: number, payload?: ApproveRequestPayload): Promise<EnrollmentRequest> {
    const { data } = await apiClient.post<EnrollmentRequest>(`/ra/requests/${id}/approve`, payload || {});
    return data;
  },

  async rejectRequest(id: number, payload: RejectRequestPayload): Promise<EnrollmentRequest> {
    const { data } = await apiClient.post<EnrollmentRequest>(`/ra/requests/${id}/reject`, payload);
    return data;
  }
};
