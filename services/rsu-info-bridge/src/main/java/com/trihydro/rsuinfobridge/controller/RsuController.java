package com.trihydro.rsuinfobridge.controller;

import com.trihydro.rsuinfobridge.mapper.RsuDtoMapper;
import com.trihydro.rsuinfobridge.models.dtos.RsuDto;
import com.trihydro.rsuinfobridge.service.RsuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rsus")
@RequiredArgsConstructor
@Validated
public class RsuController {
    private final RsuService rsuService;
    private final RsuDtoMapper rsuDtoMapper;

    @GetMapping
    public List<@Valid RsuDto> getAll(@RequestParam(defaultValue = "false") boolean timDepositEnabled) {
        return rsuDtoMapper.toDtoList(rsuService.getAll(timDepositEnabled));
    }
}
