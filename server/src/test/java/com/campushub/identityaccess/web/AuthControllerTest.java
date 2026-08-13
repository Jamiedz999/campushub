package com.campushub.identityaccess.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.identityaccess.domain.SystemRole;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private IdentityAccessModule identityAccessModule;

    @Test
    void returnsTheCurrentActorAsSeenByTheModule() {
        when(identityAccessModule.currentActor())
                .thenReturn(new CurrentActor(
                        "account-1",
                        "officer@demo.campushub",
                        "Demo Officer",
                        SystemRole.STUDENT,
                        Set.of("club-a", "club-b")));
        AuthController controller = new AuthController(identityAccessModule);

        CurrentActorResponse response = controller.currentActor();

        assertThat(response.accountId()).isEqualTo("account-1");
        assertThat(response.email()).isEqualTo("officer@demo.campushub");
        assertThat(response.displayName()).isEqualTo("Demo Officer");
        assertThat(response.systemRole()).isEqualTo(SystemRole.STUDENT);
        assertThat(response.officerClubIds()).containsExactlyInAnyOrder("club-a", "club-b");
    }
}
