package com.pki.ca.dto.response;

import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;

import java.util.List;
import java.util.Set;

public class UserProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private UserStatus status;
    private Set<RoleName> roles;
    private List<String> permissions;

    public UserProfileResponse() {
    }

    public UserProfileResponse(Long id, String username, String email, String fullName,
                               UserStatus status, Set<RoleName> roles, List<String> permissions) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.status = status;
        this.roles = roles;
        this.permissions = permissions;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Set<RoleName> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleName> roles) {
        this.roles = roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
