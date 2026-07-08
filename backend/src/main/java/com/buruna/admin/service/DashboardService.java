package com.buruna.admin.service;

import com.buruna.admin.dto.DashboardResponse;
import com.buruna.admin.dto.UserStorageResponse;
import com.buruna.identity.application.GetUserSummaryUseCase;
import com.buruna.identity.application.UserSummary;
import com.buruna.identity.application.admin.CountActiveUsersUseCase;
import com.buruna.manga.application.admin.GetStorageByOwnerUseCase;
import com.buruna.manga.application.admin.OwnerStorageUsage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1_073_741_824L);

    private final CountActiveUsersUseCase countActiveUsers;
    private final GetStorageByOwnerUseCase getStorageByOwner;
    private final GetUserSummaryUseCase getUserSummary;

    public DashboardService(CountActiveUsersUseCase countActiveUsers,
                            GetStorageByOwnerUseCase getStorageByOwner,
                            GetUserSummaryUseCase getUserSummary) {
        this.countActiveUsers = countActiveUsers;
        this.getStorageByOwner = getStorageByOwner;
        this.getUserSummary = getUserSummary;
    }

    public DashboardResponse getDashboard() {
        long activeUsers = countActiveUsers.handle();

        List<OwnerStorageUsage> storageRows = getStorageByOwner.handle();

        long totalBytes = storageRows.stream().mapToLong(OwnerStorageUsage::totalBytes).sum();
        BigDecimal totalGb = bytesToGb(totalBytes);

        List<UUID> ownerIds = storageRows.stream().map(OwnerStorageUsage::ownerId).distinct().toList();

        Map<UUID, UserSummary> userMap = getUserSummary.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));

        List<UserStorageResponse> storageByUser = storageRows.stream()
                .map(r -> {
                    UserSummary user = userMap.get(r.ownerId());
                    String username = user != null ? user.username() : "—";
                    BigDecimal quotaGb = user != null ? user.quotaGb() : BigDecimal.ZERO;
                    return new UserStorageResponse(
                            r.ownerId(),
                            username,
                            bytesToGb(r.totalBytes()),
                            quotaGb
                    );
                })
                .sorted((a, b) -> b.usedGb().compareTo(a.usedGb()))
                .toList();

        return new DashboardResponse(activeUsers, totalGb, storageByUser);
    }

    private BigDecimal bytesToGb(long bytes) {
        return BigDecimal.valueOf(bytes)
                .divide(BYTES_PER_GB, 2, RoundingMode.HALF_UP);
    }
}
