package com.onclick.domain.dashboard.controller;

import com.onclick.domain.dashboard.dto.ClosingSalesForecastResponse;
import com.onclick.domain.dashboard.dto.DashboardSummaryResponse;
import com.onclick.domain.dashboard.dto.HourlySalesResponse;
import com.onclick.domain.dashboard.dto.HourlyVisitorsResponse;
import com.onclick.domain.dashboard.dto.TomorrowVisitorsForecastResponse;
import com.onclick.domain.dashboard.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores/{storeId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return dashboardService.getSummary(jwt, storeId);
    }

    @GetMapping("/hourly-sales")
    public HourlySalesResponse getHourlySales(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return dashboardService.getHourlySales(jwt, storeId);
    }

    @GetMapping("/hourly-visitors")
    public HourlyVisitorsResponse getHourlyVisitors(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return dashboardService.getHourlyVisitors(jwt, storeId);
    }

    @GetMapping("/closing-sales-forecast")
    public ClosingSalesForecastResponse getClosingSalesForecast(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return dashboardService.getClosingSalesForecast(jwt, storeId);
    }

    @GetMapping("/tomorrow-visitors-forecast")
    public TomorrowVisitorsForecastResponse getTomorrowVisitorsForecast(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long storeId
    ) {
        return dashboardService.getTomorrowVisitorsForecast(jwt, storeId);
    }
}
