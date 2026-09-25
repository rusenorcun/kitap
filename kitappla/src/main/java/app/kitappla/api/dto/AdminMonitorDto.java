package app.kitappla.api.dto;

import java.util.List;

public record AdminMonitorDto(
        MonitorSummaryDto summary,
        List<ActiveSessionDto> sessions
) {

    public record MonitorSummaryDto(
            long activeSessions,
            long onlineUsers,
            long onlineAdmins,
            long onlineStudents,
            long persistentLogins,
            String uptimeFormatted,
            long uptimeMillis,
            String startTime,
            long usedMemoryMb,
            long totalMemoryMb,
            long maxMemoryMb,
            int memoryPercent,
            int availableProcessors,
            int threadCount,
            int peakThreadCount,
            boolean dbConnected,
            String dbProduct,
            Integer dbActiveConnections,
            Integer dbIdleConnections,
            long loginFailuresTracked
    ) {}

    public record ActiveSessionDto(
            String id,
            String maskedSessionId,
            Long userId,
            String userName,
            String userEmail,
            String initials,
            boolean admin,
            boolean student,
            boolean blocked,
            String lastRequestTime,
            String lastRequestRelative,
            boolean currentSession
    ) {}
}
