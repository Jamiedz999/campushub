package com.campushub.identityaccess.internal;

import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.identityaccess.persistence.AccountRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.security.crypto.password.PasswordEncoder;

// Always runs, in every profile: the unique index on accounts.email, and the one University Admin
// account that lets a completely fresh deployment sign in at all. This is deliberately not "demo
// data" — CampusHub has no self-service sign-up (see Issue #2's amendments), so some account has to
// exist out of the box or nobody could ever grant a Club Officer or do anything else. Its credentials
// happen to be the same published, memorable ones as the profile-gated demo accounts because this
// project has no other environment that needs a different, secret one.
@ChangeUnit(id = "identityaccess-structural-002", order = "002")
public class IdentityAccessStructuralChangeUnit {

    static final String ADMIN_EMAIL = "admin@demo.campushub";
    static final String ADMIN_PASSWORD = "123456";

    @Execution
    public void execution(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        accountRepository.ensureEmailUniqueIndex();
        accountRepository.insertIfAbsent(new Account(
                ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), "University Admin", SystemRole.UNIVERSITY_ADMIN));
    }

    @RollbackExecution
    public void rollback() {}
}
