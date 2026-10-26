import { apiClient } from './client';
import type { PagedResponse } from './users';

export type CertificateStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED' | 'SUSPENDED';
export type CaStatus = 'INITIALIZING' | 'ACTIVE' | 'SUSPENDED' | 'RETIRED';

export interface CertificateResponse {
  certificateId: number;
  serialNumber: string;
  subjectDn: string;
  issuerDn: string;
  fingerprintSha256: string;
  notBefore: string;
  notAfter: string;
  status: CertificateStatus;
  profileCode: string;
  keyAlgorithm: string;
  keyLength: number;
  sanDns: string[];
  sanIps: string[];
  sanEmails: string[];
  sanUris: string[];
  issuedAt: string;
  requestId: number;
  certificatePem: string;
  chainPem: string;
  downloadUri: string;
}

export interface CaAuthorityResponse {
  id: number;
  commonName: string;
  subjectDn: string;
  keyAlgorithm: string;
  keyLength: number;
  status: CaStatus;
  serialNumber: string;
  notBefore: string;
  notAfter: string;
  certificatePem?: string;
  chainPem?: string;
}

export const certificatesApi = {
  /**
   * Search certificates repository (RA Operator, CA Admin, Security Officer, Auditor)
   */
  async searchCertificates(params?: {
    status?: CertificateStatus;
    commonName?: string;
    serialNumber?: string;
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<PagedResponse<CertificateResponse>> {
    const { data } = await apiClient.get<PagedResponse<CertificateResponse>>('/certificates', { params });
    return data;
  },

  /**
   * Get certificates belonging to current authenticated End Entity
   */
  async getMyCertificates(params?: {
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<PagedResponse<CertificateResponse>> {
    const { data } = await apiClient.get<PagedResponse<CertificateResponse>>('/certificates/my', { params });
    return data;
  },

  /**
   * Get certificate details by serial number
   */
  async getCertificate(serialNumber: string): Promise<CertificateResponse> {
    const { data } = await apiClient.get<CertificateResponse>(`/certificates/${serialNumber}`);
    return data;
  },

  /**
   * Issue certificate from approved enrollment request (RA Operator, CA Admin)
   */
  async issueCertificate(requestId: number): Promise<CertificateResponse> {
    const { data } = await apiClient.post<CertificateResponse>(`/ca/requests/${requestId}/issue`);
    return data;
  },

  /**
   * List active CA Authorities and Enclave Metadata
   */
  async getCaAuthorities(): Promise<CaAuthorityResponse[]> {
    const { data } = await apiClient.get<CaAuthorityResponse[]>('/ca');
    return data;
  },

  /**
   * Download certificate in specified format (pem, der, or p7b)
   */
  async downloadCertificate(serialNumber: string, format: 'pem' | 'der' | 'p7b' = 'pem'): Promise<Blob> {
    const response = await apiClient.get(`/certificates/${serialNumber}/download`, {
      params: { format },
      responseType: 'blob'
    });
    return response.data;
  }
};
