package com.campushub.identityaccess.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.campushub.club.ClubModule;
import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.identityaccess.persistence.AccountRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class IdentityAccessModuleImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ClubModule clubModule;

    private IdentityAccessModuleImpl module;

    @BeforeEach
    void setUp() {
        module = new IdentityAccessModuleImpl(accountRepository, clubModule);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesTheCurrentActorFreshFromTheDatabaseOnEveryCall() {
        AccountPrincipal principal =
                new AccountPrincipal("account-1", "officer@demo.campushub", "hash", SystemRole.STUDENT);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
        Account account = new Account("officer@demo.campushub", "hash", "Demo Officer", SystemRole.STUDENT);
        when(accountRepository.findById("account-1")).thenReturn(Optional.of(account));
        when(clubModule.officerClubIdsFor("account-1")).thenReturn(Set.of("club-a", "club-b"));

        CurrentActor actor = module.currentActor();

        assertThat(actor.accountId()).isEqualTo("account-1");
        assertThat(actor.email()).isEqualTo("officer@demo.campushub");
        assertThat(actor.displayName()).isEqualTo("Demo Officer");
        assertThat(actor.systemRole()).isEqualTo(SystemRole.STUDENT);
        assertThat(actor.officerClubIds()).containsExactlyInAnyOrder("club-a", "club-b");
    }

    @Test
    void aFreshCallSeesAGrantRevokedSinceLogin() {
        AccountPrincipal principal =
                new AccountPrincipal("account-1", "officer@demo.campushub", "hash", SystemRole.STUDENT);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
        Account account = new Account("officer@demo.campushub", "hash", "Demo Officer", SystemRole.STUDENT);
        when(accountRepository.findById("account-1")).thenReturn(Optional.of(account));
        when(clubModule.officerClubIdsFor("account-1")).thenReturn(Set.of());

        CurrentActor actor = module.currentActor();

        assertThat(actor.officerClubIds()).isEmpty();
    }

    @Test
    void refusesToResolveAnActorOutsideAnAuthenticatedRequest() {
        assertThatThrownBy(() -> module.currentActor()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToResolveAnActorWhosePrincipalIsNotAnAccountPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("not-an-account", null));

        assertThatThrownBy(() -> module.currentActor()).isInstanceOf(IllegalStateException.class);
    }
}
