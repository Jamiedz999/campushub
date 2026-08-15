package com.campushub.identityaccess.internal;

import com.campushub.club.ClubModule;
import com.campushub.identityaccess.domain.Account;
import com.campushub.identityaccess.domain.SystemRole;
import com.campushub.identityaccess.persistence.AccountRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

// Profile-gated: two Clubs, two Club Officers and several Students, so a demo deployment has
// something to look at. Runs under "development" and "demo" and nowhere else, so demo data is never
// silently the production data — see Issue #2's amendments. "demo" exists because the deployed demo
// wants this seed without wanting the developer conveniences "development" also carries, one of which
// is a fallback for the check-in HMAC secret (docs/adr/15-define-http-api-and-time-contract.md); under
// "demo" that secret is required like every other. One of the two officers (and one of the students)
// uses the published demo@ email; the second officer and the remaining students exist only to make
// "a Club Officer of one Club cannot see another Club's officers" a real, checkable scenario rather
// than a single-data-point demo.
@ChangeUnit(id = "identityaccess-demo-data-004", order = "004")
@Profile({"development", "demo"})
public class IdentityAccessDemoDataChangeUnit {

    @Execution
    public void execution(AccountRepository accountRepository, PasswordEncoder passwordEncoder, ClubModule clubModule) {
        String passwordHash = passwordEncoder.encode(IdentityAccessStructuralChangeUnit.ADMIN_PASSWORD);

        String chessClubId = clubModule.createClub("Chess Club");
        String dramaClubId = clubModule.createClub("Drama Society");

        Account officer = accountRepository.insertIfAbsent(
                new Account("officer@demo.campushub", passwordHash, "Demo Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(chessClubId, officer.getId());

        Account secondOfficer = accountRepository.insertIfAbsent(
                new Account("priya.officer@demo.campushub", passwordHash, "Priya Officer", SystemRole.STUDENT));
        clubModule.grantOfficer(dramaClubId, secondOfficer.getId());

        accountRepository.insertIfAbsent(
                new Account("student@demo.campushub", passwordHash, "Demo Student", SystemRole.STUDENT));
        accountRepository.insertIfAbsent(
                new Account("alex.student@demo.campushub", passwordHash, "Alex Student", SystemRole.STUDENT));
        accountRepository.insertIfAbsent(
                new Account("sam.student@demo.campushub", passwordHash, "Sam Student", SystemRole.STUDENT));
    }

    @RollbackExecution
    public void rollback() {}
}
