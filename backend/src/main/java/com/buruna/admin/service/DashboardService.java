package com.buruna.admin.service;

import com.buruna.admin.dto.DashboardResponse;
import com.buruna.admin.dto.UserStorageResponse;
import com.buruna.manga.repository.VolumeStorageProjection;
import com.buruna.manga.repository.VolumeRepository;
import com.buruna.user.domain.User;
import com.buruna.user.domain.UserStatus;
import com.buruna.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final BigDecimal BYTES_PER_GB = BigDecimal.valueOf(1_073_741_824L);

    private final UserRepository userRepository;
    private final VolumeRepository volumeRepository;

    public DashboardService(UserRepository userRepository,
                            VolumeRepository volumeRepository) {
        this.userRepository = userRepository;
        this.volumeRepository = volumeRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);

        List<VolumeStorageProjection> storageRows = volumeRepository.findStorageByOwner();

        long totalBytes = storageRows.stream()
                .mapToLong(r -> r.getTotalBytes() != null ? r.getTotalBytes() : 0L)
                .sum();
        BigDecimal totalGb = bytesToGb(totalBytes);

        List<UUID> ownerIds = storageRows.stream()
                .map(VolumeStorageProjection::getOwnerId)
                .distinct()
                .toList();

        Map<UUID, User> userMap = userRepository.findAllById(ownerIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<UserStorageResponse> storageByUser = storageRows.stream()
                .map(r -> {
                    User user = userMap.get(r.getOwnerId());
                    String username = user != null ? user.getUsername() : "—";
                    BigDecimal quotaGb = user != null ? user.getQuotaGb() : BigDecimal.ZERO;
                    return new UserStorageResponse(
                            r.getOwnerId(),
                            username,
                            bytesToGb(r.getTotalBytes() != null ? r.getTotalBytes() : 0L),
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