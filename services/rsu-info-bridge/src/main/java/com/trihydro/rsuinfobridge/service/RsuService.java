package com.trihydro.rsuinfobridge.service;

import com.trihydro.rsuinfobridge.repository.ExampleDataRepository;
import com.trihydro.rsuinfobridge.models.dtos.RsuDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RsuService {

    public List<RsuDto> getAllRsus() {
        return ExampleDataRepository.getData();
    }

    public List<RsuDto> getAllRsusWithTimDepositEnabled() {
        return ExampleDataRepository.getData();
    }
}
