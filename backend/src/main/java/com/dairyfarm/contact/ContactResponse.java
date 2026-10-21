package com.dairyfarm.contact;

import java.math.BigDecimal;
import java.util.List;

import com.dairyfarm.contact.Contact.PayEvery;
import com.dairyfarm.contact.Contact.Role;

public record ContactResponse(Long id, String name, List<Role> roles, String phone, String village,
                              BigDecimal milkRate, PayEvery payEvery, String notes, boolean active) {

    public static ContactResponse of(Contact c, boolean showMoney) {
        return new ContactResponse(c.getId(), c.getName(), c.getRoles(), c.getPhone(), c.getVillage(),
                showMoney ? c.getMilkRate() : null, c.getPayEvery(), c.getNotes(), c.isActive());
    }

    /** Short form used inside milk sales and health records. */
    public record Ref(Long id, String name) {
        public static Ref of(Contact c) {
            return c == null ? null : new Ref(c.getId(), c.getName());
        }
    }
}
