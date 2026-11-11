package com.fptufa26.ecomotion.common.security;

import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

public class SecurityUser implements UserDetails {

    private final User user;
    private boolean fullAccess = true;

    public SecurityUser(User user) {
        this.user = user;
        if (user.getRole() == Role.PROVIDER && !user.isActive()) {
            fullAccess = false;
        }
    }

    public SecurityUser(User user, boolean fullAccess) {
        this(user);
        this.fullAccess = this.fullAccess && fullAccess;
    }

    public void restrictToOnboarding() { fullAccess = false; }

    public String getSessionScope() { return fullAccess ? "FULL" : "ONBOARDING"; }

    public boolean isRestrictedProvider() { return user.getRole() == Role.PROVIDER && !fullAccess; }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getUserId();
    }

    public Role getRole() {
        return user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(isRestrictedProvider()
                ? "ROLE_PROVIDER_ONBOARDING" : "ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive() || (user.getRole() == Role.PROVIDER
                && user.getStatus() == UserStatus.PENDING && user.getEmailVerifiedAt() != null);
    }
}
