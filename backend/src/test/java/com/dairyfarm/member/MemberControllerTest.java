package com.dairyfarm.member;

import static com.dairyfarm.support.TestTokens.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.dairyfarm.member.AuthAdmin.CreatedUser;
import com.dairyfarm.member.AuthAdmin.InviteResult;
import com.dairyfarm.support.Api;
import com.dairyfarm.support.IntegrationTest;
import com.dairyfarm.support.TestTokens.TestUser;

class MemberControllerTest extends IntegrationTest {

    @MockitoBean
    AuthAdmin authAdmin;

    private final TestUser owner = TestUser.random("Bharath");
    private long farmId;

    @BeforeEach
    void setUp() throws Exception {
        farmId = Api.createFarm(mvc, json, owner, "Villupuram Dairy");
    }

    private String members() {
        return "/api/v1/farms/" + farmId + "/members";
    }

    private long ownerMemberId() {
        return jdbc.queryForObject("select id from farm_member where farm_id = ? and user_id = ?", Long.class,
                farmId, owner.id());
    }

    @Test
    void addsAWorkerWithoutSignIn() throws Exception {
        mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Selvi","role":"WORKER","sex":"FEMALE","wageType":"MONTHLY","wageRate":12000,
                                 "joinedOn":"2025-06-15"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.signIn").value("NONE"))
                .andExpect(jsonPath("$.wageRate").value(12000))
                .andExpect(jsonPath("$.joinedOn").value("2025-06-15"));
        verify(authAdmin, never()).createUser(anyString(), anyString(), anyString());
    }

    @Test
    void ownerSetsAPasswordAndTheWorkerCanUseTheFarm() throws Exception {
        UUID newId = UUID.randomUUID();
        when(authAdmin.createUser("murugan@example.com", "cows-at-6am", "Murugan")).thenReturn(new CreatedUser(newId));

        mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Murugan","role":"WORKER","email":"Murugan@Example.com","wageType":"DAILY",
                                 "wageRate":500,"login":"PASSWORD","password":"cows-at-6am"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("murugan@example.com"))
                .andExpect(jsonPath("$.loginType").value("PASSWORD"))
                .andExpect(jsonPath("$.signIn").value("ACTIVE"));

        TestUser murugan = new TestUser(newId, "murugan@example.com", "Murugan");
        mvc.perform(get("/api/v1/me").with(as(murugan)))
                .andExpect(jsonPath("$.farms[0].role").value("WORKER"));
        mvc.perform(get("/api/v1/farms/{id}/settings", farmId).with(as(murugan)))
                .andExpect(status().isOk());
        mvc.perform(get(members()).with(as(murugan)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerChangesAManagedPassword() throws Exception {
        UUID newId = UUID.randomUUID();
        when(authAdmin.createUser(anyString(), anyString(), anyString())).thenReturn(new CreatedUser(newId));
        String created = mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Murugan\",\"role\":\"WORKER\",\"email\":\"m@example.com\",\"login\":\"PASSWORD\",\"password\":\"first-pass-1\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(put(members() + "/" + id).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Murugan\",\"role\":\"WORKER\",\"email\":\"m@example.com\",\"login\":\"PASSWORD\",\"password\":\"second-pass-2\"}"))
                .andExpect(status().isOk());
        verify(authAdmin).setPassword(newId, "second-pass-2");
    }

    @Test
    void passwordLoginNeedsEmailAndPassword() throws Exception {
        mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ravi\",\"role\":\"WORKER\",\"login\":\"PASSWORD\",\"password\":\"long-enough-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Add an email address so Ravi can sign in."));

        mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ravi\",\"role\":\"WORKER\",\"email\":\"ravi@example.com\",\"login\":\"PASSWORD\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void invitedWorkerIsLinkedOnFirstSignIn() throws Exception {
        when(authAdmin.invite("kavi@example.com", "Kavi")).thenReturn(InviteResult.INVITED);

        mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kavi\",\"role\":\"WORKER\",\"email\":\"kavi@example.com\",\"login\":\"INVITE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginType").value("INVITE"))
                .andExpect(jsonPath("$.signIn").value("PENDING"))
                .andExpect(jsonPath("$.invitedAt").exists())
                .andExpect(jsonPath("$.notice").value("Invite sent to kavi@example.com."));

        TestUser kavi = new TestUser(UUID.randomUUID(), "kavi@example.com", "Kavi");
        mvc.perform(get("/api/v1/me").with(as(kavi)))
                .andExpect(jsonPath("$.farms[0].name").value("Villupuram Dairy"));
        mvc.perform(get(members()).with(as(owner)))
                .andExpect(jsonPath("$[?(@.name=='Kavi')].signIn").value("ACTIVE"));
    }

    @Test
    void inviteToAnExistingAccountLinksItStraightAway() throws Exception {
        TestUser selvam = TestUser.random("Selvam");
        mvc.perform(get("/api/v1/me").with(as(selvam))).andExpect(status().isOk());
        when(authAdmin.invite(eq(selvam.email()), anyString())).thenReturn(InviteResult.ALREADY_REGISTERED);

        mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Selvam\",\"role\":\"OWNER\",\"email\":\"" + selvam.email() + "\",\"login\":\"INVITE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginType").value("SELF"))
                .andExpect(jsonPath("$.signIn").value("ACTIVE"))
                .andExpect(jsonPath("$.notice").value(MemberService.EXISTING_ACCOUNT_NOTICE));

        mvc.perform(get("/api/v1/me").with(as(selvam)))
                .andExpect(jsonPath("$.farms[0].role").value("OWNER"));
    }

    @Test
    void emailsAreUniqueWithinAFarm() throws Exception {
        String body = "{\"name\":\"Selvi\",\"role\":\"WORKER\",\"email\":\"selvi@example.com\"}";
        mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void theLastOwnerCannotBeRemovedDemotedOrDeactivated() throws Exception {
        long me = ownerMemberId();
        mvc.perform(put(members() + "/" + me).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bharath\",\"role\":\"WORKER\",\"email\":\"" + owner.email() + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(members() + "/" + me).with(as(owner)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(members() + "/" + me + "/status").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivateAndReactivateAWorker() throws Exception {
        String created = mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ravi\",\"role\":\"WORKER\",\"wageType\":\"DAILY\",\"wageRate\":450}"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(patch(members() + "/" + id + "/status").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"leftOn\":\"2026-09-30\"}"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.leftOn").value("2026-09-30"));
        mvc.perform(patch(members() + "/" + id + "/status").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.leftOn").doesNotExist());

        mvc.perform(delete(members() + "/" + id).with(as(owner))).andExpect(status().isNoContent());
        mvc.perform(get(members() + "/" + id).with(as(owner))).andExpect(status().isNotFound());
    }

    @Test
    void cannotChangeTheEmailOfSomeoneWhoSignsIn() throws Exception {
        when(authAdmin.createUser(anyString(), anyString(), anyString())).thenReturn(new CreatedUser(UUID.randomUUID()));
        String created = mvc.perform(post(members()).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Murugan\",\"role\":\"WORKER\",\"email\":\"m@example.com\",\"login\":\"PASSWORD\",\"password\":\"first-pass-1\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(put(members() + "/" + id).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Murugan\",\"role\":\"WORKER\",\"email\":\"other@example.com\"}"))
                .andExpect(status().isConflict());
        verify(authAdmin, never()).setPassword(any(), anyString());
    }
}
