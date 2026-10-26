package com.pki.ca.dto.request;

import com.pki.ca.enums.RoleName;
import com.pki.ca.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class UpdateUserRequest {

    @Email(message = "Email must be valid")
    @Size(max = 128, message = "Email cannot exceed 128 characters")
    private String email;

    @Size(min = 2, max = 128, message = "Full name must be between 2 and 128 characters")
    private String fullName;

    private UserStatus status;

    private Set<RoleName> roles;

    @Size(min = 8, max = 128, message = "New password must be at least 8 characters long")
    private String newPassword;

    public UpdateUserRequest() {
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

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
