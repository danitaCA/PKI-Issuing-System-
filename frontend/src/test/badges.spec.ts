import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import RoleBadge from '../components/RoleBadge.vue';
import StatusBadge from '../components/StatusBadge.vue';

describe('RoleBadge.vue', () => {
  it('renders CA_ADMIN badge with correct title and class', () => {
    const wrapper = mount(RoleBadge, {
      props: { role: 'ROLE_CA_ADMIN' }
    });
    expect(wrapper.text()).toContain('CA Admin');
    expect(wrapper.classes()).toContain('badge-role-admin');
  });

  it('renders RA_OPERATOR badge with correct label and class', () => {
    const wrapper = mount(RoleBadge, {
      props: { role: 'ROLE_RA_OPERATOR' }
    });
    expect(wrapper.text()).toContain('RA Operator');
    expect(wrapper.classes()).toContain('badge-role-ra');
  });

  it('renders SECURITY_OFFICER badge with correct label and class', () => {
    const wrapper = mount(RoleBadge, {
      props: { role: 'ROLE_SECURITY_OFFICER' }
    });
    expect(wrapper.text()).toContain('Security Officer');
    expect(wrapper.classes()).toContain('badge-role-sec');
  });

  it('renders AUDITOR badge with correct label and class', () => {
    const wrapper = mount(RoleBadge, {
      props: { role: 'ROLE_AUDITOR' }
    });
    expect(wrapper.text()).toContain('Auditor');
    expect(wrapper.classes()).toContain('badge-role-auditor');
  });

  it('renders END_ENTITY badge with correct label and class', () => {
    const wrapper = mount(RoleBadge, {
      props: { role: 'ROLE_END_ENTITY' }
    });
    expect(wrapper.text()).toContain('End Entity');
    expect(wrapper.classes()).toContain('badge-role-ee');
  });
});

describe('StatusBadge.vue', () => {
  it('renders ACTIVE status correctly', () => {
    const wrapper = mount(StatusBadge, {
      props: { status: 'ACTIVE' }
    });
    expect(wrapper.text()).toBe('ACTIVE');
    expect(wrapper.classes()).toContain('badge-active');
  });

  it('renders LOCKED status correctly', () => {
    const wrapper = mount(StatusBadge, {
      props: { status: 'LOCKED' }
    });
    expect(wrapper.text()).toBe('LOCKED');
    expect(wrapper.classes()).toContain('badge-locked');
  });

  it('renders DISABLED status correctly', () => {
    const wrapper = mount(StatusBadge, {
      props: { status: 'DISABLED' }
    });
    expect(wrapper.text()).toBe('DISABLED');
    expect(wrapper.classes()).toContain('badge-disabled');
  });
});
