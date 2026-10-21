package com.dairyfarm.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.config.SupabaseProperties;

class SupabaseAuthAdminTest {

    private static final String URL = "https://abcd.supabase.co";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private SupabaseAuthAdmin admin(String key, String redirect) {
        return new SupabaseAuthAdmin(new SupabaseProperties(URL, null, key, "animal-photos", redirect), builder);
    }

    @Test
    void createsAConfirmedUserWithTheSecretKeyInTheApikeyHeader() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(URL + "/auth/v1/admin/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "sb_secret_abc"))
                .andExpect(content().json("{\"email\":\"m@example.com\",\"password\":\"pw-123456\",\"email_confirm\":true,\"user_metadata\":{\"full_name\":\"Murugan\"}}"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\"}", MediaType.APPLICATION_JSON));

        var created = admin("sb_secret_abc", null).createUser("m@example.com", "pw-123456", "Murugan");

        assertThat(created.userId()).isEqualTo(id);
        server.verify();
    }

    @Test
    void reportsAnExistingAccount() {
        server.expect(requestTo(URL + "/auth/v1/admin/users"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":422,\"error_code\":\"email_exists\",\"msg\":\"A user with this email address has already been registered\"}"));

        assertThat(admin("sb_secret_abc", null).createUser("m@example.com", "pw-123456", "M").alreadyExisted()).isTrue();
    }

    @Test
    void invitesWithTheRedirectAndLegacyBearerKey() {
        server.expect(requestTo(URL + "/auth/v1/invite?redirect_to=https://farm.example/update-password"))
                .andExpect(header("Authorization", "Bearer eyJlegacy"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(admin("eyJlegacy", "https://farm.example/update-password").invite("k@example.com", "Kavi"))
                .isEqualTo(AuthAdmin.InviteResult.INVITED);
        server.verify();
    }

    @Test
    void turnsAWeakPasswordIntoAClearMessage() {
        server.expect(requestTo(URL + "/auth/v1/admin/users"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error_code\":\"weak_password\",\"msg\":\"Password should contain letters and digits\"}"));

        assertThatThrownBy(() -> admin("sb_secret_abc", null).createUser("m@example.com", "aaaaaaaa", "M"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void saysSoWhenTheServiceKeyIsMissing() {
        assertThatThrownBy(() -> admin("", null).invite("k@example.com", "Kavi"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
