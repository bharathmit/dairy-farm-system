package com.dairyfarm.feed;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.dairyfarm.feed.FeedViews.AnimalRation;
import com.dairyfarm.feed.FeedViews.BatchPreview;
import com.dairyfarm.feed.FeedViews.BatchRequest;
import com.dairyfarm.feed.FeedViews.BatchSaved;
import com.dairyfarm.feed.FeedViews.Category;
import com.dairyfarm.feed.FeedViews.FeedItem;
import com.dairyfarm.feed.FeedViews.FeedItemRequest;
import com.dairyfarm.feed.FeedViews.FeedNeed;
import com.dairyfarm.feed.FeedViews.FeedingRequest;
import com.dairyfarm.feed.FeedViews.FeedingSheet;
import com.dairyfarm.feed.FeedViews.Formula;
import com.dairyfarm.feed.FeedViews.FormulaRequest;
import com.dairyfarm.feed.FeedViews.Move;
import com.dairyfarm.feed.FeedViews.Overview;
import com.dairyfarm.feed.FeedViews.PurchaseRequest;
import com.dairyfarm.feed.FeedViews.PurchaseSaved;
import com.dairyfarm.feed.FeedViews.RationCategory;
import com.dairyfarm.feed.FeedViews.RationSummary;
import com.dairyfarm.feed.FeedViews.RuleRequest;
import com.dairyfarm.feed.FeedViews.RuleSet;
import com.dairyfarm.feed.FeedViews.Session;
import com.dairyfarm.feed.FeedViews.StockItem;
import com.dairyfarm.feed.FeedViews.StockRequest;
import com.dairyfarm.feed.FeedViews.Target;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/feed")
@Tag(name = "Feed & ration")
public class FeedController {

    private final FeedService feed;
    private final FormulaService formulas;
    private final FarmContext ctx;

    public FeedController(FeedService feed, FormulaService formulas, FarmContext ctx) {
        this.feed = feed;
        this.formulas = formulas;
        this.ctx = ctx;
    }

    @GetMapping("/overview")
    @Operation(summary = "Feed home: today's feeding sessions, daily need, stock with days left")
    public Overview overview(@PathVariable Long farmId, @RequestParam(required = false) LocalDate date) {
        return feed.overview(ctx.farmId(), date, ctx.isOwner());
    }

    // ---- items ----

    @GetMapping("/items")
    public List<FeedItem> items(@PathVariable Long farmId, @RequestParam(required = false) Category category,
                                @RequestParam(defaultValue = "false") boolean includeInactive) {
        return feed.list(ctx.farmId(), category, includeInactive, ctx.isOwner());
    }

    @GetMapping("/items/{id}")
    public FeedItem item(@PathVariable Long farmId, @PathVariable Long id) {
        return feed.get(ctx.farmId(), id, ctx.isOwner());
    }

    @PostMapping("/items")
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    public FeedItem createItem(@PathVariable Long farmId, @Valid @RequestBody FeedItemRequest req) {
        return feed.create(ctx.farmId(), req);
    }

    @PutMapping("/items/{id}")
    @OwnerOnly
    public FeedItem updateItem(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody FeedItemRequest req) {
        return feed.update(ctx.farmId(), id, req);
    }

    @DeleteMapping("/items/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long farmId, @PathVariable Long id) {
        feed.delete(ctx.farmId(), id);
    }

    @GetMapping("/items/{id}/moves")
    public List<Move> moves(@PathVariable Long farmId, @PathVariable Long id,
                            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return feed.moves(ctx.farmId(), id, null, from, to, ctx.isOwner());
    }

    @PostMapping("/items/{id}/stock")
    @Operation(summary = "Record own fodder harvested (HARVEST) or a stock correction (ADJUSTMENT, ±)")
    public StockItem stock(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody StockRequest req) {
        return feed.stock(ctx.farmId(), id, req, ctx.isOwner());
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseSaved purchase(@PathVariable Long farmId, @Valid @RequestBody PurchaseRequest req) {
        return feed.purchase(ctx.farmId(), req, ctx.isOwner());
    }

    @GetMapping("/purchases")
    public List<Move> purchases(@PathVariable Long farmId, @RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long itemId) {
        return feed.moves(ctx.farmId(), itemId, "PURCHASE", from, to, ctx.isOwner());
    }

    // ---- feeding ----

    @GetMapping("/feeding-sheet")
    @Operation(summary = "Planned (half the day's ration) and logged kg for a session and target")
    public FeedingSheet sheet(@PathVariable Long farmId, @RequestParam(required = false) LocalDate date,
                              @RequestParam(required = false) Session session, @RequestParam(required = false) Target target,
                              @RequestParam(required = false) Long groupId, @RequestParam(required = false) Long animalId) {
        return feed.sheet(ctx.farmId(), date, session, target, groupId, animalId);
    }

    @PutMapping("/feeding-sheet")
    public FeedingSheet saveSheet(@PathVariable Long farmId, @Valid @RequestBody FeedingRequest req) {
        return feed.saveSheet(ctx.farmId(), req);
    }

    // ---- formulas ----

    @GetMapping("/formulas")
    public List<Formula> formulas(@PathVariable Long farmId) {
        return formulas.list(ctx.farmId(), ctx.isOwner());
    }

    @GetMapping("/formulas/{id}")
    public Formula formula(@PathVariable Long farmId, @PathVariable Long id) {
        return formulas.get(ctx.farmId(), id, ctx.isOwner());
    }

    @PostMapping("/formulas")
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    public Formula createFormula(@PathVariable Long farmId, @Valid @RequestBody FormulaRequest req) {
        return formulas.save(ctx.farmId(), null, req);
    }

    @PutMapping("/formulas/{id}")
    @OwnerOnly
    public Formula updateFormula(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody FormulaRequest req) {
        return formulas.save(ctx.farmId(), id, req);
    }

    @DeleteMapping("/formulas/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFormula(@PathVariable Long farmId, @PathVariable Long id) {
        formulas.delete(ctx.farmId(), id);
    }

    @GetMapping("/formulas/{id}/batch-preview")
    public BatchPreview preview(@PathVariable Long farmId, @PathVariable Long id,
                                @RequestParam(required = false) BigDecimal batchKg) {
        return formulas.preview(ctx.farmId(), id, batchKg, ctx.isOwner());
    }

    @PostMapping("/formulas/{id}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Prepare a batch: uses ingredients from stock and adds the mix")
    public BatchSaved prepare(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody BatchRequest req) {
        return formulas.prepare(ctx.farmId(), id, req, ctx.memberId(), ctx.isOwner());
    }

    // ---- ration ----

    @GetMapping("/ration")
    public AnimalRation ration(@PathVariable Long farmId, @RequestParam Long animalId,
                               @RequestParam(required = false) LocalDate date) {
        return feed.ration(ctx.farmId(), animalId, date, ctx.isOwner());
    }

    @GetMapping("/ration/summary")
    public RationSummary summary(@PathVariable Long farmId, @RequestParam(required = false) LocalDate date) {
        return feed.summary(ctx.farmId(), date, ctx.isOwner());
    }

    @GetMapping("/need")
    @Operation(summary = "Farm feed need vs stock, ingredient need for batches, and shortfalls within N days")
    public FeedNeed need(@PathVariable Long farmId, @RequestParam(defaultValue = "7") int days) {
        return feed.need(ctx.farmId(), days, ctx.isOwner());
    }

    @GetMapping("/ration-rules")
    public List<RuleSet> rules(@PathVariable Long farmId) {
        return feed.rules(ctx.farmId());
    }

    @PutMapping("/ration-rules/{category}")
    @OwnerOnly
    public List<RuleSet> saveRule(@PathVariable Long farmId, @PathVariable RationCategory category,
                                  @Valid @RequestBody RuleRequest req) {
        return feed.saveRule(ctx.farmId(), category, req.effectiveFrom(), req.params());
    }
}
