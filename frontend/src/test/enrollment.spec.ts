import { describe, it, expect, beforeEach, vi } from 'vitest';
import { enrollmentApi } from '../api/enrollment';
import { apiClient } from '../api/client';

vi.mock('../api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  }
}));

describe('Enrollment and RA API Client Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches active certificate profiles correctly', async () => {
    const mockProfiles = [
      {
        id: 1,
        profileCode: 'TLS_SERVER',
        profileName: 'Internal TLS Server Certificate',
        profileType: 'SERVER_AUTH',
        defaultValidityDays: 365,
        maxValidityDays: 730,
        allowedKeyTypes: ['RSA-2048', 'RSA-3072'],
        keyUsages: ['digitalSignature', 'keyEncipherment'],
        extendedKeyUsages: ['1.3.6.1.5.5.7.3.1'],
        requireSan: true,
        allowWildcards: false,
        requiresDualApproval: false,
        isActive: true,
      }
    ];

    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockProfiles });

    const profiles = await enrollmentApi.getProfiles();
    expect(apiClient.get).toHaveBeenCalledWith('/profiles');
    expect(profiles).toHaveLength(1);
    expect(profiles[0].profileCode).toBe('TLS_SERVER');
    expect(profiles[0].requireSan).toBe(true);
  });

  it('submits a new CSR enrollment request', async () => {
    const mockResponse = {
      requestId: 101,
      requesterId: 5,
      requesterUsername: 'end_entity_1',
      profileCode: 'TLS_SERVER',
      profileName: 'Internal TLS Server Certificate',
      status: 'PENDING_REVIEW',
      subjectDn: 'CN=service.internal.corp, O=Acme, C=US',
      sanEntries: { dns: ['service.internal.corp'], ip: [], email: [], uri: [] },
      rawSanEntries: ['DNS:service.internal.corp'],
      csrPem: '-----BEGIN CERTIFICATE REQUEST-----\nMII...',
      keyAlgorithm: 'RSA',
      keyLength: 2048,
      fingerprint: '3a7b8c...',
      validityDays: 365,
      requiresDualApproval: false,
      submittedAt: '2026-10-20T12:00:00Z',
      updatedAt: '2026-10-20T12:00:00Z',
      approvals: [],
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await enrollmentApi.submitEnrollment({
      profileCode: 'TLS_SERVER',
      validityDays: 365,
      csrPem: '-----BEGIN CERTIFICATE REQUEST-----\nMII...',
    });

    expect(apiClient.post).toHaveBeenCalledWith('/enrollment/requests', {
      profileCode: 'TLS_SERVER',
      validityDays: 365,
      csrPem: '-----BEGIN CERTIFICATE REQUEST-----\nMII...',
    });
    expect(result.requestId).toBe(101);
    expect(result.status).toBe('PENDING_REVIEW');
  });

  it('queries RA review queue with status filter', async () => {
    const mockPagedResponse = {
      content: [
        {
          requestId: 101,
          requesterId: 5,
          requesterUsername: 'end_entity_1',
          profileCode: 'TLS_SERVER',
          profileName: 'Internal TLS Server Certificate',
          status: 'PENDING_REVIEW',
          subjectDn: 'CN=service.internal.corp, O=Acme, C=US',
          sanEntries: { dns: ['service.internal.corp'], ip: [], email: [], uri: [] },
          rawSanEntries: ['DNS:service.internal.corp'],
          csrPem: '-----BEGIN CERTIFICATE REQUEST-----\nMII...',
          keyAlgorithm: 'RSA',
          keyLength: 2048,
          fingerprint: '3a7b8c...',
          validityDays: 365,
          requiresDualApproval: false,
          submittedAt: '2026-10-20T12:00:00Z',
          updatedAt: '2026-10-20T12:00:00Z',
          approvals: [],
        }
      ],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      isLast: true,
    };

    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockPagedResponse });

    const result = await enrollmentApi.getRequests({ status: 'PENDING_REVIEW', page: 0, size: 20 });
    expect(apiClient.get).toHaveBeenCalledWith('/enrollment/requests', {
      params: { status: 'PENDING_REVIEW', page: 0, size: 20 }
    });
    expect(result.content).toHaveLength(1);
    expect(result.content[0].status).toBe('PENDING_REVIEW');
  });

  it('submits RA approval with reviewer comment', async () => {
    const mockApproved = {
      requestId: 101,
      requesterId: 5,
      requesterUsername: 'end_entity_1',
      profileCode: 'TLS_SERVER',
      profileName: 'Internal TLS Server Certificate',
      status: 'APPROVED',
      subjectDn: 'CN=service.internal.corp, O=Acme, C=US',
      sanEntries: { dns: ['service.internal.corp'], ip: [], email: [], uri: [] },
      rawSanEntries: ['DNS:service.internal.corp'],
      csrPem: '-----BEGIN CERTIFICATE REQUEST-----\nMII...',
      keyAlgorithm: 'RSA',
      keyLength: 2048,
      fingerprint: '3a7b8c...',
      validityDays: 365,
      requiresDualApproval: false,
      submittedAt: '2026-10-20T12:00:00Z',
      updatedAt: '2026-10-20T12:05:00Z',
      approvals: [
        {
          id: 1,
          reviewerId: 2,
          reviewerUsername: 'ra_operator',
          action: 'APPROVED',
          reviewStage: 'INITIAL_REVIEW',
          reviewerComment: 'Identity verified against enterprise asset inventory.',
          clientIp: '127.0.0.1',
          reviewedAt: '2026-10-20T12:05:00Z',
        }
      ],
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockApproved });

    const result = await enrollmentApi.approveRequest(101, {
      comment: 'Identity verified against enterprise asset inventory.',
    });

    expect(apiClient.post).toHaveBeenCalledWith('/ra/requests/101/approve', {
      comment: 'Identity verified against enterprise asset inventory.',
    });
    expect(result.status).toBe('APPROVED');
    expect(result.approvals).toHaveLength(1);
  });

  it('submits RA rejection with mandatory reason and explanation', async () => {
    const mockRejected = {
      requestId: 102,
      requesterId: 5,
      requesterUsername: 'end_entity_1',
      profileCode: 'TLS_SERVER',
      profileName: 'Internal TLS Server Certificate',
      status: 'REJECTED',
      subjectDn: 'CN=unauthorized.internal.corp, O=Acme, C=US',
      sanEntries: { dns: [], ip: [], email: [], uri: [] },
      rawSanEntries: [],
      csrPem: '-----BEGIN CERTIFICATE REQUEST-----\nMII...',
      keyAlgorithm: 'RSA',
      keyLength: 2048,
      fingerprint: '3a7b8c...',
      validityDays: 365,
      rejectionReason: 'INSUFFICIENT_DOMAIN_AUTHORITY',
      rejectionComment: 'Requester has no registered ownership for unauthorized.internal.corp.',
      requiresDualApproval: false,
      submittedAt: '2026-10-20T12:00:00Z',
      updatedAt: '2026-10-20T12:10:00Z',
      approvals: [
        {
          id: 2,
          reviewerId: 2,
          reviewerUsername: 'ra_operator',
          action: 'REJECTED',
          reviewStage: 'INITIAL_REVIEW',
          reviewerComment: 'INSUFFICIENT_DOMAIN_AUTHORITY: Requester has no registered ownership for unauthorized.internal.corp.',
          clientIp: '127.0.0.1',
          reviewedAt: '2026-10-20T12:10:00Z',
        }
      ],
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockRejected });

    const result = await enrollmentApi.rejectRequest(102, {
      rejectionReason: 'INSUFFICIENT_DOMAIN_AUTHORITY',
      comment: 'Requester has no registered ownership for unauthorized.internal.corp.',
    });

    expect(apiClient.post).toHaveBeenCalledWith('/ra/requests/102/reject', {
      rejectionReason: 'INSUFFICIENT_DOMAIN_AUTHORITY',
      comment: 'Requester has no registered ownership for unauthorized.internal.corp.',
    });
    expect(result.status).toBe('REJECTED');
    expect(result.rejectionReason).toBe('INSUFFICIENT_DOMAIN_AUTHORITY');
  });

  it('cancels pending enrollment request by requester', async () => {
    const mockCancelled = {
      requestId: 103,
      requesterId: 5,
      requesterUsername: 'end_entity_1',
      profileCode: 'TLS_SERVER',
      profileName: 'Internal TLS Server Certificate',
      status: 'CANCELLED',
      subjectDn: 'CN=cancel.internal.corp, O=Acme, C=US',
      sanEntries: { dns: [], ip: [], email: [], uri: [] },
      rawSanEntries: [],
      csrPem: '-----BEGIN CERTIFICATE REQUEST-----\nMII...',
      keyAlgorithm: 'RSA',
      keyLength: 2048,
      fingerprint: '3a7b8c...',
      validityDays: 365,
      requiresDualApproval: false,
      submittedAt: '2026-10-20T12:00:00Z',
      updatedAt: '2026-10-20T12:15:00Z',
      approvals: [],
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockCancelled });

    const result = await enrollmentApi.cancelRequest(103);
    expect(apiClient.post).toHaveBeenCalledWith('/enrollment/requests/103/cancel');
    expect(result.status).toBe('CANCELLED');
  });
});
