package com.campushub.identityaccess.internal;

import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.persistence.AccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    AccountUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        Account account = accountRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for email " + email));
        return new AccountPrincipal(
                account.getId(), account.getEmail(), account.getPasswordHash(), account.getSystemRole());
    }
}
