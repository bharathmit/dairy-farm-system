package com.dairyfarm.contact;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.contact.Contact.Role;

@Service
public class ContactService {

    private final ContactRepository contacts;

    public ContactService(ContactRepository contacts) {
        this.contacts = contacts;
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> list(Long farmId, Role role, Boolean active, boolean showMoney) {
        return contacts.findByFarmIdOrderByNameAsc(farmId).stream()
                .filter(c -> role == null || c.hasRole(role))
                .filter(c -> active == null || c.isActive() == active)
                .map(c -> ContactResponse.of(c, showMoney))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContactResponse get(Long farmId, Long id, boolean showMoney) {
        return ContactResponse.of(load(farmId, id), showMoney);
    }

    @Transactional
    public ContactResponse create(Long farmId, ContactRequest req, boolean showMoney) {
        if (contacts.existsByFarmIdAndNameIgnoreCase(farmId, req.name().trim())) {
            throw ApiException.conflict("A contact named \"" + req.name().trim() + "\" already exists.");
        }
        Contact c = new Contact(farmId);
        apply(c, req, true);
        return ContactResponse.of(contacts.save(c), showMoney);
    }

    @Transactional
    public ContactResponse update(Long farmId, Long id, ContactRequest req, boolean showMoney) {
        Contact c = load(farmId, id);
        if (contacts.existsByFarmIdAndNameIgnoreCaseAndIdNot(farmId, req.name().trim(), id)) {
            throw ApiException.conflict("A contact named \"" + req.name().trim() + "\" already exists.");
        }
        // Workers don't see the buyer's rate, so a worker's edit keeps it.
        apply(c, req, showMoney || req.milkRate() != null);
        return ContactResponse.of(c, showMoney);
    }

    /** Contacts already used on records are kept for history; make them inactive instead. */
    @Transactional
    public void delete(Long farmId, Long id) {
        Contact c = load(farmId, id);
        if (contacts.countUses(id) > 0) {
            throw ApiException.conflict("This contact is used on milk sales or health records. Make it inactive instead.");
        }
        contacts.delete(c);
    }

    /** A contact of this farm that has the role, for use on a record. Null id gives null. */
    @Transactional(readOnly = true)
    public Contact require(Long farmId, Long id, Role role) {
        if (id == null) {
            return null;
        }
        Contact c = contacts.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> ApiException.badRequest("That contact isn't on this farm."));
        if (!c.hasRole(role)) {
            throw ApiException.badRequest(c.getName() + " isn't saved as a " + label(role) + ". Edit the contact to add it.");
        }
        return c;
    }

    Contact load(Long farmId, Long id) {
        return contacts.findByIdAndFarmId(id, farmId).orElseThrow(() -> ApiException.notFound("Contact not found."));
    }

    private static String label(Role role) {
        return switch (role) {
            case BUYER -> "milk buyer";
            case VET -> "vet";
            case AI_TECH -> "AI technician";
            case SUPPLIER -> "supplier";
            case OTHER -> "contact";
        };
    }

    private static void apply(Contact c, ContactRequest req, boolean setRate) {
        c.setName(req.name().trim());
        c.setRoles(req.roles());
        c.setPhone(blank(req.phone()));
        c.setVillage(blank(req.village()));
        if (setRate) {
            c.setMilkRate(req.milkRate());
        }
        c.setPayEvery(req.payEvery());
        c.setNotes(blank(req.notes()));
        if (req.active() != null) {
            c.setActive(req.active());
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
