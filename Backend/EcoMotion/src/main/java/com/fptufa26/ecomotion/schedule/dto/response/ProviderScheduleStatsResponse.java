package com.fptufa26.ecomotion.schedule.dto.response;

public record ProviderScheduleStatsResponse(
        long totalActiveBlocks,
        long activeMaintenance,
        long activeBlocked,
        long upcomingBookings
) {
}
