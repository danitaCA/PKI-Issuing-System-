<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';
import {
  certificatesApi,
  type CertificateResponse,
  type CertificateStatus,
  type CaAuthorityResponse
} from '../api/certificates';

const authStore = useAuthStore();

// View State
const activeTab = ref<'certificates' | 'ca_enclave'>('certificates');
const isLoading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

// Certificates Data
const certificates = ref<CertificateResponse[]>([]);
const totalElements = ref(0);
const totalPages = ref(0);
const currentPage = ref(0);
const pageSize = ref(10);

// Filters
const filterStatus = ref<CertificateStatus | ''>('');
const filterCommonName = ref('');
const filterSerialNumber = ref('');

// CA Authorities Data
const caAuthorities = ref<CaAuthorityResponse[]>([]);
const isCaLoading = ref(false);

// Inspection Modal
const selectedCert = ref<CertificateResponse | null>(null);
const showInspectModal = ref(false);
const inspectActiveTab = ref<'summary' | 'pem' | 'chain'>('summary');
const copyFeedback = ref('');

// Computed Permissions
const isEndEntityOnly = computed(() => {
  return authStore.hasRole('ROLE_END_ENTITY') && !authStore.hasAnyRole([
    'ROLE_RA_OPERATOR',
    'ROLE_CA_ADMIN',
    'ROLE_SECURITY_OFFICER',
    'ROLE_AUDITOR'
  ]);
});

async function fetchCertificates(page = 0) {
  isLoading.value = true;
  errorMessage.value = '';
  currentPage.value = page;

  try {
    if (isEndEntityOnly.value) {
      const res = await certificatesApi.getMyCertificates({
        page: currentPage.value,
        size: pageSize.value
      });
      certificates.value = res.content || [];
      totalElements.value = res.totalElements || 0;
      totalPages.value = res.totalPages || 0;
    } else {
      const res = await certificatesApi.searchCertificates({
        status: filterStatus.value ? (filterStatus.value as CertificateStatus) : undefined,
        commonName: filterCommonName.value.trim() || undefined,
        serialNumber: filterSerialNumber.value.trim() || undefined,
        page: currentPage.value,
        size: pageSize.value
      });
      certificates.value = res.content || [];
      totalElements.value = res.totalElements || 0;
      totalPages.value = res.totalPages || 0;
    }
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || err.response?.data?.message || 'Failed to fetch certificates.';
  } finally {
    isLoading.value = false;
  }
}

async function fetchCaAuthorities() {
  isCaLoading.value = true;
  try {
    caAuthorities.value = await certificatesApi.getCaAuthorities();
  } catch (err: any) {
    // If user lacks admin/officer access to CA endpoint, fail gracefully
    caAuthorities.value = [];
  } finally {
    isCaLoading.value = false;
  }
}

function openInspectModal(cert: CertificateResponse) {
  selectedCert.value = cert;
  inspectActiveTab.value = 'summary';
  copyFeedback.value = '';
  showInspectModal.value = true;
}

async function downloadCert(serialNumber: string, format: 'pem' | 'der' | 'p7b') {
  try {
    const blob = await certificatesApi.downloadCertificate(serialNumber, format);
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const ext = format === 'pem' ? 'crt' : format;
    a.download = `${serialNumber}.${ext}`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  } catch (err: any) {
    errorMessage.value = 'Failed to download certificate.';
  }
}

function downloadCaCert(ca: CaAuthorityResponse, type: 'cert' | 'chain') {
  const content = type === 'cert' ? ca.certificatePem : ca.chainPem;
  if (!content) return;
  const blob = new Blob([content], { type: 'application/x-pem-file' });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${ca.commonName.replace(/\s+/g, '_')}_${type}.pem`;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
}

async function copyToClipboard(text: string, label: string) {
  try {
    await navigator.clipboard.writeText(text);
    copyFeedback.value = `${label} copied!`;
    setTimeout(() => {
      copyFeedback.value = '';
    }, 2500);
  } catch {
    copyFeedback.value = 'Copy failed';
  }
}

function getStatusBadgeClass(status: CertificateStatus) {
  switch (status) {
    case 'ACTIVE':
      return 'badge-active';
    case 'REVOKED':
      return 'badge-revoked';
    case 'EXPIRED':
      return 'badge-expired';
    case 'SUSPENDED':
      return 'badge-suspended';
    default:
      return 'badge-default';
  }
}

function formatDate(iso: string) {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

function extractCommonName(dn: string) {
  if (!dn) return '-';
  const match = dn.match(/CN=([^,]+)/i);
  return match ? match[1].trim() : dn;
}

onMounted(() => {
  fetchCertificates();
  if (!isEndEntityOnly.value) {
    fetchCaAuthorities();
  }
});
</script>

<template>
  <div class="cert-container">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h1 class="page-title">
          {{ isEndEntityOnly ? 'My Issued Certificates' : 'Certificate Repository & CA Enclave' }}
        </h1>
        <p class="page-subtitle">
          {{ isEndEntityOnly 
            ? 'View and download authoritative X.509 certificates issued for your account.' 
            : 'Authoritative X.509 certificate repository, SoftHSM key signing status, and trust chain export.' }}
        </p>
      </div>

      <!-- Tab Switcher (Operators / Admins) -->
      <div v-if="!isEndEntityOnly" class="tab-switcher">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'certificates' }"
          @click="activeTab = 'certificates'"
          id="tab-btn-certificates"
        >
          <svg class="tab-icon" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M10 2a1 1 0 00-1 1v1a1 1 0 002 0V3a1 1 0 00-1-1zM4 4h3a3 3 0 006 0h3a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V6a2 2 0 012-2zm2.5 7a1.5 1.5 0 100-3 1.5 1.5 0 000 3zm2.45 4a2.5 2.5 0 00-4.9 0h4.9zM12 9a1 1 0 100 2h3a1 1 0 100-2h-3zm0 4a1 1 0 100 2h3a1 1 0 100-2h-3z" clip-rule="evenodd" />
          </svg>
          Repository ({{ totalElements }})
        </button>

        <button
          class="tab-btn"
          :class="{ active: activeTab === 'ca_enclave' }"
          @click="activeTab = 'ca_enclave'"
          id="tab-btn-ca-enclave"
        >
          <svg class="tab-icon" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clip-rule="evenodd" />
          </svg>
          CA Hierarchy & HSM Enclave
        </button>
      </div>
    </div>

    <!-- Alert Banners -->
    <div v-if="errorMessage" class="alert alert-error">
      <svg class="alert-icon" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
      </svg>
      <span>{{ errorMessage }}</span>
      <button class="alert-close" @click="errorMessage = ''">&times;</button>
    </div>

    <div v-if="successMessage" class="alert alert-success">
      <svg class="alert-icon" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
      </svg>
      <span>{{ successMessage }}</span>
      <button class="alert-close" @click="successMessage = ''">&times;</button>
    </div>

    <!-- TAB 1: CERTIFICATES REPOSITORY -->
    <div v-if="activeTab === 'certificates'" class="tab-content">
      <!-- Search & Filters Toolbar -->
      <div v-if="!isEndEntityOnly" class="filter-toolbar card">
        <div class="filter-group">
          <label class="filter-label">Status</label>
          <select v-model="filterStatus" class="form-select" @change="fetchCertificates(0)" id="select-filter-status">
            <option value="">All Statuses</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="REVOKED">REVOKED</option>
            <option value="EXPIRED">EXPIRED</option>
            <option value="SUSPENDED">SUSPENDED</option>
          </select>
        </div>

        <div class="filter-group flex-1">
          <label class="filter-label">Common Name / Subject</label>
          <input
            v-model="filterCommonName"
            type="text"
            class="form-input"
            placeholder="e.g. api.internal.corp"
            @keyup.enter="fetchCertificates(0)"
            id="input-filter-cn"
          />
        </div>

        <div class="filter-group flex-1">
          <label class="filter-label">Serial Number</label>
          <input
            v-model="filterSerialNumber"
            type="text"
            class="form-input"
            placeholder="e.g. 7f2a1b9c8d0e3f..."
            @keyup.enter="fetchCertificates(0)"
            id="input-filter-serial"
          />
        </div>

        <div class="filter-actions">
          <button class="btn btn-primary" @click="fetchCertificates(0)" id="btn-search-certs">
            Search
          </button>
          <button
            class="btn btn-outline"
            @click="filterStatus = ''; filterCommonName = ''; filterSerialNumber = ''; fetchCertificates(0)"
          >
            Reset
          </button>
        </div>
      </div>

      <!-- Table Card -->
      <div class="card cert-table-card">
        <div v-if="isLoading" class="loading-state">
          <div class="spinner"></div>
          <p>Loading certificates from repository...</p>
        </div>

        <div v-else-if="certificates.length === 0" class="empty-state">
          <svg class="empty-icon" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M10 2a1 1 0 00-1 1v1a1 1 0 002 0V3a1 1 0 00-1-1zM4 4h3a3 3 0 006 0h3a2 2 0 012 2v9a2 2 0 01-2 2H4a2 2 0 01-2-2V6a2 2 0 012-2zm2.5 7a1.5 1.5 0 100-3 1.5 1.5 0 000 3zm2.45 4a2.5 2.5 0 00-4.9 0h4.9zM12 9a1 1 0 100 2h3a1 1 0 100-2h-3zm0 4a1 1 0 100 2h3a1 1 0 100-2h-3z" clip-rule="evenodd" />
          </svg>
          <h3>No certificates found</h3>
          <p class="text-secondary">
            {{ isEndEntityOnly 
              ? 'You have not been issued any certificates yet. Submit an enrollment request to begin.' 
              : 'No issued certificates match the specified filter criteria.' }}
          </p>
        </div>

        <div v-else class="table-responsive">
          <table class="data-table">
            <thead>
              <tr>
                <th>Serial Number</th>
                <th>Subject Common Name</th>
                <th>Profile</th>
                <th>Status</th>
                <th>Valid Until</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="cert in certificates" :key="cert.certificateId">
                <td class="cell-serial">
                  <span class="serial-pill" :title="cert.serialNumber">
                    {{ cert.serialNumber.substring(0, 16) }}...
                  </span>
                </td>
                <td class="cell-cn">
                  <div class="cn-title font-bold">{{ extractCommonName(cert.subjectDn) }}</div>
                  <div class="dn-subtitle text-xs text-secondary" :title="cert.subjectDn">{{ cert.subjectDn }}</div>
                </td>
                <td>
                  <span class="tag tag-profile">{{ cert.profileCode }}</span>
                </td>
                <td>
                  <span class="badge" :class="getStatusBadgeClass(cert.status)">
                    {{ cert.status }}
                  </span>
                </td>
                <td class="text-secondary text-sm">
                  {{ formatDate(cert.notAfter) }}
                </td>
                <td class="cell-actions">
                  <button
                    class="btn btn-xs btn-outline"
                    @click="openInspectModal(cert)"
                    title="Inspect Certificate Details"
                    :id="`btn-inspect-${cert.serialNumber}`"
                  >
                    Inspect
                  </button>
                  <button
                    class="btn btn-xs btn-primary"
                    @click="downloadCert(cert.serialNumber, 'pem')"
                    title="Download PEM (.crt)"
                    :id="`btn-dl-pem-${cert.serialNumber}`"
                  >
                    PEM
                  </button>
                  <button
                    class="btn btn-xs btn-outline"
                    @click="downloadCert(cert.serialNumber, 'der')"
                    title="Download DER (.der)"
                    :id="`btn-dl-der-${cert.serialNumber}`"
                  >
                    DER
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Pagination Footer -->
        <div v-if="totalPages > 1" class="pagination-footer">
          <span class="text-sm text-secondary">
            Showing page {{ currentPage + 1 }} of {{ totalPages }} ({{ totalElements }} total)
          </span>
          <div class="pagination-controls">
            <button
              class="btn btn-xs btn-outline"
              :disabled="currentPage === 0"
              @click="fetchCertificates(currentPage - 1)"
            >
              Previous
            </button>
            <button
              class="btn btn-xs btn-outline"
              :disabled="currentPage >= totalPages - 1"
              @click="fetchCertificates(currentPage + 1)"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- TAB 2: CA HIERARCHY & HSM ENCLAVE -->
    <div v-if="activeTab === 'ca_enclave' && !isEndEntityOnly" class="tab-content">
      <!-- Enclave Security Summary Banner -->
      <div class="card enclave-banner">
        <div class="enclave-icon-box">
          <svg viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
          </svg>
        </div>
        <div>
          <h3 class="enclave-title">PKCS#11 Cryptographic Token Boundary Active</h3>
          <p class="enclave-desc">
            The Subordinate Issuing CA signs certificates using non-extractable asymmetric keys resident inside the configured SoftHSM token (<code class="code-pill">Slot 1: PkiIssuingCaTestToken</code>). 
            Private key bytes are never exported into application memory or relational database storage.
          </p>
          <div class="token-attributes mt-2">
            <span class="attr-tag">CKA_PRIVATE = true</span>
            <span class="attr-tag">CKA_SENSITIVE = true</span>
            <span class="attr-tag">CKA_EXTRACTABLE = false</span>
            <span class="attr-tag">Failsafe: No Software Fallback</span>
          </div>
        </div>
      </div>

      <!-- CA Hierarchy Grid -->
      <div class="ca-grid mt-4">
        <div v-for="ca in caAuthorities" :key="ca.id" class="card ca-card">
          <div class="ca-header">
            <div>
              <span class="ca-type-tag">
                {{ ca.commonName.toLowerCase().includes('root') ? 'Offline Root Trust Anchor' : 'Online Subordinate Issuing CA' }}
              </span>
              <h3 class="ca-name">{{ ca.commonName }}</h3>
              <p class="ca-dn text-xs text-secondary">{{ ca.subjectDn }}</p>
            </div>
            <span class="badge" :class="ca.status === 'ACTIVE' ? 'badge-active' : 'badge-suspended'">
              {{ ca.status }}
            </span>
          </div>

          <div class="ca-body mt-3">
            <div class="ca-meta-row">
              <span class="ca-meta-label">Key Algorithm:</span>
              <span class="ca-meta-value font-mono">{{ ca.keyAlgorithm }}-{{ ca.keyLength }}</span>
            </div>
            <div class="ca-meta-row">
              <span class="ca-meta-label">Serial Number:</span>
              <span class="ca-meta-value font-mono text-xs">{{ ca.serialNumber }}</span>
            </div>
            <div class="ca-meta-row">
              <span class="ca-meta-label">Validity Window:</span>
              <span class="ca-meta-value text-xs">{{ formatDate(ca.notBefore) }} &rarr; {{ formatDate(ca.notAfter) }}</span>
            </div>
          </div>

          <div class="ca-footer mt-4">
            <button
              v-if="ca.certificatePem"
              class="btn btn-sm btn-outline flex-1"
              @click="downloadCaCert(ca, 'cert')"
            >
              Download Certificate
            </button>
            <button
              v-if="ca.chainPem"
              class="btn btn-sm btn-primary flex-1"
              @click="downloadCaCert(ca, 'chain')"
            >
              Download Full Chain
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- CERTIFICATE INSPECT MODAL -->
    <div v-if="showInspectModal && selectedCert" class="modal-backdrop" @click.self="showInspectModal = false">
      <div class="modal-card">
        <div class="modal-header">
          <div>
            <h3>Certificate #{{ selectedCert.certificateId }} Details</h3>
            <span class="badge mt-1" :class="getStatusBadgeClass(selectedCert.status)">
              {{ selectedCert.status }}
            </span>
          </div>
          <button class="modal-close" @click="showInspectModal = false">&times;</button>
        </div>

        <div class="modal-body">
          <!-- Modal Tabs -->
          <div class="inspect-nav-tabs">
            <button
              class="inspect-tab"
              :class="{ active: inspectActiveTab === 'summary' }"
              @click="inspectActiveTab = 'summary'"
            >
              Summary & Extensions
            </button>
            <button
              class="inspect-tab"
              :class="{ active: inspectActiveTab === 'pem' }"
              @click="inspectActiveTab = 'pem'"
            >
              Certificate (PEM)
            </button>
            <button
              class="inspect-tab"
              :class="{ active: inspectActiveTab === 'chain' }"
              @click="inspectActiveTab = 'chain'"
            >
              Trust Chain (PEM)
            </button>
          </div>

          <!-- Tab: Summary -->
          <div v-if="inspectActiveTab === 'summary'" class="inspect-content">
            <div class="detail-grid">
              <div class="detail-item full-width">
                <span class="detail-label">Subject DN</span>
                <span class="detail-value font-mono">{{ selectedCert.subjectDn }}</span>
              </div>

              <div class="detail-item full-width">
                <span class="detail-label">Issuer DN</span>
                <span class="detail-value font-mono">{{ selectedCert.issuerDn }}</span>
              </div>

              <div class="detail-item">
                <span class="detail-label">Serial Number</span>
                <span class="detail-value font-mono text-sm">{{ selectedCert.serialNumber }}</span>
              </div>

              <div class="detail-item">
                <span class="detail-label">Certificate Profile</span>
                <span class="detail-value">{{ selectedCert.profileCode }}</span>
              </div>

              <div class="detail-item">
                <span class="detail-label">Key Algorithm & Size</span>
                <span class="detail-value font-mono">{{ selectedCert.keyAlgorithm }}-{{ selectedCert.keyLength }}</span>
              </div>

              <div class="detail-item">
                <span class="detail-label">SHA-256 Fingerprint</span>
                <span class="detail-value font-mono text-xs">{{ selectedCert.fingerprintSha256 }}</span>
              </div>

              <div class="detail-item">
                <span class="detail-label">Not Before</span>
                <span class="detail-value">{{ formatDate(selectedCert.notBefore) }}</span>
              </div>

              <div class="detail-item">
                <span class="detail-label">Not After</span>
                <span class="detail-value">{{ formatDate(selectedCert.notAfter) }}</span>
              </div>
            </div>

            <!-- Subject Alternative Names (SANs) -->
            <div class="san-section mt-4">
              <h4 class="san-title">Subject Alternative Names (SAN)</h4>
              <div v-if="(selectedCert.sanDns && selectedCert.sanDns.length > 0) || (selectedCert.sanIps && selectedCert.sanIps.length > 0)" class="san-badges mt-2">
                <span v-for="dns in selectedCert.sanDns" :key="dns" class="tag tag-san">
                  DNS: {{ dns }}
                </span>
                <span v-for="ip in selectedCert.sanIps" :key="ip" class="tag tag-san">
                  IP: {{ ip }}
                </span>
                <span v-for="email in selectedCert.sanEmails" :key="email" class="tag tag-san">
                  Email: {{ email }}
                </span>
                <span v-for="uri in selectedCert.sanUris" :key="uri" class="tag tag-san">
                  URI: {{ uri }}
                </span>
              </div>
              <p v-else class="text-muted text-xs mt-1">No SAN entries encoded for this certificate.</p>
            </div>
          </div>

          <!-- Tab: PEM Viewer -->
          <div v-if="inspectActiveTab === 'pem'" class="inspect-content">
            <div class="pem-box">
              <div class="pem-header">
                <span>RFC 7468 PEM Format</span>
                <button class="btn btn-xs btn-outline" @click="copyToClipboard(selectedCert.certificatePem, 'PEM')">
                  Copy PEM
                </button>
              </div>
              <pre class="pem-content"><code>{{ selectedCert.certificatePem }}</code></pre>
            </div>
          </div>

          <!-- Tab: Chain Viewer -->
          <div v-if="inspectActiveTab === 'chain'" class="inspect-content">
            <div class="pem-box">
              <div class="pem-header">
                <span>Full Issuing CA Certificate Chain</span>
                <button class="btn btn-xs btn-outline" @click="copyToClipboard(selectedCert.chainPem, 'Chain')">
                  Copy Chain
                </button>
              </div>
              <pre class="pem-content"><code>{{ selectedCert.chainPem }}</code></pre>
            </div>
          </div>

          <div v-if="copyFeedback" class="copy-alert mt-2">
            {{ copyFeedback }}
          </div>
        </div>

        <div class="modal-footer">
          <div class="download-group">
            <button class="btn btn-sm btn-primary" @click="downloadCert(selectedCert.serialNumber, 'pem')">
              Download PEM (.crt)
            </button>
            <button class="btn btn-sm btn-outline" @click="downloadCert(selectedCert.serialNumber, 'der')">
              Download DER (.der)
            </button>
            <button class="btn btn-sm btn-outline" @click="downloadCert(selectedCert.serialNumber, 'p7b')">
              Download PKCS#7 (.p7b)
            </button>
          </div>
          <button class="btn btn-sm btn-outline" @click="showInspectModal = false">
            Close
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cert-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
}

.page-subtitle {
  color: var(--text-secondary);
  font-size: 0.875rem;
  margin-top: 0.25rem;
}

.tab-switcher {
  display: flex;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0.25rem;
  gap: 0.25rem;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.875rem;
  font-size: 0.8125rem;
  font-weight: 500;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  border: none;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tab-btn.active {
  background: var(--primary);
  color: white;
  font-weight: 600;
}

.tab-icon {
  width: 16px;
  height: 16px;
}

.filter-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 1rem;
  padding: 1.25rem;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.filter-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.filter-actions {
  display: flex;
  gap: 0.5rem;
}

.serial-pill {
  font-family: var(--font-mono);
  font-size: 0.8125rem;
  background: rgba(255, 255, 255, 0.05);
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  color: var(--primary);
}

.tag-profile {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.15rem 0.5rem;
  border-radius: var(--radius-sm);
  background: rgba(139, 92, 246, 0.15);
  color: #a78bfa;
}

.tag-san {
  font-size: 0.75rem;
  font-family: var(--font-mono);
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-sm);
  background: rgba(59, 130, 246, 0.12);
  color: var(--primary);
  border: 1px solid rgba(59, 130, 246, 0.25);
}

.san-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.san-title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.badge-active {
  background: var(--success-bg);
  color: var(--success);
}

.badge-revoked {
  background: var(--danger-bg);
  color: var(--danger);
}

.badge-expired {
  background: rgba(107, 114, 128, 0.15);
  color: var(--text-muted);
}

.badge-suspended {
  background: var(--warning-bg);
  color: var(--warning);
}

.enclave-banner {
  display: flex;
  gap: 1.25rem;
  align-items: flex-start;
  padding: 1.5rem;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.08) 0%, rgba(59, 130, 246, 0.08) 100%);
  border: 1px solid rgba(16, 185, 129, 0.25);
}

.enclave-icon-box {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--success-bg);
  color: var(--success);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.enclave-icon-box svg {
  width: 24px;
  height: 24px;
}

.enclave-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
}

.enclave-desc {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin-top: 0.375rem;
  line-height: 1.5;
}

.code-pill {
  font-family: var(--font-mono);
  background: rgba(255, 255, 255, 0.06);
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  font-size: 0.8125rem;
  color: #60a5fa;
}

.token-attributes {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.attr-tag {
  font-size: 0.75rem;
  font-family: var(--font-mono);
  background: rgba(255, 255, 255, 0.05);
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
}

.ca-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 1.25rem;
}

.ca-card {
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
}

.ca-type-tag {
  font-size: 0.6875rem;
  text-transform: uppercase;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--primary);
  display: block;
  margin-bottom: 0.25rem;
}

.ca-name {
  font-size: 1.125rem;
  font-weight: 600;
}

.ca-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.ca-meta-row {
  display: flex;
  justify-content: space-between;
  padding: 0.35rem 0;
  border-bottom: 1px solid var(--border-color);
  font-size: 0.8125rem;
}

.ca-meta-label {
  color: var(--text-muted);
}

.ca-meta-value {
  color: var(--text-primary);
}

.ca-footer {
  display: flex;
  gap: 0.75rem;
}

.inspect-nav-tabs {
  display: flex;
  border-bottom: 1px solid var(--border-color);
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.inspect-tab {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  padding: 0.5rem 0.25rem;
  font-size: 0.875rem;
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 500;
}

.inspect-tab.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
  font-weight: 600;
}

.pem-box {
  background: #050811;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.pem-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0.75rem;
  background: rgba(255, 255, 255, 0.03);
  border-bottom: 1px solid var(--border-color);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.pem-content {
  padding: 0.75rem;
  max-height: 280px;
  overflow-y: auto;
  font-family: var(--font-mono);
  font-size: 0.75rem;
  line-height: 1.4;
  color: #a5b4fc;
}

.copy-alert {
  font-size: 0.75rem;
  color: var(--success);
  font-weight: 600;
}

.download-group {
  display: flex;
  gap: 0.5rem;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }
  .ca-grid {
    grid-template-columns: 1fr;
  }
  .download-group {
    flex-direction: column;
  }
}
</style>
