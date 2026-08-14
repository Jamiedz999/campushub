package com.campushub.club.internal;

import com.campushub.club.ClubModule;
import com.campushub.club.persistence.ClubOfficerGrantRepository;
import com.campushub.club.persistence.ClubRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class ClubModuleImpl implements ClubModule {

    private final ClubRepository clubRepository;
    private final ClubOfficerGrantRepository grantRepository;

    ClubModuleImpl(ClubRepository clubRepository, ClubOfficerGrantRepository grantRepository) {
        this.clubRepository = clubRepository;
        this.grantRepository = grantRepository;
    }

    @Override
    public String createClub(String name) {
        return clubRepository.create(name);
    }

    @Override
    public void grantOfficer(String clubId, String accountId) {
        grantRepository.grant(clubId, accountId);
    }

    @Override
    public void revokeOfficer(String clubId, String accountId) {
        grantRepository.revoke(clubId, accountId);
    }

    @Override
    public Map<String, String> clubNames(Set<String> clubIds) {
        return clubRepository.namesOf(clubIds);
    }

    @Override
    public List<String> officersOf(String clubId) {
        return grantRepository.officerAccountIdsOf(clubId);
    }

    @Override
    public Set<String> officerClubIdsFor(String accountId) {
        return grantRepository.clubIdsOfficeredBy(accountId);
    }
}
