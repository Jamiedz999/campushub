package com.campushub.dashboard.domain;

import java.util.Set;

/**
 * Which Clubs a dashboard read covers.
 *
 * <p>A nullable {@code Set<String>} would say the same thing in fewer characters and would depend on
 * every caller remembering what null meant — the pattern this project rejects everywhere else, because
 * a rule enforced by memory is broken by the first caller who does not have it. The distinction that
 * matters is not "some ids or none" but "every Club or exactly these", and those are different enough
 * that the empty case has to be unambiguous: {@link NamedClubs} of nothing matches nothing, which is
 * what a Club Officer holding no grants must see. See
 * docs/adr/08-define-roles-and-resource-authorization.md.
 */
public sealed interface ClubScope {

    /** Every Club — the University Admin's unscoped read, and the only way to get one. */
    record AllClubs() implements ClubScope {}

    /** Exactly these Clubs. An empty set matches nothing rather than everything. */
    record NamedClubs(Set<String> clubIds) implements ClubScope {

        public NamedClubs {
            clubIds = Set.copyOf(clubIds);
        }
    }

    static ClubScope allClubs() {
        return new AllClubs();
    }

    static ClubScope of(Set<String> clubIds) {
        return new NamedClubs(clubIds);
    }
}
