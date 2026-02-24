package com.trihydro.rsuinfobridge.controller;

import com.trihydro.rsuinfobridge.service.RsuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RsuControllerTest {
    RsuService rsuService;
    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        rsuService = new RsuService();
        mockMvc = MockMvcBuilders.standaloneSetup(new RsuController(rsuService)).build();
    }

    @Test
    void testGetAllRsus_Success() throws Exception {
        // Act
        ResultActions resultActions = mockMvc.perform(get("/rsus/all"));

        // Assert
        resultActions.andExpect(status().isOk());
    }

    @Test
    void testGetAllRsusWithTimDepositEnabled_Success() throws Exception {
        // Act
        ResultActions resultActions = mockMvc.perform(get("/rsus/all-tim-deposit-enabled"));

        // Assert
        resultActions.andExpect(status().isOk());
    }
}
