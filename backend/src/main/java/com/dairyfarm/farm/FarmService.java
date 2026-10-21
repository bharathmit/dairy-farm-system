package com.dairyfarm.farm;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.member.FarmMember;
import com.dairyfarm.member.FarmMemberRepository;
import com.dairyfarm.member.LoginType;
import com.dairyfarm.member.Role;
import com.dairyfarm.user.AppUser;
import com.dairyfarm.user.UserService;

@Service
public class FarmService {

    /** Dates like "joined on" use the farm's local day. */
    public static final ZoneId FARM_ZONE = ZoneId.of("Asia/Kolkata");

    private final FarmRepository farms;
    private final FarmMemberRepository members;
    private final UserService users;

    public FarmService(FarmRepository farms, FarmMemberRepository members, UserService users) {
        this.farms = farms;
        this.members = members;
        this.users = users;
    }

    /** Creates a farm; the caller becomes its first owner. */
    @Transactional
    public FarmResponse create(FarmRequest req) {
        AppUser me = users.current();
        // One farm creation at a time per person, and an owner's farm with the same name is
        // returned instead of made again (a sign-up retried or sent twice by the browser).
        farms.lockForUser(me.getId().toString());
        var existing = farms.findOwnedByName(me.getId(), req.name().trim()).stream().findFirst();
        if (existing.isPresent()) {
            return FarmResponse.of(existing.get(), Role.OWNER);
        }
        Farm farm = new Farm(req.name().trim());
        apply(farm, req);
        farm = farms.save(farm);

        String name = me.getFullName() != null ? me.getFullName()
                : me.getEmail() != null ? me.getEmail().replaceAll("@.*", "") : "Owner";
        FarmMember owner = new FarmMember(farm.getId(), name, Role.OWNER);
        owner.setUserId(me.getId());
        owner.setEmail(me.getEmail());
        owner.setPhone(me.getPhone());
        owner.setLoginType(LoginType.SELF);
        owner.setJoinedOn(LocalDate.now(FARM_ZONE));
        members.save(owner);
        return FarmResponse.of(farm, Role.OWNER);
    }

    @Transactional(readOnly = true)
    public FarmResponse get(Long farmId, Role role) {
        return FarmResponse.of(load(farmId), role);
    }

    @Transactional
    public FarmResponse update(Long farmId, FarmRequest req) {
        Farm farm = load(farmId);
        farm.setName(req.name().trim());
        apply(farm, req);
        return FarmResponse.of(farm, Role.OWNER);
    }

    @Transactional(readOnly = true)
    public FarmSettings settings(Long farmId) {
        return load(farmId).getSettings();
    }

    /** Partial update: only the values present in the request change. */
    @Transactional
    public FarmSettings updateSettings(Long farmId, FarmSettings patch) {
        Farm farm = load(farmId);
        FarmSettings merged = farm.getSettings().merge(patch);
        farm.setSettings(merged);
        return merged;
    }

    private Farm load(Long farmId) {
        return farms.findById(farmId).orElseThrow(() -> ApiException.notFound("Farm not found."));
    }

    private static void apply(Farm farm, FarmRequest req) {
        farm.setVillage(trim(req.village()));
        farm.setDistrict(trim(req.district()));
        farm.setState(trim(req.state()));
        farm.setCountry(trim(req.country()));
        farm.setLatitude(req.latitude());
        farm.setLongitude(req.longitude());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
