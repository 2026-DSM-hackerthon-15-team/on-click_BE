package com.onclick.domain.visitor.repository;

import com.onclick.domain.visitor.entity.HourlyVisitorCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HourlyVisitorCountRepository extends JpaRepository<HourlyVisitorCount, Long> {

    Optional<HourlyVisitorCount> findByStoreIdAndBusinessDateAndHour(
            Long storeId,
            LocalDate businessDate,
            int hour
    );

    List<HourlyVisitorCount> findAllByStoreIdAndBusinessDateOrderByHourAsc(
            Long storeId,
            LocalDate businessDate
    );
}
