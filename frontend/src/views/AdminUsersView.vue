<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { usersApi, type CreateUserPayload, type UpdateUserPayload } from '../api/users';
import type { UserSummary, RoleName } from '../api/auth';
import RoleBadge from '../components/RoleBadge.vue';
import StatusBadge from '../components/StatusBadge.vue';
import ProblemAlert from '../components/ProblemAlert.vue';
import type { AxiosError } from 'axios';
import type { ProblemDetail } from '../api/auth';

const users = ref<UserSummary[]>([]);
const totalElements = ref(0);
const totalPages = ref(1);
const currentPage = ref(0);
const pageSize = ref(10);
const loading = ref(false);
const problem = ref<ProblemDetail | null>(null);
const successMsg = ref<string | null>(null);

// Modal states
const showCreateModal = ref(false);
const showLockModal = ref(false);
const showEditModal = ref(false);
const selectedUser = ref<UserSummary | null>(null);

// Create user form
const newUser = ref<CreateUserPayload>({
  username: '',
  email: '',
  fullName: '',
  password: '',
  roles: ['ROLE_END_ENTITY']
});

// Lock user form
const lockDuration = ref(30);
const lockReason = ref('Administrative policy review');

// Edit user form
const editRoles = ref<RoleName[]>([]);

const allRoles: { name: RoleName; label: string }[] = [
  { name: 'ROLE_CA_ADMIN', label: 'CA Administrator' },
  { name: 'ROLE_RA_OPERATOR', label: 'RA Operator' },
  { name: 'ROLE_SECURITY_OFFICER', label: 'Security Officer' },
  { name: 'ROLE_AUDITOR', label: 'Auditor' },
  { name: 'ROLE_END_ENTITY', label: 'End Entity' }
];

async function loadUsers(page = 0) {
  loading.value = true;
  problem.value = null;
  try {
    const data = await usersApi.listUsers(page, pageSize.value);
    users.value = data.content;
    totalElements.value = data.totalElements;
    totalPages.value = data.totalPages;
    currentPage.value = data.pageNumber;
  } catch (err: unknown) {
    const axiosError = err as AxiosError<ProblemDetail>;
    problem.value = axiosError.response?.data || {
      type: 'about:blank',
      title: 'Load Failed',
      status: 500,
      detail: 'Failed to retrieve user list from the CA administration backend.'
    };
  } finally {
    loading.value = false;
  }
}

function openCreateModal() {
  newUser.value = {
    username: '',
    email: '',
    fullName: '',
    password: '',
    roles: ['ROLE_END_ENTITY']
  };
  showCreateModal.value = true;
}

function toggleNewUserRole(role: RoleName) {
  const idx = newUser.value.roles.indexOf(role);
  if (idx >= 0) {
    if (newUser.value.roles.length > 1) {
      newUser.value.roles.splice(idx, 1);
    }
  } else {
    newUser.value.roles.push(role);
  }
}

async function handleCreateUser() {
  problem.value = null;
  successMsg.value = null;
  try {
    await usersApi.createUser(newUser.value);
    showCreateModal.value = false;
    successMsg.value = `User account '${newUser.value.username}' created successfully.`;
    await loadUsers(0);
  } catch (err: unknown) {
    const axiosError = err as AxiosError<ProblemDetail>;
    problem.value = axiosError.response?.data || {
      type: 'about:blank',
      title: 'User Creation Error',
      status: 400,
      detail: 'Failed to create user. Please check form inputs and uniqueness.'
    };
  }
}

function openLockModal(user: UserSummary) {
  selectedUser.value = user;
  lockDuration.value = 30;
  lockReason.value = 'Administrative lockout initiated';
  showLockModal.value = true;
}

async function handleLockUser() {
  if (!selectedUser.value) return;
  problem.value = null;
  try {
    await usersApi.lockUser(selectedUser.value.id, lockDuration.value, lockReason.value);
    showLockModal.value = false;
    successMsg.value = `User '${selectedUser.value.username}' has been locked for ${lockDuration.value} minutes.`;
    await loadUsers(currentPage.value);
  } catch (err: unknown) {
    const axiosError = err as AxiosError<ProblemDetail>;
    problem.value = axiosError.response?.data || {
      type: 'about:blank',
      title: 'Lock Error',
      status: 500,
      detail: 'Could not lock target user account.'
    };
  }
}

async function handleUnlockUser(user: UserSummary) {
  problem.value = null;
  try {
    await usersApi.unlockUser(user.id);
    successMsg.value = `User '${user.username}' has been unlocked.`;
    await loadUsers(currentPage.value);
  } catch (err: unknown) {
    const axiosError = err as AxiosError<ProblemDetail>;
    problem.value = axiosError.response?.data || {
      type: 'about:blank',
      title: 'Unlock Error',
      status: 500,
      detail: 'Could not unlock user account.'
    };
  }
}

function openEditModal(user: UserSummary) {
  selectedUser.value = user;
  editRoles.value = [...user.roles];
  showEditModal.value = true;
}

function toggleEditRole(role: RoleName) {
  const idx = editRoles.value.indexOf(role);
  if (idx >= 0) {
    if (editRoles.value.length > 1) {
      editRoles.value.splice(idx, 1);
    }
  } else {
    editRoles.value.push(role);
  }
}

async function handleUpdateRoles() {
  if (!selectedUser.value) return;
  problem.value = null;
  try {
    const payload: UpdateUserPayload = {
      roles: editRoles.value
    };
    await usersApi.updateUser(selectedUser.value.id, payload);
    showEditModal.value = false;
    successMsg.value = `Roles for '${selectedUser.value.username}' updated successfully.`;
    await loadUsers(currentPage.value);
  } catch (err: unknown) {
    const axiosError = err as AxiosError<ProblemDetail>;
    problem.value = axiosError.response?.data || {
      type: 'about:blank',
      title: 'Update Error',
      status: 500,
      detail: 'Could not update user roles.'
    };
  }
}

onMounted(() => {
  loadUsers();
});
</script>

<template>
  <div class="admin-users-view">
    <!-- Header -->
    <div class="view-header">
      <div class="header-titles">
        <h1>User & Role Administration</h1>
        <p class="header-subtitle">
          Manage PKI administrative personnel, registration operators, security officers, and subscribers.
        </p>
      </div>
      <div class="header-actions">
        <button class="btn btn-secondary btn-sm" @click="loadUsers(currentPage)" :disabled="loading">
          <svg viewBox="0 0 20 20" fill="currentColor" class="btn-icon">
            <path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z" clip-rule="evenodd" />
          </svg>
          <span>Refresh</span>
        </button>
        <button id="create-user-modal-btn" class="btn btn-primary btn-sm" @click="openCreateModal">
          <svg viewBox="0 0 20 20" fill="currentColor" class="btn-icon">
            <path fill-rule="evenodd" d="M10 5a1 1 0 011 1v3h3a1 1 0 110 2h-3v3a1 1 0 11-2 0v-3H6a1 1 0 110-2h3V6a1 1 0 011-1z" clip-rule="evenodd" />
          </svg>
          <span>Provision New User</span>
        </button>
      </div>
    </div>

    <!-- Feedback Alerts -->
    <ProblemAlert :problem="problem" @dismiss="problem = null" />

    <div v-if="successMsg" class="success-banner">
      <svg viewBox="0 0 20 20" fill="currentColor" class="banner-icon">
        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
      </svg>
      <span>{{ successMsg }}</span>
      <button class="dismiss-btn" @click="successMsg = null">×</button>
    </div>

    <!-- Data Table Container -->
    <div class="table-card glass-panel">
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <span>Loading user directory...</span>
      </div>

      <div v-else class="table-container">
        <table class="data-table" id="users-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>User / Identifier</th>
              <th>Full Name</th>
              <th>Assigned RBAC Roles</th>
              <th>Status</th>
              <th>Created Date</th>
              <th class="actions-header">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td class="id-col font-mono">#{{ user.id }}</td>
              <td>
                <div class="user-meta">
                  <span class="user-username">{{ user.username }}</span>
                  <span class="user-email font-mono">{{ user.email }}</span>
                </div>
              </td>
              <td>{{ user.fullName }}</td>
              <td>
                <div class="roles-cell">
                  <RoleBadge v-for="role in user.roles" :key="role" :role="role" />
                </div>
              </td>
              <td>
                <StatusBadge :status="user.status" />
              </td>
              <td class="font-mono text-muted date-col">
                {{ new Date(user.createdAt).toLocaleDateString() }}
              </td>
              <td class="actions-cell">
                <button
                  class="action-btn-sm"
                  title="Modify Assigned Roles"
                  @click="openEditModal(user)"
                >
                  Roles
                </button>
                <button
                  v-if="user.status === 'LOCKED'"
                  class="action-btn-sm unlock-btn"
                  title="Unlock Account"
                  @click="handleUnlockUser(user)"
                >
                  Unlock
                </button>
                <button
                  v-else-if="user.status === 'ACTIVE'"
                  class="action-btn-sm lock-btn"
                  title="Lock Account"
                  @click="openLockModal(user)"
                >
                  Lock
                </button>
              </td>
            </tr>
            <tr v-if="users.length === 0">
              <td colspan="7" class="empty-state">No user records found in the directory.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination Footer -->
      <div class="pagination-footer">
        <span class="pagination-info">
          Showing page {{ currentPage + 1 }} of {{ totalPages || 1 }} ({{ totalElements }} total users)
        </span>
        <div class="pagination-controls">
          <button
            class="btn btn-secondary btn-sm"
            :disabled="currentPage === 0 || loading"
            @click="loadUsers(currentPage - 1)"
          >
            Previous
          </button>
          <button
            class="btn btn-secondary btn-sm"
            :disabled="currentPage >= totalPages - 1 || loading"
            @click="loadUsers(currentPage + 1)"
          >
            Next
          </button>
        </div>
      </div>
    </div>

    <!-- Create User Modal -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal-content glass-panel">
        <div class="modal-header">
          <h3>Provision New System User</h3>
          <button class="close-btn" @click="showCreateModal = false">×</button>
        </div>
        <form @submit.prevent="handleCreateUser" class="modal-form">
          <div class="form-group">
            <label class="form-label" for="nu-username">Username</label>
            <input id="nu-username" v-model="newUser.username" type="text" class="form-input" required />
          </div>
          <div class="form-group">
            <label class="form-label" for="nu-email">Corporate Email</label>
            <input id="nu-email" v-model="newUser.email" type="email" class="form-input" required />
          </div>
          <div class="form-group">
            <label class="form-label" for="nu-fullname">Full Name</label>
            <input id="nu-fullname" v-model="newUser.fullName" type="text" class="form-input" required />
          </div>
          <div class="form-group">
            <label class="form-label" for="nu-password">Initial Password</label>
            <input id="nu-password" v-model="newUser.password" type="password" class="form-input" placeholder="Min 10 chars, upper, lower, digit, symbol" required />
          </div>
          <div class="form-group">
            <label class="form-label">Assigned Roles</label>
            <div class="checkbox-role-group">
              <label
                v-for="role in allRoles"
                :key="role.name"
                class="checkbox-role-label"
                :class="{ selected: newUser.roles.includes(role.name) }"
              >
                <input
                  type="checkbox"
                  :checked="newUser.roles.includes(role.name)"
                  @change="toggleNewUserRole(role.name)"
                />
                <span>{{ role.label }}</span>
              </label>
            </div>
          </div>

          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="showCreateModal = false">Cancel</button>
            <button type="submit" id="submit-create-user-btn" class="btn btn-primary">Create Account</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Lock User Modal -->
    <div v-if="showLockModal && selectedUser" class="modal-overlay" @click.self="showLockModal = false">
      <div class="modal-content glass-panel">
        <div class="modal-header">
          <h3>Lock User: {{ selectedUser.username }}</h3>
          <button class="close-btn" @click="showLockModal = false">×</button>
        </div>
        <form @submit.prevent="handleLockUser" class="modal-form">
          <p class="modal-instruction">
            Locking this user immediately invalidates active sessions and prevents further logins.
          </p>
          <div class="form-group">
            <label class="form-label">Duration (Minutes)</label>
            <input v-model.number="lockDuration" type="number" min="5" max="1440" class="form-input" required />
          </div>
          <div class="form-group">
            <label class="form-label">Lock Reason / Audit Justification</label>
            <input v-model="lockReason" type="text" class="form-input" required />
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="showLockModal = false">Cancel</button>
            <button type="submit" class="btn btn-danger">Confirm Lock</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Edit Roles Modal -->
    <div v-if="showEditModal && selectedUser" class="modal-overlay" @click.self="showEditModal = false">
      <div class="modal-content glass-panel">
        <div class="modal-header">
          <h3>Edit Roles: {{ selectedUser.username }}</h3>
          <button class="close-btn" @click="showEditModal = false">×</button>
        </div>
        <form @submit.prevent="handleUpdateRoles" class="modal-form">
          <div class="form-group">
            <label class="form-label">Select Authorized Roles</label>
            <div class="checkbox-role-group">
              <label
                v-for="role in allRoles"
                :key="role.name"
                class="checkbox-role-label"
                :class="{ selected: editRoles.includes(role.name) }"
              >
                <input
                  type="checkbox"
                  :checked="editRoles.includes(role.name)"
                  @change="toggleEditRole(role.name)"
                />
                <span>{{ role.label }}</span>
              </label>
            </div>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="showEditModal = false">Cancel</button>
            <button type="submit" class="btn btn-primary">Save Changes</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-users-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 1rem;
}

.header-titles h1 {
  font-size: 1.75rem;
  letter-spacing: -0.02em;
}

.header-subtitle {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin-top: 0.25rem;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.success-banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: var(--success-bg);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: var(--success);
  padding: 0.75rem 1rem;
  border-radius: var(--radius-md);
  font-size: 0.875rem;
}

.banner-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.dismiss-btn {
  margin-left: auto;
  background: none;
  border: none;
  color: var(--success);
  font-size: 1.25rem;
  cursor: pointer;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 1rem;
  gap: 0.75rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.spinner {
  width: 28px;
  height: 28px;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.id-col {
  color: var(--text-muted);
  font-size: 0.8125rem;
}

.user-meta {
  display: flex;
  flex-direction: column;
}

.user-username {
  font-weight: 600;
  color: var(--text-primary);
}

.user-email {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.roles-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.date-col {
  font-size: 0.75rem;
}

.actions-header {
  text-align: right;
}

.actions-cell {
  text-align: right;
  white-space: nowrap;
}

.action-btn-sm {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 0.75rem;
  font-weight: 500;
  padding: 0.25rem 0.5rem;
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-left: 0.35rem;
  transition: all var(--transition-fast);
}

.action-btn-sm:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-primary);
}

.lock-btn:hover {
  background: var(--danger-bg);
  color: var(--danger);
  border-color: rgba(239, 68, 68, 0.3);
}

.unlock-btn {
  background: var(--success-bg);
  color: var(--success);
  border-color: rgba(16, 185, 129, 0.3);
}

.unlock-btn:hover {
  background: rgba(16, 185, 129, 0.2);
}

.empty-state {
  text-align: center;
  padding: 2.5rem;
  color: var(--text-muted);
}

.pagination-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.875rem 1.25rem;
  border-top: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.15);
}

.pagination-info {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.pagination-controls {
  display: flex;
  gap: 0.5rem;
}

/* Modals */
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.25rem;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: var(--text-muted);
  cursor: pointer;
}

.close-btn:hover {
  color: var(--text-primary);
}

.modal-instruction {
  font-size: 0.8125rem;
  color: var(--text-secondary);
  margin-bottom: 1rem;
}

.checkbox-role-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.checkbox-role-label {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.5rem 0.75rem;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: 0.8125rem;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.checkbox-role-label.selected {
  border-color: var(--primary);
  background: rgba(59, 130, 246, 0.1);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-color);
}
</style>
