package com.campushub.identityaccess.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.identityaccess.persistence.AccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AccountUserDetailsServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new AccountUserDetailsService(accountRepository);
    }

    @Test
    void loadsAnAccountByEmailAsAnAccountPrincipal() {
        Account account = new Account("officer@demo.campushub", "hashed-password", "Demo Officer", SystemRole.STUDENT);
        setId(account, "account-1");
        when(accountRepository.findByEmail("officer@demo.campushub")).thenReturn(Optional.of(account));

        UserDetails details = service.loadUserByUsername("officer@demo.campushub");

        assertThat(details).isInstanceOf(AccountPrincipal.class);
        AccountPrincipal principal = (AccountPrincipal) details;
        assertThat(principal.getAccountId()).isEqualTo("account-1");
        assertThat(principal.getUsername()).isEqualTo("officer@demo.campushub");
        assertThat(principal.getPassword()).isEqualTo("hashed-password");
        assertThat(principal.getSystemRole()).isEqualTo(SystemRole.STUDENT);
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STUDENT");
    }

    @Test
    void anUnknownEmailFailsWithoutRevealingWhetherTheAccountExists() {
        when(accountRepository.findByEmail("nobody@demo.campushub")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@demo.campushub"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private static void setId(Account account, String id) {
        try {
            java.lang.reflect.Field field = Account.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(account, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
