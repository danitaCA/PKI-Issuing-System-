import { describe, it, expect, beforeEach, vi } from 'vitest';
import { certificatesApi, type CertificateResponse, type CaAuthorityResponse } from '../api/certificates';
import { apiClient } from '../api/client';

vi.mock('../api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn()
  }
}));

describe('Certificates and CA Enclave API Client Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockCertResponse: CertificateResponse = {
    certificateId: 501,
    serialNumber: '7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c',
    subjectDn: 'CN=api.internal.corp, O=Acme, C=US',
    issuerDn: 'CN=Enterprise Issuing CA 01, O=Acme, C=US',
    fingerprintSha256: 'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
    notBefore: '2026-10-21T00:00:00Z',
    notAfter: '2027-10-21T00:00:00Z',
    status: 'ACTIVE',
    profileCode: 'TLS_SERVER',
    keyAlgorithm: 'RSA',
    keyLength: 2048,
    sanDns: ['api.internal.corp'],
    sanIps: [],
    sanEmails: [],
    sanUris: [],
    issuedAt: '2026-10-21T10:00:00Z',
    requestId: 101,
    certificatePem: '-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----',
    chainPem: '-----BEGIN CERTIFICATE-----\nMIIC...\n-----END CERTIFICATE-----',
    downloadUri: '/api/v1/certificates/7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c/download'
  };

  it('searches certificates repository with filter parameters', async () => {
    const mockPagedResponse = {
      content: [mockCertResponse],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false
    };

    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockPagedResponse });

    const result = await certificatesApi.searchCertificates({
      status: 'ACTIVE',
      commonName: 'api.internal',
      page: 0,
      size: 10
    });

    expect(apiClient.get).toHaveBeenCalledWith('/certificates', {
      params: {
        status: 'ACTIVE',
        commonName: 'api.internal',
        page: 0,
        size: 10
      }
    });
    expect(result.content).toHaveLength(1);
    expect(result.content[0].serialNumber).toBe('7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c');
    expect(result.content[0].status).toBe('ACTIVE');
  });

  it('queries own certificates for End Entity', async () => {
    const mockPagedResponse = {
      content: [mockCertResponse],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false
    };

    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockPagedResponse });

    const result = await certificatesApi.getMyCertificates({ page: 0, size: 20 });
    expect(apiClient.get).toHaveBeenCalledWith('/certificates/my', {
      params: { page: 0, size: 20 }
    });
    expect(result.content[0].certificateId).toBe(501);
  });

  it('executes certificate issuance from approved enrollment request', async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: mockCertResponse });

    const issued = await certificatesApi.issueCertificate(101);
    expect(apiClient.post).toHaveBeenCalledWith('/ca/requests/101/issue');
    expect(issued.certificateId).toBe(501);
    expect(issued.serialNumber).toBe('7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c');
    expect(issued.status).toBe('ACTIVE');
  });

  it('fetches CA authorities and enclave status', async () => {
    const mockCas: CaAuthorityResponse[] = [
      {
        id: 1,
        commonName: 'Test Isolated Root CA',
        subjectDn: 'CN=Test Isolated Root CA, O=PKI Architecture Test, C=US',
        keyAlgorithm: 'RSA',
        keyLength: 4096,
        status: 'ACTIVE',
        serialNumber: '01',
        notBefore: '2026-01-01T00:00:00Z',
        notAfter: '2046-01-01T00:00:00Z',
        certificatePem: '-----BEGIN CERTIFICATE-----\nROOT...\n-----END CERTIFICATE-----'
      },
      {
        id: 2,
        commonName: 'Enterprise Issuing CA 01',
        subjectDn: 'CN=Enterprise Issuing CA 01, O=Acme, C=US',
        keyAlgorithm: 'RSA',
        keyLength: 2048,
        status: 'ACTIVE',
        serialNumber: '02',
        notBefore: '2026-01-01T00:00:00Z',
        notAfter: '2036-01-01T00:00:00Z',
        certificatePem: '-----BEGIN CERTIFICATE-----\nSUBCA...\n-----END CERTIFICATE-----'
      }
    ];

    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockCas });

    const cas = await certificatesApi.getCaAuthorities();
    expect(apiClient.get).toHaveBeenCalledWith('/ca');
    expect(cas).toHaveLength(2);
    expect(cas[0].commonName).toContain('Root');
    expect(cas[1].commonName).toContain('Issuing');
  });

  it('downloads certificate binary or PEM blob with requested format', async () => {
    const mockBlob = new Blob(['mock-cert-data'], { type: 'application/x-pem-file' });
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: mockBlob });

    const blob = await certificatesApi.downloadCertificate('7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c', 'pem');
    expect(apiClient.get).toHaveBeenCalledWith('/certificates/7f2a1b9c8d0e3f4a5b6c7d8e9f0a1b2c/download', {
      params: { format: 'pem' },
      responseType: 'blob'
    });
    expect(blob).toBeDefined();
  });
});
