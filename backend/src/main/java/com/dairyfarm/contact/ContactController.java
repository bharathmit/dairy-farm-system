package com.dairyfarm.contact;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.contact.Contact.Role;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Any member can add or edit contacts (a worker logging a vet visit adds the vet); only owners delete. */
@RestController
@RequestMapping("/api/v1/farms/{farmId}/contacts")
@Tag(name = "Contacts")
public class ContactController {

    private final ContactService contacts;
    private final FarmContext ctx;

    public ContactController(ContactService contacts, FarmContext ctx) {
        this.contacts = contacts;
        this.ctx = ctx;
    }

    @GetMapping
    @Operation(summary = "Contacts, optionally only one type (e.g. role=BUYER) or only active ones")
    public List<ContactResponse> list(@PathVariable Long farmId, @RequestParam(required = false) Role role,
                                      @RequestParam(required = false) Boolean active) {
        return contacts.list(ctx.farmId(), role, active, ctx.isOwner());
    }

    @GetMapping("/{id}")
    public ContactResponse get(@PathVariable Long farmId, @PathVariable Long id) {
        return contacts.get(ctx.farmId(), id, ctx.isOwner());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse create(@PathVariable Long farmId, @Valid @RequestBody ContactRequest req) {
        return contacts.create(ctx.farmId(), req, ctx.isOwner());
    }

    @PutMapping("/{id}")
    public ContactResponse update(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody ContactRequest req) {
        return contacts.update(ctx.farmId(), id, req, ctx.isOwner());
    }

    @DeleteMapping("/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a contact that isn't used on any record")
    public void delete(@PathVariable Long farmId, @PathVariable Long id) {
        contacts.delete(ctx.farmId(), id);
    }
}
