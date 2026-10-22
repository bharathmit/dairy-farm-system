package com.dairyfarm.report;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.report.ReportViews.Compare;
import com.dairyfarm.report.ReportViews.CullAdvisor;
import com.dairyfarm.report.ReportViews.CullRow;
import com.dairyfarm.report.ReportViews.Report;
import com.dairyfarm.report.ReportViews.ReportType;
import com.dairyfarm.report.ReportViews.Summary;
import com.dairyfarm.security.FarmContext;
import com.dairyfarm.security.OwnerOnly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/reports")
@Tag(name = "Reports")
@OwnerOnly
public class ReportController {

    private final ReportService reports;
    private final FarmContext ctx;

    public ReportController(ReportService reports, FarmContext ctx) {
        this.reports = reports;
        this.ctx = ctx;
    }

    @GetMapping("/summary")
    public Summary summary(@PathVariable Long farmId, @RequestParam(required = false) LocalDate from,
                           @RequestParam(required = false) LocalDate to) {
        return reports.summary(ctx.farmId(), from, to);
    }

    @GetMapping("/cull-advisor")
    @Operation(summary = "Keep / monitor / dry-off / cull suggestions for adult cows (advice only)")
    public CullAdvisor cullAdvisor(@PathVariable Long farmId) {
        return reports.cullAdvisor(ctx.farmId());
    }

    @PostMapping("/cull-advisor/{animalId}/keep")
    @Operation(summary = "Keep this cow: not flagged for culling for 90 days")
    public CullRow keep(@PathVariable Long farmId, @PathVariable Long animalId) {
        return reports.keep(ctx.farmId(), animalId);
    }

    @PostMapping("/cull-advisor/{animalId}/dry")
    @Operation(summary = "Mark a milking cow as dry")
    public CullRow dry(@PathVariable Long farmId, @PathVariable Long animalId) {
        return reports.dry(ctx.farmId(), animalId);
    }

    @GetMapping("/compare")
    public Compare compare(@PathVariable Long farmId, @RequestParam Long a, @RequestParam Long b) {
        return reports.compare(ctx.farmId(), a, b);
    }

    @GetMapping("/{type}.csv")
    @Operation(summary = "Report as a CSV download (milk, cows, breeding, feed, health, pnl)")
    public ResponseEntity<byte[]> csv(@PathVariable Long farmId, @PathVariable String type,
                                      @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        Report r = reports.report(ctx.farmId(), type(type), from, to);
        byte[] body = ("﻿" + reports.csv(r)).getBytes(StandardCharsets.UTF_8);
        String name = r.type() + "-" + r.period().from() + "-to-" + r.period().to() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }

    @GetMapping("/{type}")
    @Operation(summary = "Report data as JSON (the app prints it as PDF)")
    public Report report(@PathVariable Long farmId, @PathVariable String type, @RequestParam(required = false) LocalDate from,
                         @RequestParam(required = false) LocalDate to) {
        return reports.report(ctx.farmId(), type(type), from, to);
    }

    private static ReportType type(String s) {
        try {
            return ReportType.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Unknown report " + s + ". Use milk, cows, breeding, feed, health or pnl.");
        }
    }
}
