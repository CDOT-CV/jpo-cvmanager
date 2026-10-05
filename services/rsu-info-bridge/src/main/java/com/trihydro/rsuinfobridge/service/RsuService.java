package com.trihydro.rsuinfobridge.service;

import com.trihydro.rsuinfobridge.models.dtos.RsuFilter;
import com.trihydro.rsuinfobridge.models.tables.Rsu;
import com.trihydro.rsuinfobridge.repository.RsuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RsuService {
    private final RsuRepository rsuRepository;

    public List<Rsu> getAll(RsuFilter filter) {
        String route = filter.primaryRoute();
        boolean timDepositOnly = filter.timDepositEnabledOnly();

        if (route != null) {
            return timDepositOnly
                    ? rsuRepository.findByPrimaryRouteIgnoreCaseAndRsuOptionTimDepositIsTrue(route)
                    : rsuRepository.findByPrimaryRouteIgnoreCase(route);
        }
        return timDepositOnly
                ? rsuRepository.findByRsuOptionTimDepositIsTrue()
                : rsuRepository.findAll();
    }
}