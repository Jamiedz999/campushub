package com.campushub.identityaccess.internal;

import com.campushub.identityaccess.domain.SystemRole;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// Deliberately carries only the account id, email, password hash and system role — never Club Officer
// grants. Those are resolved fresh per request (see IdentityAccessModuleImpl), not baked into the
// session-persisted Authentication, which is what lets a revoked grant take effect immediately.
class AccountPrincipal implements UserDetails {

    private final String accountId;
    private final String email;
    private final String passwordHash;
    private final SystemRole systemRole;

    AccountPrincipal(String accountId, String email, String passwordHash, SystemRole systemRole) {
        this.accountId = accountId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.systemRole = systemRole;
    }

    String getAccountId() {
        return accountId;
    }

    SystemRole getSystemRole() {
        return systemRole;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
