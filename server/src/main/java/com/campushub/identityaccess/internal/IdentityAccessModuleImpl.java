package com.campushub.identityaccess.internal;

import com.campushub.club.ClubModule;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.persistence.AccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class IdentityAccessModuleImpl implements IdentityAccessModule {

    private final AccountRepository accountRepository;
    private final ClubModule clubModule;

    IdentityAccessModuleImpl(AccountRepository accountRepository, ClubModule clubModule) {
        this.accountRepository = accountRepository;
        this.clubModule = clubModule;
    }

    @Override
    public CurrentActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new IllegalStateException("currentActor() called outside an authenticated request");
        }
        String accountId = principal.getAccountId();

        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Signed-in account " + accountId + " no longer exists"));

        return new CurrentActor(
                accountId,
                account.getEmail(),
                account.getDisplayName(),
                account.getSystemRole(),
                clubModule.officerClubIdsFor(accountId));
    }
}
