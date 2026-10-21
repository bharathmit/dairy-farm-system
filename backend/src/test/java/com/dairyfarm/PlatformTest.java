package com.dairyfarm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.dairyfarm.support.IntegrationTest;

class PlatformTest extends IntegrationTest {

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Dairy Farm API"))
                .andExpect(jsonPath("$.paths['/api/v1/me']").exists());
    }

    @Test
    void unknownRoutesAreClosed() throws Exception {
        mvc.perform(get("/anything")).andExpect(status().isUnauthorized());
    }
}
