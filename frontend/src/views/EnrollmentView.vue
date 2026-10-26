<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import {
  enrollmentApi,
  type CertificateProfile,
  type EnrollmentRequest,
  type RequestStatus
} from '../api/enrollment';

// Tabs
const activeTab = ref<'new' | 'history'>('new');

// State
const profiles = ref<CertificateProfile[]>([]);
const myRequests = ref<EnrollmentRequest[]>([]);
const isLoadingProfiles = ref(false);
const isLoadingRequests = ref(false);
const isSubmitting = ref(false);
const submitSuccess = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

// Form State
const selectedProfileCode = ref('');
const validityDays = ref<number | undefined>(undefined);
const csrPem = ref('');
const submittedResult = ref<EnrollmentRequest | null>(null);

// Detail Modal
const selectedRequest = ref<EnrollmentRequest | null>(null);
const showDetailModal = ref(false);
const isCancelling = ref(false);

const selectedProfile = computed(() => {
  return profiles.value.find((p) => p.profileCode === selectedProfileCode.value);
});

onMounted(async () => {
  await fetchProfiles();
  await fetchMyRequests();
});

async function fetchProfiles() {
  isLoadingProfiles.value = true;
  try {
    profiles.value = await enrollmentApi.getProfiles();
    if (profiles.value.length > 0 && !selectedProfileCode.value) {
      selectProfile(profiles.value[0]);
    }
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || 'Failed to load certificate profiles.';
  } finally {
    isLoadingProfiles.value = false;
  }
}

function selectProfile(profile: CertificateProfile) {
  selectedProfileCode.value = profile.profileCode;
  validityDays.value = profile.defaultValidityDays;
}

async function fetchMyRequests() {
  isLoadingRequests.value = true;
  try {
    const res = await enrollmentApi.getRequests({ page: 0, size: 50, sort: 'id,desc' });
    myRequests.value = res.content;
  } catch (err: any) {
    console.error('Failed to fetch requests:', err);
  } finally {
    isLoadingRequests.value = false;
  }
}

function handleFileUpload(event: Event) {
  const target = event.target as HTMLInputElement;
  if (!target.files || target.files.length === 0) return;
  const file = target.files[0];
  const reader = new FileReader();
  reader.onload = (e) => {
    csrPem.value = (e.target?.result as string) || '';
  };
  reader.readAsText(file);
}

async function handleSubmit() {
  errorMessage.value = '';
  successMessage.value = '';

  if (!selectedProfileCode.value) {
    errorMessage.value = 'Please select a certificate profile.';
    return;
  }

  const trimmedCsr = csrPem.value.trim();
  if (!trimmedCsr) {
    errorMessage.value = 'Please paste or upload a valid PKCS#10 CSR.';
    return;
  }

  if (!trimmedCsr.includes('-----BEGIN CERTIFICATE REQUEST-----') &&
      !trimmedCsr.includes('-----BEGIN NEW CERTIFICATE REQUEST-----')) {
    errorMessage.value = 'Invalid CSR format. CSR must begin with -----BEGIN CERTIFICATE REQUEST-----.';
    return;
  }

  isSubmitting.value = true;
  try {
    const res = await enrollmentApi.submitEnrollment({
      profileCode: selectedProfileCode.value,
      validityDays: validityDays.value,
      csrPem: trimmedCsr
    });
    submittedResult.value = res;
    submitSuccess.value = true;
    successMessage.value = `Enrollment request #${res.requestId} submitted successfully.`;
    csrPem.value = '';
    await fetchMyRequests();
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || err.response?.data?.message || 'Enrollment request submission failed.';
  } finally {
    isSubmitting.value = false;
  }
}

function viewRequestDetails(req: EnrollmentRequest) {
  selectedRequest.value = req;
  showDetailModal.value = true;
}

async function cancelRequest(id: number) {
  if (!confirm(`Are you sure you want to cancel enrollment request #${id}?`)) return;
  isCancelling.value = true;
  try {
    const updated = await enrollmentApi.cancelRequest(id);
    if (selectedRequest.value && selectedRequest.value.requestId === id) {
      selectedRequest.value = updated;
    }
    await fetchMyRequests();
    successMessage.value = `Request #${id} cancelled successfully.`;
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || 'Failed to cancel enrollment request.';
  } finally {
    isCancelling.value = false;
  }
}

function getStatusBadgeClass(status: RequestStatus) {
  switch (status) {
    case 'PENDING_REVIEW':
      return 'badge-pending';
    case 'PENDING_SECOND_APPROVAL':
      return 'badge-dual';
    case 'APPROVED':
    case 'ISSUED':
      return 'badge-approved';
    case 'REJECTED':
      return 'badge-rejected';
    case 'CANCELLED':
      return 'badge-cancelled';
    default:
      return 'badge-default';
  }
}

function formatDate(iso: string) {
  if (!iso) return '-';
  return new Date(iso).toLocaleString();
}
</script>

<template>
  <div class="enrollment-container">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h1 class="page-title">Certificate Enrollment</h1>
        <p class="page-subtitle">Submit PKCS#10 Certificate Signing Requests (CSR) and track issuance lifecycle.</p>
      </div>

      <!-- Tab Switcher -->
      <div class="tab-controls">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'new' }"
          @click="activeTab = 'new'"
          id="btn-tab-new-request"
        >
          New Request
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'history' }"
          @click="activeTab = 'history'"
          id="btn-tab-my-requests"
        >
          My Requests ({{ myRequests.length }})
        </button>
      </div>
    </div>

    <!-- Alert Messages -->
    <div v-if="errorMessage" class="alert alert-danger" id="enrollment-error-alert">
      <span>{{ errorMessage }}</span>
      <button class="alert-close" @click="errorMessage = ''">&times;</button>
    </div>

    <div v-if="successMessage" class="alert alert-success" id="enrollment-success-alert">
      <span>{{ successMessage }}</span>
      <button class="alert-close" @click="successMessage = ''">&times;</button>
    </div>

    <!-- TAB 1: NEW ENROLLMENT REQUEST -->
    <div v-if="activeTab === 'new'" class="tab-content">
      <div class="grid-layout">
        <!-- Form Column -->
        <div class="card form-card">
          <h2 class="card-title">CSR Submission</h2>
          <form @submit.prevent="handleSubmit" class="form-body">
            <!-- Profile Selection -->
            <div class="form-group">
              <label for="profile-select" class="form-label">Certificate Profile <span class="required">*</span></label>
              <select
                id="profile-select"
                v-model="selectedProfileCode"
                class="form-control"
                @change="selectedProfile && (validityDays = selectedProfile.defaultValidityDays)"
                required
              >
                <option value="" disabled>-- Select a Profile --</option>
                <option v-for="p in profiles" :key="p.id" :value="p.profileCode">
                  {{ p.profileName }} ({{ p.profileCode }})
                </option>
              </select>
            </div>

            <!-- Validity Duration -->
            <div class="form-group">
              <label for="validity-input" class="form-label">
                Requested Validity (Days)
                <span v-if="selectedProfile" class="hint-label">(Max: {{ selectedProfile.maxValidityDays }} days)</span>
              </label>
              <input
                id="validity-input"
                type="number"
                v-model.number="validityDays"
                :max="selectedProfile?.maxValidityDays || 365"
                min="1"
                class="form-control"
                required
              />
            </div>

            <!-- CSR Input -->
            <div class="form-group">
              <div class="label-row">
                <label for="csr-textarea" class="form-label">PKCS#10 CSR (PEM Format) <span class="required">*</span></label>
                <label class="file-upload-label" for="csr-file-input">
                  Upload .csr / .pem
                  <input
                    id="csr-file-input"
                    type="file"
                    accept=".csr,.pem,.txt"
                    class="hidden-file-input"
                    @change="handleFileUpload"
                  />
                </label>
              </div>
              <textarea
                id="csr-textarea"
                v-model="csrPem"
                class="form-control font-mono"
                rows="8"
                placeholder="-----BEGIN CERTIFICATE REQUEST-----&#10;MIICvDCCAaQCAQAwdzELMAkGA1UEBhMCVVMx...&#10;-----END CERTIFICATE REQUEST-----"
                required
              ></textarea>
              <span class="field-hint">Proof-of-possession signature will be verified against the embedded public key.</span>
            </div>

            <!-- Actions -->
            <div class="form-actions">
              <button
                type="submit"
                class="btn btn-primary"
                :disabled="isSubmitting"
                id="btn-submit-enrollment"
              >
                <span v-if="isSubmitting" class="spinner"></span>
                <span>{{ isSubmitting ? 'Verifying & Submitting...' : 'Submit Request for RA Review' }}</span>
              </button>
            </div>
          </form>
        </div>

        <!-- Policy / Profile Details Card -->
        <div class="card policy-card">
          <h2 class="card-title">Profile Policy Constraints</h2>
          <div v-if="selectedProfile" class="policy-details">
            <div class="policy-header">
              <div class="badge-profile-type">{{ selectedProfile.profileType }}</div>
              <div v-if="selectedProfile.requiresDualApproval" class="badge-dual-req">Dual-Approval Required</div>
            </div>

            <div class="policy-item">
              <span class="policy-key">Allowed Key Algorithms:</span>
              <div class="key-tags">
                <span v-for="k in selectedProfile.allowedKeyTypes" :key="k" class="tag tag-key">{{ k }}</span>
              </div>
            </div>

            <div class="policy-item">
              <span class="policy-key">Key Usages:</span>
              <div class="key-tags">
                <span v-for="ku in selectedProfile.keyUsages" :key="ku" class="tag tag-usage">{{ ku }}</span>
              </div>
            </div>

            <div class="policy-item">
              <span class="policy-key">Extended Key Usages (EKU):</span>
              <div class="key-tags">
                <span v-for="eku in selectedProfile.extendedKeyUsages" :key="eku" class="tag tag-eku">{{ eku }}</span>
              </div>
            </div>

            <div class="policy-item">
              <span class="policy-key">SAN Requirement:</span>
              <span class="policy-val">{{ selectedProfile.requireSan ? 'Mandatory (At least 1 SAN)' : 'Optional' }}</span>
            </div>

            <div class="policy-item">
              <span class="policy-key">Wildcard Names (*.domain):</span>
              <span class="policy-val" :class="selectedProfile.allowWildcards ? 'text-success' : 'text-danger'">
                {{ selectedProfile.allowWildcards ? 'Permitted' : 'Strictly Prohibited' }}
              </span>
            </div>

            <div class="policy-note">
              <strong>Registration Authority Policy:</strong>
              Requests under this profile will enter the RA queue. Approval is prohibited if you are the requester (Separation of Duties).
            </div>
          </div>
          <div v-else class="empty-state">
            <span>Select a profile to view constraints.</span>
          </div>
        </div>
      </div>

      <!-- Submission Result Modal / Box -->
      <div v-if="submitSuccess && submittedResult" class="card result-card" id="submission-result-card">
        <div class="result-header">
          <svg class="result-icon text-success" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
          </svg>
          <div>
            <h3>Enrollment Request #{{ submittedResult.requestId }} Created</h3>
            <p class="text-secondary">Signature cryptographically verified. Status is now <strong class="text-warning">{{ submittedResult.status }}</strong>.</p>
          </div>
        </div>

        <div class="result-grid">
          <div><span class="text-muted">Subject DN:</span> <code>{{ submittedResult.subjectDn }}</code></div>
          <div><span class="text-muted">Algorithm:</span> <code>{{ submittedResult.keyAlgorithm }}-{{ submittedResult.keyLength }}</code></div>
          <div><span class="text-muted">Fingerprint:</span> <code class="break-all">{{ submittedResult.fingerprint }}</code></div>
          <div><span class="text-muted">Validity:</span> {{ submittedResult.validityDays }} days</div>
          <div v-if="submittedResult.rawSanEntries && submittedResult.rawSanEntries.length > 0">
            <span class="text-muted">SANs:</span>
            <span v-for="s in submittedResult.rawSanEntries" :key="s" class="tag tag-san">{{ s }}</span>
          </div>
        </div>

        <div class="result-actions">
          <button class="btn btn-secondary" @click="activeTab = 'history'">View in My Requests</button>
          <button class="btn btn-outline" @click="submitSuccess = false">Submit Another</button>
        </div>
      </div>
    </div>

    <!-- TAB 2: REQUEST HISTORY -->
    <div v-if="activeTab === 'history'" class="tab-content">
      <div class="card table-card">
        <div class="table-header">
          <h2 class="card-title">My Submitted Enrollment Requests</h2>
          <button class="btn btn-sm btn-outline" @click="fetchMyRequests" :disabled="isLoadingRequests">
            Refresh
          </button>
        </div>

        <div v-if="isLoadingRequests" class="loading-state">
          <div class="spinner"></div>
          <span>Loading request history...</span>
        </div>

        <div v-else-if="myRequests.length === 0" class="empty-state">
          <p>No enrollment requests found. Submit a request using the "New Request" tab.</p>
        </div>

        <div v-else class="table-responsive">
          <table class="data-table" id="my-requests-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Profile</th>
                <th>Subject DN</th>
                <th>Key</th>
                <th>Status</th>
                <th>Submitted</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="req in myRequests" :key="req.requestId">
                <td class="font-mono font-bold">#{{ req.requestId }}</td>
                <td><span class="tag tag-profile">{{ req.profileCode }}</span></td>
                <td class="cell-dn" :title="req.subjectDn">{{ req.subjectDn }}</td>
                <td><span class="tag tag-key">{{ req.keyAlgorithm }}-{{ req.keyLength }}</span></td>
                <td>
                  <span class="badge" :class="getStatusBadgeClass(req.status)">
                    {{ req.status.replace(/_/g, ' ') }}
                  </span>
                </td>
                <td class="text-secondary text-sm">{{ formatDate(req.submittedAt) }}</td>
                <td class="cell-actions">
                  <button class="btn-icon" @click="viewRequestDetails(req)" title="View Details">
                    <svg viewBox="0 0 20 20" fill="currentColor">
                      <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
                      <path fill-rule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clip-rule="evenodd" />
                    </svg>
                  </button>
                  <button
                    v-if="req.status === 'PENDING_REVIEW' || req.status === 'PENDING_SECOND_APPROVAL'"
                    class="btn-icon text-danger"
                    @click="cancelRequest(req.requestId)"
                    title="Cancel Request"
                    :disabled="isCancelling"
                  >
                    <svg viewBox="0 0 20 20" fill="currentColor">
                      <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
                    </svg>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- DETAIL MODAL -->
    <div v-if="showDetailModal && selectedRequest" class="modal-backdrop" @click.self="showDetailModal = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3>Request #{{ selectedRequest.requestId }} Details</h3>
          <button class="modal-close" @click="showDetailModal = false">&times;</button>
        </div>

        <div class="modal-body">
          <div class="detail-row">
            <span class="detail-key">Current Status:</span>
            <span class="badge" :class="getStatusBadgeClass(selectedRequest.status)">
              {{ selectedRequest.status.replace(/_/g, ' ') }}
            </span>
          </div>

          <div class="detail-row">
            <span class="detail-key">Profile:</span>
            <span>{{ selectedRequest.profileName }} (<code>{{ selectedRequest.profileCode }}</code>)</span>
          </div>

          <div class="detail-row">
            <span class="detail-key">Subject DN:</span>
            <code class="break-all">{{ selectedRequest.subjectDn }}</code>
          </div>

          <div class="detail-row">
            <span class="detail-key">Public Key Spec:</span>
            <span>{{ selectedRequest.keyAlgorithm }} ({{ selectedRequest.keyLength }} bits)</span>
          </div>

          <div class="detail-row">
            <span class="detail-key">Public Key Fingerprint (SHA-256):</span>
            <code class="break-all text-xs font-mono">{{ selectedRequest.fingerprint }}</code>
          </div>

          <div class="detail-row">
            <span class="detail-key">Validity:</span>
            <span>{{ selectedRequest.validityDays }} Days</span>
          </div>

          <div class="detail-row" v-if="selectedRequest.rawSanEntries && selectedRequest.rawSanEntries.length > 0">
            <span class="detail-key">Subject Alternative Names:</span>
            <div class="key-tags">
              <span v-for="s in selectedRequest.rawSanEntries" :key="s" class="tag tag-san">{{ s }}</span>
            </div>
          </div>

          <!-- Rejection details if rejected -->
          <div v-if="selectedRequest.status === 'REJECTED'" class="rejection-box">
            <strong>Rejection Reason:</strong> {{ selectedRequest.rejectionReason }}
            <p v-if="selectedRequest.rejectionComment" class="text-sm mt-1">
              <em>Note:</em> {{ selectedRequest.rejectionComment }}
            </p>
          </div>

          <!-- Approval History -->
          <div class="approvals-section" v-if="selectedRequest.approvals && selectedRequest.approvals.length > 0">
            <h4>Review & Approval Audit Trail</h4>
            <div class="timeline">
              <div v-for="app in selectedRequest.approvals" :key="app.id" class="timeline-item">
                <div class="timeline-badge" :class="app.action === 'APPROVED' ? 'bg-success' : 'bg-danger'"></div>
                <div class="timeline-content">
                  <div class="timeline-title">
                    <strong>{{ app.action }}</strong> at stage <code>{{ app.reviewStage }}</code>
                    by <em>{{ app.reviewerUsername }}</em>
                  </div>
                  <div class="timeline-meta text-xs text-secondary">
                    {{ formatDate(app.reviewedAt) }} (IP: {{ app.clientIp }})
                  </div>
                  <div v-if="app.reviewerComment" class="timeline-comment text-sm">
                    "{{ app.reviewerComment }}"
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button
            v-if="selectedRequest.status === 'PENDING_REVIEW' || selectedRequest.status === 'PENDING_SECOND_APPROVAL'"
            class="btn btn-danger"
            @click="cancelRequest(selectedRequest.requestId)"
            :disabled="isCancelling"
          >
            Cancel Request
          </button>
          <button class="btn btn-secondary" @click="showDetailModal = false">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.enrollment-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.page-subtitle {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.tab-controls {
  display: flex;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0.25rem;
  gap: 0.25rem;
}

.tab-btn {
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tab-btn.active {
  background: var(--primary);
  color: #ffffff;
  font-weight: 600;
}

.grid-layout {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 1.5rem;
}

@media (max-width: 992px) {
  .grid-layout {
    grid-template-columns: 1fr;
  }
}

.card {
  background: var(--bg-card);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 1.5rem;
  box-shadow: var(--shadow-md);
}

.card-title {
  font-size: 1.125rem;
  font-weight: 600;
  margin-bottom: 1.25rem;
}

.form-body {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.form-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
}

.label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-upload-label {
  font-size: 0.75rem;
  color: var(--primary);
  cursor: pointer;
  text-decoration: underline;
}

.hidden-file-input {
  display: none;
}

.required {
  color: var(--danger);
}

.hint-label {
  font-size: 0.75rem;
  color: var(--text-muted);
  font-weight: 400;
}

.field-hint {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.form-control {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0.625rem 0.875rem;
  color: var(--text-primary);
  font-size: 0.875rem;
  outline: none;
  transition: border-color var(--transition-fast);
}

.form-control:focus {
  border-color: var(--primary);
}

.font-mono {
  font-family: var(--font-mono);
  font-size: 0.8125rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.625rem 1.25rem;
  border-radius: var(--radius-md);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: all var(--transition-fast);
}

.btn-primary {
  background: var(--primary);
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--primary-hover);
}

.btn-secondary {
  background: var(--bg-surface);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.btn-outline {
  background: transparent;
  color: var(--primary);
  border: 1px solid var(--primary);
}

.btn-danger {
  background: var(--danger);
  color: #ffffff;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-icon {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-icon svg {
  width: 1.125rem;
  height: 1.125rem;
}

.btn-icon:hover {
  color: var(--text-primary);
  background: var(--bg-card-hover);
}

.policy-details {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.policy-header {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.badge-profile-type {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.25rem 0.5rem;
  background: var(--primary-light);
  color: var(--primary);
  border-radius: var(--radius-sm);
}

.badge-dual-req {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.25rem 0.5rem;
  background: rgba(245, 158, 11, 0.15);
  color: var(--warning);
  border-radius: var(--radius-sm);
}

.policy-item {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}

.policy-key {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.policy-val {
  font-size: 0.875rem;
  font-weight: 500;
}

.key-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.tag {
  font-size: 0.75rem;
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
}

.tag-key {
  background: rgba(139, 92, 246, 0.15);
  color: #a78bfa;
}

.tag-usage {
  background: rgba(6, 182, 212, 0.15);
  color: #22d3ee;
}

.tag-eku {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
}

.tag-san {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
}

.tag-profile {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  font-weight: 600;
}

.policy-note {
  font-size: 0.75rem;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.03);
  padding: 0.75rem;
  border-radius: var(--radius-md);
  border-left: 3px solid var(--warning);
}

.result-card {
  margin-top: 1.5rem;
  border-color: rgba(16, 185, 129, 0.3);
}

.result-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.result-icon {
  width: 2rem;
  height: 2rem;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 0.75rem;
  font-size: 0.875rem;
  margin-bottom: 1.25rem;
}

.result-actions {
  display: flex;
  gap: 0.75rem;
}

/* Badges */
.badge {
  display: inline-block;
  padding: 0.25rem 0.625rem;
  border-radius: var(--radius-full);
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}

.badge-pending {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.badge-dual {
  background: rgba(139, 92, 246, 0.15);
  color: #a78bfa;
}

.badge-approved {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.badge-rejected {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.badge-cancelled {
  background: rgba(107, 114, 128, 0.15);
  color: #9ca3af;
}

/* Table */
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.data-table th, .data-table td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
}

.data-table th {
  color: var(--text-secondary);
  font-weight: 600;
  font-size: 0.75rem;
  text-transform: uppercase;
}

.cell-dn {
  max-width: 250px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Modal */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
}

.modal-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

.modal-close {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: 1.5rem;
  cursor: pointer;
}

.modal-body {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1.25rem 1.5rem;
  border-top: 1px solid var(--border-color);
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.875rem;
}

.detail-key {
  font-weight: 600;
  color: var(--text-secondary);
  font-size: 0.75rem;
  text-transform: uppercase;
}

.rejection-box {
  background: rgba(239, 68, 68, 0.1);
  border-left: 3px solid var(--danger);
  padding: 0.75rem;
  border-radius: var(--radius-sm);
  color: #fca5a5;
  font-size: 0.875rem;
}

.approvals-section h4 {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  position: relative;
  padding-left: 1.25rem;
  border-left: 2px solid var(--border-color);
}

.timeline-item {
  position: relative;
}

.timeline-badge {
  position: absolute;
  left: -1.65rem;
  top: 0.25rem;
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 50%;
}

.bg-success { background: var(--success); }
.bg-danger { background: var(--danger); }

.timeline-comment {
  font-style: italic;
  color: var(--text-secondary);
  margin-top: 0.25rem;
}

.alert {
  padding: 0.75rem 1rem;
  border-radius: var(--radius-md);
  font-size: 0.875rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.alert-danger {
  background: var(--danger-bg);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #fca5a5;
}

.alert-success {
  background: var(--success-bg);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: #6ee7b7;
}

.alert-close {
  background: transparent;
  border: none;
  color: inherit;
  font-size: 1.25rem;
  cursor: pointer;
}

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.break-all {
  word-break: break-all;
}
</style>
