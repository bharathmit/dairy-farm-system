package com.dairyfarm.procurement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.dairyfarm.contact.ContactResponse.Ref;
import com.dairyfarm.feed.FeedViews.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public final class ProcurementViews {

    private ProcurementViews() {
    }

    public enum NoteStatus { PLANNED, ORDERED, RECEIVED, CANCELLED }

    public enum NoteSource { MANUAL, LOW_STOCK, FEED_NEED }

    public record ItemInfo(Long id, String name, String type) {
    }

    public record Suggestion(ItemInfo item, BigDecimal stock, String unit, BigDecimal reorderLevel, BigDecimal daysLeft,
                             String reason) {
    }

    public record Note(Long id, ItemInfo item, String title, BigDecimal quantity, String unit, BigDecimal expectedPrice,
                       LocalDate neededBy, Ref supplier, NoteStatus status, LocalDate orderedOn, LocalDate receivedOn,
                       BigDecimal paidAmount, NoteSource source, String notes) {
    }

    public record Counts(int all, int planned, int ordered, int received) {
    }

    public record Board(Counts counts, List<Suggestion> suggestions, List<Note> notes) {
    }

    public record NoteRequest(Long itemId, @Size(max = 160) String title,
                              @DecimalMin(value = "0.001", message = "Enter a quantity above 0") BigDecimal quantity,
                              @Size(max = 6) String unit,
                              @DecimalMin(value = "0", message = "Price can't be negative") BigDecimal expectedPrice,
                              LocalDate neededBy, Long supplierContactId, @Size(max = 2000) String notes,
                              NoteSource source, NoteStatus status) {
    }

    public record OrderRequest(LocalDate orderedOn) {
    }

    public record ReceiveRequest(LocalDate receivedOn,
                                 @DecimalMin(value = "0.001", message = "Enter a quantity above 0") BigDecimal quantity,
                                 @DecimalMin(value = "0", message = "Amount can't be negative") BigDecimal totalAmount,
                                 PaymentMethod paymentMethod, Boolean paid) {
    }
}
