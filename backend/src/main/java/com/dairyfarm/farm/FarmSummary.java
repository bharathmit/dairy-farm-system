package com.dairyfarm.farm;

import com.dairyfarm.member.Role;

/** A farm as shown in the farm picker: the caller's role and the active herd size. */
public record FarmSummary(Long id, String name, String village, String district, String state,
                          Role role, Long memberId, long animalCount) {
}
