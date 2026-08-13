package com.campushub.identityaccess.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("accounts")
public class Account {

    @Id
    private String id;

    private String email;

    private String passwordHash;

    private String displayName;

    private SystemRole systemRole;

    public Account() {}

    public Account(String email, String passwordHash, String displayName, SystemRole systemRole) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.systemRole = systemRole;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SystemRole getSystemRole() {
        return systemRole;
    }
}
