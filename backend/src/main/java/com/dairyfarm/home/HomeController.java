package com.dairyfarm.home;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.dairyfarm.home.HomeViews.Barn;
import com.dairyfarm.home.HomeViews.BarnRequest;
import com.dairyfarm.home.HomeViews.Checklist;
import com.dairyfarm.home.HomeViews.Home;
import com.dairyfarm.home.HomeViews.TaskRequest;
import com.dairyfarm.home.HomeViews.TickRequest;
import com.dairyfarm.record.DailyDates;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/farms/{farmId}")
@Tag(name = "Home")
public class HomeController {

    private final HomeService home;
    private final FarmContext ctx;

    public HomeController(HomeService home, FarmContext ctx) {
        this.home = home;
        this.ctx = ctx;
    }

    @GetMapping("/home")
    @Operation(summary = "Home screen for the caller's role (workers get no money)")
    public Home home(@PathVariable Long farmId) {
        return home.home(ctx.farmId(), ctx.isOwner());
    }

    @GetMapping("/checklist")
    public Checklist checklist(@PathVariable Long farmId, @RequestParam(required = false) LocalDate date) {
        return home.checklist(ctx.farmId(), date == null ? DailyDates.today() : date, ctx.isOwner());
    }

    @PutMapping("/checklist/{taskKey}")
    @Operation(summary = "Tick or untick a checklist task for a day")
    public Checklist tick(@PathVariable Long farmId, @PathVariable String taskKey, @RequestParam(required = false) LocalDate date,
                          @Valid @RequestBody TickRequest req) {
        return home.tick(ctx.farmId(), taskKey, date == null ? DailyDates.today() : date, req.done(), ctx.isOwner());
    }

    @PostMapping("/checklist/tasks")
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    public Checklist createTask(@PathVariable Long farmId, @Valid @RequestBody TaskRequest req) {
        return home.createTask(ctx.farmId(), req);
    }

    @PutMapping("/checklist/tasks/{id}")
    @OwnerOnly
    public Checklist updateTask(@PathVariable Long farmId, @PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        return home.updateTask(ctx.farmId(), id, req);
    }

    @DeleteMapping("/checklist/tasks/{id}")
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long farmId, @PathVariable Long id) {
        home.deleteTask(ctx.farmId(), id);
    }

    @PostMapping("/barn-readings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record barn temperature and humidity; THI and advice are worked out")
    public Barn addBarn(@PathVariable Long farmId, @Valid @RequestBody BarnRequest req) {
        return home.addBarn(ctx.farmId(), req);
    }

    @GetMapping("/barn-readings/latest")
    public ResponseEntity<Barn> latestBarn(@PathVariable Long farmId) {
        Barn b = home.latestBarn(ctx.farmId());
        return b == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(b);
    }
}
