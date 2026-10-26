<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useAuthStore } from '../stores/auth';
import {
  enrollmentApi,
  type EnrollmentRequest,
  type RequestStatus
} from '../api/enrollment';
import { certificatesApi } from '../api/certificates';

const authStore = useAuthStore();

// State
const requests = ref<EnrollmentRequest[]>([]);
const isLoading = ref(false);
const statusFilter = ref<RequestStatus | ''>('PENDING_REVIEW');
const errorMessage = ref('');
const successMessage = ref('');

// Review Modal State
const selectedRequest = ref<EnrollmentRequest | null>(null);
const showReviewModal = ref(false);
const reviewAction = ref<'approve' | 'reject' | null>(null);
const approvalComment = ref('');
const rejectionReason = ref('INSUFFICIENT_DOMAIN_AUTHORITY');
const rejectionComment = ref('');
const isProcessing = ref(false);

const standardRejectionReasons = [
  { value: 'INSUFFICIENT_DOMAIN_AUTHORITY', label: 'Insufficient Domain Authority / Ownership' },
  { value: 'UNAUTHORIZED_SAN_IDENTIFIER', label: 'Unauthorized Subject Alternative Name (SAN)' },
  { value: 'INADEQUATE_ORGANIZATIONAL_PROOF', label: 'Inadequate Organizational Identity Proof' },
  { value: 'CRYPTOGRAPHIC_POLICY_VIOLATION', label: 'Cryptographic Policy or Algorithm Violation' },
  { value: 'SUSPICIOUS_REQUEST_SOURCE', label: 'Suspicious Request Source / Identity Conflict' },
  { value: 'ADMINISTRATIVE_REJECTION', label: 'Administrative / Deprecated System Rejection' }
];

onMounted(async () => {
  await fetchQueue();
});

async function fetchQueue() {
  isLoading.value = true;
  errorMessage.value = '';
  try {
    const params: any = { page: 0, size: 50, sort: 'id,desc' };
    if (statusFilter.value) {
      params.status = statusFilter.value;
    }
    const res = await enrollmentApi.getRequests(params);
    requests.value = res.content;
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || 'Failed to load RA review queue.';
  } finally {
    isLoading.value = false;
  }
}

function openReview(req: EnrollmentRequest, action: 'approve' | 'reject' | null = null) {
  selectedRequest.value = req;
  reviewAction.value = action;
  approvalComment.value = '';
  rejectionReason.value = 'INSUFFICIENT_DOMAIN_AUTHORITY';
  rejectionComment.value = '';
  showReviewModal.value = true;
}

const isSelfRequest = computed(() => {
  if (!selectedRequest.value || !authStore.user) return false;
  return selectedRequest.value.requesterId === authStore.user.id;
});

const isAlreadyStage1Reviewer = computed(() => {
  if (!selectedRequest.value || !authStore.user) return false;
  return selectedRequest.value.approvals?.some(
    (app) => app.reviewerId === authStore.user?.id
  );
});

const canUserApprove = computed(() => {
  if (!selectedRequest.value || !authStore.user) return false;
  if (isSelfRequest.value) return false;

  const status = selectedRequest.value.status;
  if (status === 'PENDING_REVIEW') {
    return authStore.hasAnyRole(['ROLE_RA_OPERATOR', 'ROLE_CA_ADMIN']);
  }
  if (status === 'PENDING_SECOND_APPROVAL') {
    if (isAlreadyStage1Reviewer.value) return false;
    return authStore.hasAnyRole(['ROLE_SECURITY_OFFICER', 'ROLE_CA_ADMIN']);
  }
  return false;
});

const approvalBlockReason = computed(() => {
  if (isSelfRequest.value) {
    return 'Separation of Duties (SoD-01): You cannot approve your own enrollment request.';
  }
  if (selectedRequest.value?.status === 'PENDING_SECOND_APPROVAL' && isAlreadyStage1Reviewer.value) {
    return 'Dual-Approval Policy (SoD-03): Stage 2 approval must be performed by a different officer than Stage 1.';
  }
  if (selectedRequest.value?.status === 'PENDING_SECOND_APPROVAL' && !authStore.hasAnyRole(['ROLE_SECURITY_OFFICER', 'ROLE_CA_ADMIN'])) {
    return 'Stage 2 approval strictly requires ROLE_SECURITY_OFFICER or ROLE_CA_ADMIN.';
  }
  return '';
});

async function handleApprove() {
  if (!selectedRequest.value) return;
  isProcessing.value = true;
  errorMessage.value = '';
  try {
    const updated = await enrollmentApi.approveRequest(selectedRequest.value.requestId, {
      comment: approvalComment.value.trim() || undefined
    });
    successMessage.value = `Request #${updated.requestId} approved successfully (${updated.status}).`;
    showReviewModal.value = false;
    await fetchQueue();
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || err.response?.data?.message || 'Approval action failed.';
  } finally {
    isProcessing.value = false;
  }
}

async function handleReject() {
  if (!selectedRequest.value) return;
  if (!rejectionReason.value.trim()) {
    errorMessage.value = 'Rejection reason is required.';
    return;
  }

  isProcessing.value = true;
  errorMessage.value = '';
  try {
    const updated = await enrollmentApi.rejectRequest(selectedRequest.value.requestId, {
      rejectionReason: rejectionReason.value,
      comment: rejectionComment.value.trim() || undefined
    });
    successMessage.value = `Request #${updated.requestId} formally rejected.`;
    showReviewModal.value = false;
    await fetchQueue();
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || err.response?.data?.message || 'Rejection action failed.';
  } finally {
    isProcessing.value = false;
  }
}

async function handleIssue(req: EnrollmentRequest) {
  isProcessing.value = true;
  errorMessage.value = '';
  try {
    const cert = await certificatesApi.issueCertificate(req.requestId);
    successMessage.value = `Certificate #${cert.certificateId} successfully issued (Serial: ${cert.serialNumber}) via SoftHSM!`;
    req.status = 'ISSUED';
    if (selectedRequest.value && selectedRequest.value.requestId === req.requestId) {
      selectedRequest.value.status = 'ISSUED';
    }
  } catch (err: any) {
    errorMessage.value = err.response?.data?.detail || err.response?.data?.message || 'Issuance failed.';
  } finally {
    isProcessing.value = false;
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
  <div class="ra-queue-container">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h1 class="page-title">Registration Authority (RA) Queue</h1>
        <p class="page-subtitle">Verify applicant identity, cryptographic proof-of-possession, and policy constraints.</p>
      </div>

      <!-- Filters & Actions -->
      <div class="header-actions">
        <select v-model="statusFilter" @change="fetchQueue" class="form-control status-select" id="ra-status-filter">
          <option value="">All Statuses</option>
          <option value="PENDING_REVIEW">Pending Initial Review</option>
          <option value="PENDING_SECOND_APPROVAL">Pending Second Approval</option>
          <option value="APPROVED">Approved (Ready for Issuance)</option>
          <option value="ISSUED">Issued</option>
          <option value="REJECTED">Rejected</option>
          <option value="CANCELLED">Cancelled</option>
        </select>

        <button class="btn btn-outline" @click="fetchQueue" :disabled="isLoading" id="btn-refresh-ra-queue">
          <span v-if="isLoading" class="spinner"></span>
          <span>Refresh</span>
        </button>
      </div>
    </div>

    <!-- Alert Messages -->
    <div v-if="errorMessage" class="alert alert-danger" id="ra-error-alert">
      <span>{{ errorMessage }}</span>
      <button class="alert-close" @click="errorMessage = ''">&times;</button>
    </div>

    <div v-if="successMessage" class="alert alert-success" id="ra-success-alert">
      <span>{{ successMessage }}</span>
      <button class="alert-close" @click="successMessage = ''">&times;</button>
    </div>

    <!-- Queue Table Card -->
    <div class="card table-card">
      <div v-if="isLoading" class="loading-state">
        <div class="spinner"></div>
        <span>Loading enrollment requests...</span>
      </div>

      <div v-else-if="requests.length === 0" class="empty-state">
        <p>No enrollment requests found for the selected filter.</p>
      </div>

      <div v-else class="table-responsive">
        <table class="data-table" id="ra-requests-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Profile</th>
              <th>Requester</th>
              <th>Subject DN</th>
              <th>Key Spec</th>
              <th>Status</th>
              <th>Submitted</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="req in requests" :key="req.requestId">
              <td class="font-mono font-bold">#{{ req.requestId }}</td>
              <td>
                <span class="tag tag-profile">{{ req.profileCode }}</span>
                <span v-if="req.requiresDualApproval" class="tag-dual-dot" title="Requires Dual Approval">Dual</span>
              </td>
              <td>
                <div class="requester-cell">
                  <span class="font-medium">{{ req.requesterUsername }}</span>
                  <span v-if="authStore.user && req.requesterId === authStore.user.id" class="badge-self" title="Self-Request: Cannot approve own request">
                    Self (SoD)
                  </span>
                </div>
              </td>
              <td class="cell-dn" :title="req.subjectDn">{{ req.subjectDn }}</td>
              <td><span class="tag tag-key">{{ req.keyAlgorithm }}-{{ req.keyLength }}</span></td>
              <td>
                <span class="badge" :class="getStatusBadgeClass(req.status)">
                  {{ req.status.replace(/_/g, ' ') }}
                </span>
              </td>
              <td class="text-secondary text-sm">{{ formatDate(req.submittedAt) }}</td>
              <td class="cell-actions">
                <button
                  class="btn btn-xs btn-outline"
                  @click="openReview(req, null)"
                  title="Inspect Request"
                  :id="`btn-inspect-${req.requestId}`"
                >
                  Inspect
                </button>
                <button
                  v-if="req.status === 'PENDING_REVIEW' || req.status === 'PENDING_SECOND_APPROVAL'"
                  class="btn btn-xs btn-success"
                  @click="openReview(req, 'approve')"
                  :id="`btn-quick-approve-${req.requestId}`"
                >
                  Approve
                </button>
                <button
                  v-if="req.status === 'APPROVED' && authStore.hasAnyRole(['ROLE_RA_OPERATOR', 'ROLE_CA_ADMIN'])"
                  class="btn btn-xs btn-primary"
                  @click="handleIssue(req)"
                  :disabled="isProcessing"
                  :id="`btn-quick-issue-${req.requestId}`"
                  title="Issue Certificate via SoftHSM"
                >
                  Issue Cert
                </button>
                <button
                  v-if="req.status === 'PENDING_REVIEW' || req.status === 'PENDING_SECOND_APPROVAL'"
                  class="btn btn-xs btn-danger"
                  @click="openReview(req, 'reject')"
                  :id="`btn-quick-reject-${req.requestId}`"
                >
                  Reject
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- REVIEW / DECISION MODAL -->
    <div v-if="showReviewModal && selectedRequest" class="modal-backdrop" @click.self="showReviewModal = false">
      <div class="modal-card">
        <div class="modal-header">
          <div>
            <h3>Enrollment Request #{{ selectedRequest.requestId }} Review</h3>
            <span class="badge mt-1" :class="getStatusBadgeClass(selectedRequest.status)">
              {{ selectedRequest.status.replace(/_/g, ' ') }}
            </span>
          </div>
          <button class="modal-close" @click="showReviewModal = false">&times;</button>
        </div>

        <div class="modal-body">
          <!-- Request Metadata Grid -->
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">Requester</span>
              <span class="detail-value font-bold">
                {{ selectedRequest.requesterUsername }}
                <span v-if="isSelfRequest" class="badge-self ml-1">Your Account</span>
              </span>
            </div>

            <div class="detail-item">
              <span class="detail-label">Certificate Profile</span>
              <span class="detail-value">
                {{ selectedRequest.profileName }} (<code>{{ selectedRequest.profileCode }}</code>)
              </span>
            </div>

            <div class="detail-item full-width">
              <span class="detail-label">Subject DN</span>
              <code class="break-all">{{ selectedRequest.subjectDn }}</code>
            </div>

            <div class="detail-item">
              <span class="detail-label">Algorithm & Length</span>
              <span class="detail-value">{{ selectedRequest.keyAlgorithm }} ({{ selectedRequest.keyLength }} bits)</span>
            </div>

            <div class="detail-item">
              <span class="detail-label">Requested Validity</span>
              <span class="detail-value">{{ selectedRequest.validityDays }} Days</span>
            </div>

            <div class="detail-item full-width">
              <span class="detail-label">Public Key Fingerprint (SHA-256)</span>
              <code class="break-all text-xs">{{ selectedRequest.fingerprint }}</code>
            </div>

            <div class="detail-item full-width" v-if="selectedRequest.rawSanEntries && selectedRequest.rawSanEntries.length > 0">
              <span class="detail-label">Subject Alternative Names (SANs)</span>
              <div class="key-tags">
                <span v-for="s in selectedRequest.rawSanEntries" :key="s" class="tag tag-san">{{ s }}</span>
              </div>
            </div>
          </div>

          <!-- Existing Approval History -->
          <div v-if="selectedRequest.approvals && selectedRequest.approvals.length > 0" class="approvals-box">
            <h4>Approval History</h4>
            <div class="timeline">
              <div v-for="app in selectedRequest.approvals" :key="app.id" class="timeline-item">
                <div class="timeline-badge" :class="app.action === 'APPROVED' ? 'bg-success' : 'bg-danger'"></div>
                <div class="timeline-content">
                  <div>
                    <strong>{{ app.action }}</strong> ({{ app.reviewStage }}) by <em>{{ app.reviewerUsername }}</em>
                  </div>
                  <div class="text-xs text-secondary">{{ formatDate(app.reviewedAt) }} (IP: {{ app.clientIp }})</div>
                  <div v-if="app.reviewerComment" class="timeline-comment text-sm">"{{ app.reviewerComment }}"</div>
                </div>
              </div>
            </div>
          </div>

          <!-- Action Panels -->
          <div v-if="selectedRequest.status === 'PENDING_REVIEW' || selectedRequest.status === 'PENDING_SECOND_APPROVAL'" class="action-panel">
            <div class="action-tabs">
              <button
                class="action-tab"
                :class="{ active: reviewAction === 'approve' }"
                @click="reviewAction = 'approve'"
              >
                Approve Request
              </button>
              <button
                class="action-tab"
                :class="{ active: reviewAction === 'reject' }"
                @click="reviewAction = 'reject'"
              >
                Reject Request
              </button>
            </div>

            <!-- Approve Form -->
            <div v-if="reviewAction === 'approve'" class="action-body">
              <div v-if="!canUserApprove" class="alert alert-danger mb-3" id="sod-violation-alert">
                <strong>Action Prohibited:</strong> {{ approvalBlockReason }}
              </div>

              <div v-if="selectedRequest.requiresDualApproval && selectedRequest.status === 'PENDING_REVIEW'" class="notice-box mb-3">
                <strong>Dual-Approval Profile:</strong> This is Stage 1 approval. After your approval, request status will transition to <code>PENDING_SECOND_APPROVAL</code> for independent review by a Security Officer.
              </div>

              <div v-else-if="selectedRequest.status === 'PENDING_SECOND_APPROVAL'" class="notice-box mb-3">
                <strong>Dual-Approval Profile:</strong> This is Stage 2 approval. Completing this will transition the request to <code>APPROVED</code>.
              </div>

              <div class="form-group">
                <label for="approval-comment" class="form-label">Review Justification / Comment (Optional)</label>
                <textarea
                  id="approval-comment"
                  v-model="approvalComment"
                  class="form-control"
                  rows="3"
                  placeholder="e.g. Identity verified against enterprise asset registry."
                  :disabled="!canUserApprove"
                ></textarea>
              </div>

              <button
                class="btn btn-success mt-3"
                @click="handleApprove"
                :disabled="!canUserApprove || isProcessing"
                id="btn-confirm-approve"
              >
                <span v-if="isProcessing" class="spinner"></span>
                <span>Confirm Approval</span>
              </button>
            </div>

            <!-- Reject Form -->
            <div v-if="reviewAction === 'reject'" class="action-body">
              <div class="form-group mb-3">
                <label for="rejection-reason" class="form-label">Rejection Reason Code <span class="required">*</span></label>
                <select id="rejection-reason" v-model="rejectionReason" class="form-control" required>
                  <option v-for="r in standardRejectionReasons" :key="r.value" :value="r.value">
                    {{ r.label }}
                  </option>
                </select>
              </div>

              <div class="form-group">
                <label for="rejection-comment" class="form-label">Detailed Explanation for Requester</label>
                <textarea
                  id="rejection-comment"
                  v-model="rejectionComment"
                  class="form-control"
                  rows="3"
                  placeholder="Explain why this request is being rejected..."
                ></textarea>
              </div>

              <button
                class="btn btn-danger mt-3"
                @click="handleReject"
                :disabled="isProcessing"
                id="btn-confirm-reject"
              >
                <span v-if="isProcessing" class="spinner"></span>
                <span>Confirm Rejection</span>
              </button>
            </div>
          </div>

          <div v-else-if="selectedRequest.status === 'APPROVED' && authStore.hasAnyRole(['ROLE_RA_OPERATOR', 'ROLE_CA_ADMIN'])" class="action-panel">
            <div class="action-body">
              <div class="notice-box mb-3" style="background: rgba(59, 130, 246, 0.1); border-color: rgba(59, 130, 246, 0.3);">
                <strong>Eligible for SoftHSM Issuance:</strong> This request has received all required authoritative approvals. Issuing will trigger on-token cryptographic signing in SoftHSM and generate the X.509 certificate.
              </div>
              <button
                class="btn btn-primary"
                @click="handleIssue(selectedRequest)"
                :disabled="isProcessing"
                id="btn-modal-issue"
              >
                <span v-if="isProcessing" class="spinner"></span>
                <span>Issue Certificate via SoftHSM</span>
              </button>
            </div>
          </div>

          <div v-else-if="selectedRequest.status === 'ISSUED'" class="action-panel">
            <div class="action-body">
              <div class="alert alert-success">
                <strong>Certificate Issued:</strong> This enrollment request has been signed and issued.
              </div>
              <router-link to="/certificates" class="btn btn-outline mt-2">
                View in Certificate Repository &rarr;
              </router-link>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-secondary" @click="showReviewModal = false">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ra-queue-container {
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

.header-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.status-select {
  min-width: 200px;
}

.card {
  background: var(--bg-card);
  backdrop-filter: blur(12px);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 1.5rem;
  box-shadow: var(--shadow-md);
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
  max-width: 240px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.requester-cell {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.badge-self {
  font-size: 0.6875rem;
  padding: 0.15rem 0.4rem;
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
  border-radius: var(--radius-sm);
  font-weight: 700;
}

.tag-dual-dot {
  font-size: 0.6875rem;
  padding: 0.15rem 0.4rem;
  background: rgba(139, 92, 246, 0.15);
  color: #a78bfa;
  border-radius: var(--radius-sm);
  font-weight: 700;
  margin-left: 0.35rem;
}

.cell-actions {
  display: flex;
  gap: 0.375rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-radius: var(--radius-md);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: all var(--transition-fast);
}

.btn-xs {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
}

.btn-primary { background: var(--primary); color: #fff; }
.btn-success { background: var(--success); color: #fff; }
.btn-danger { background: var(--danger); color: #fff; }
.btn-secondary { background: var(--bg-surface); color: var(--text-primary); border: 1px solid var(--border-color); }
.btn-outline { background: transparent; color: var(--primary); border: 1px solid var(--primary); }

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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

.badge-pending { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
.badge-dual { background: rgba(139, 92, 246, 0.15); color: #a78bfa; }
.badge-approved { background: rgba(16, 185, 129, 0.15); color: #10b981; }
.badge-rejected { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
.badge-cancelled { background: rgba(107, 114, 128, 0.15); color: #9ca3af; }

.tag {
  font-size: 0.75rem;
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
}

.tag-profile { background: rgba(59, 130, 246, 0.15); color: #60a5fa; font-weight: 600; }
.tag-key { background: rgba(139, 92, 246, 0.15); color: #a78bfa; }
.tag-san { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }

/* Modal */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
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
  max-width: 650px;
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
  gap: 1.25rem;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 1.25rem 1.5rem;
  border-top: 1px solid var(--border-color);
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem 1.25rem;
  font-size: 0.875rem;
}

.full-width {
  grid-column: 1 / -1;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
}

.action-panel {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.action-tabs {
  display: flex;
  border-bottom: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.02);
}

.action-tab {
  flex: 1;
  padding: 0.625rem 1rem;
  background: transparent;
  border: none;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-tab.active {
  background: var(--bg-surface);
  color: var(--primary);
  border-bottom: 2px solid var(--primary);
}

.action-body {
  padding: 1.25rem;
}

.notice-box {
  background: rgba(59, 130, 246, 0.08);
  border-left: 3px solid var(--primary);
  padding: 0.75rem;
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
}

.approvals-box {
  background: rgba(255, 255, 255, 0.02);
  padding: 1rem;
  border-radius: var(--radius-md);
}

.approvals-box h4 {
  font-size: 0.875rem;
  margin-bottom: 0.75rem;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-left: 1.25rem;
  border-left: 2px solid var(--border-color);
}

.timeline-item { position: relative; }

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

.form-control {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 0.625rem 0.875rem;
  color: var(--text-primary);
  font-size: 0.875rem;
  outline: none;
  width: 100%;
}

.form-control:focus { border-color: var(--primary); }

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }
.break-all { word-break: break-all; }
.mb-3 { margin-bottom: 0.75rem; }
.mt-3 { margin-top: 0.75rem; }
.font-mono { font-family: var(--font-mono); }
.font-bold { font-weight: 700; }
</style>
