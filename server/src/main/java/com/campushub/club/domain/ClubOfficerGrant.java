package com.campushub.club.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// One Club Officer grant, always naming exactly one Club — see
// docs/adr/08-define-roles-and-resource-authorization.md: the officer role is a per-Club grant, never
// a global flag, and a Student may hold grants in more than one Club.
@Document("clubOfficerGrants")
public class ClubOfficerGrant {

    @Id
    private String id;

    private String clubId;

    private String accountId;

    public ClubOfficerGrant() {}

    public ClubOfficerGrant(String clubId, String accountId) {
        this.clubId = clubId;
        this.accountId = accountId;
    }

    public String getId() {
        return id;
    }

    public String getClubId() {
        return clubId;
    }

    public String getAccountId() {
        return accountId;
    }
}
