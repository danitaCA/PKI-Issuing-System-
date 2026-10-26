import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import type { RoleName } from '../api/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { guestOnly: true }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/enrollment',
    name: 'Enrollment',
    component: () => import('../views/EnrollmentView.vue'),
    meta: { requiresAuth: true, allowedRoles: ['ROLE_END_ENTITY' as RoleName, 'ROLE_RA_OPERATOR' as RoleName, 'ROLE_CA_ADMIN' as RoleName] }
  },
  {
    path: '/ra/queue',
    name: 'RaQueue',
    component: () => import('../views/RaQueueView.vue'),
    meta: { requiresAuth: true, allowedRoles: ['ROLE_RA_OPERATOR' as RoleName, 'ROLE_CA_ADMIN' as RoleName, 'ROLE_SECURITY_OFFICER' as RoleName, 'ROLE_AUDITOR' as RoleName] }
  },
  {
    path: '/certificates',
    name: 'Certificates',
    component: () => import('../views/CertificatesView.vue'),
    meta: {
      requiresAuth: true,
      allowedRoles: [
        'ROLE_END_ENTITY' as RoleName,
        'ROLE_RA_OPERATOR' as RoleName,
        'ROLE_CA_ADMIN' as RoleName,
        'ROLE_SECURITY_OFFICER' as RoleName,
        'ROLE_AUDITOR' as RoleName
      ]
    }
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('../views/AdminUsersView.vue'),
    meta: { requiresAuth: true, allowedRoles: ['ROLE_CA_ADMIN' as RoleName] }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: () => import('../views/ForbiddenView.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFoundView.vue')
  }
];

export const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore();

  // Ensure auth is initialized from stored session if present
  if (!authStore.user && authStore.accessToken) {
    await authStore.fetchProfile();
  }

  const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
  const guestOnly = to.matched.some(record => record.meta.guestOnly);
  const allowedRoles = to.meta.allowedRoles as RoleName[] | undefined;

  if (requiresAuth && !authStore.isAuthenticated) {
    next({ path: '/login', query: { redirect: to.fullPath } });
  } else if (guestOnly && authStore.isAuthenticated) {
    next('/dashboard');
  } else if (requiresAuth && allowedRoles && !authStore.hasAnyRole(allowedRoles)) {
    next('/forbidden');
  } else {
    next();
  }
});
