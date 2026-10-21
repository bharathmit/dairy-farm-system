package com.dairyfarm.animal.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.config.SupabaseProperties;

class SupabasePhotoStorageTest {

    private static final String URL = "https://abcd.supabase.co";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private SupabasePhotoStorage storage(String key) {
        return new SupabasePhotoStorage(new SupabaseProperties(URL, null, key, "animal-photos", null), builder);
    }

    @Test
    void uploadsIntoThePrivateBucket() {
        server.expect(requestTo(URL + "/storage/v1/object/animal-photos/farm-1/animal-2/x.png"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "sb_secret_abc"))
                .andExpect(header("x-upsert", "false"))
                .andExpect(header("Content-Type", "image/png"))
                .andRespond(withSuccess("{\"Key\":\"animal-photos/farm-1/animal-2/x.png\"}", MediaType.APPLICATION_JSON));

        storage("sb_secret_abc").upload("farm-1/animal-2/x.png", new byte[] {1, 2}, "image/png");
        server.verify();
    }

    @Test
    void signsLinksInOneCall() {
        server.expect(requestTo(URL + "/storage/v1/object/sign/animal-photos"))
                .andExpect(content().json("{\"expiresIn\":3600,\"paths\":[\"a.png\",\"b.png\"]}"))
                .andRespond(withSuccess("""
                        [{"path":"a.png","signedURL":"/object/sign/animal-photos/a.png?token=1","error":null},
                         {"path":"b.png","signedURL":"/object/sign/animal-photos/b.png?token=2","error":null}]""",
                        MediaType.APPLICATION_JSON));

        var urls = storage("sb_secret_abc").signedUrls(List.of("a.png", "b.png"));

        assertThat(urls).containsEntry("a.png", URL + "/storage/v1/object/sign/animal-photos/a.png?token=1");
        assertThat(urls).hasSize(2);
    }

    @Test
    void withoutAKeyListsStillWorkButUploadsExplain() {
        assertThat(storage("").signedUrls(List.of("a.png"))).isEmpty();
        assertThatThrownBy(() -> storage("").upload("a.png", new byte[] {1}, "image/png"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Photo storage isn't set up");
    }
}
