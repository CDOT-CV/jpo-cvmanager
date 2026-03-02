package com.trihydro.rsuinfobridge.controller;

import com.trihydro.rsuinfobridge.models.tables.Rsu;
import com.trihydro.rsuinfobridge.models.tables.RsuOption;
import com.trihydro.rsuinfobridge.models.tables.SnmpCredential;
import com.trihydro.rsuinfobridge.models.tables.SnmpProtocol;
import com.trihydro.rsuinfobridge.service.RsuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RsuControllerTest {
    RsuService rsuService;
    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        rsuService = mock(RsuService.class);
    }

    @Test
    void testGetAll_Success() throws Exception {
        // Arrange
        List<Rsu> rsus = getMockData();
        when(rsuService.getAll(false)).thenReturn(rsus);
        mockMvc = initializeMockMvc();

        // Act
        ResultActions resultActions = mockMvc.perform(get("/rsus"));

        // Assert
        resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("myid"))
                .andExpect(jsonPath("$[0].ipv4Address").value("10.10.10.10"))
                .andExpect(jsonPath("$[1].id").value("myid2"))
                .andExpect(jsonPath("$[1].ipv4Address").value("10.10.10.11"));
    }

    @Test
    void testGetAllWithTimDepositEnabled_Success() throws Exception {
        // Arrange
        List<Rsu> rsus = getMockData();
        when(rsuService.getAll(true)).thenReturn(rsus);
        mockMvc = initializeMockMvc();

        // Act
        ResultActions resultActions = mockMvc.perform(get("/rsus?timDepositEnabled=true"));

        // Assert
        resultActions.andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].timDepositEnabled").value(true))
                .andExpect(jsonPath("$[1].timDepositEnabled").value(true));
    }

    List<Rsu> getMockData() throws UnknownHostException {
        List<Rsu> rsus = new java.util.ArrayList<>();

        SnmpProtocol snmpProtocol = new SnmpProtocol();
        snmpProtocol.setId(1);
        snmpProtocol.setProtocolCode("NTCIP1218");

        SnmpCredential snmpCredential1 = new SnmpCredential();
        snmpCredential1.setId(1);
        snmpCredential1.setNickname("myusername");
        snmpCredential1.setPassword("mypassword");

        SnmpCredential snmpCredential2 = new SnmpCredential();
        snmpCredential2.setId(2);
        snmpCredential2.setNickname("myusername2");
        snmpCredential2.setPassword("mypassword2");

        Point point1 = mock(Point.class);
        when(point1.getX()).thenReturn(39.73915);
        when(point1.getY()).thenReturn(-104.9847);

        Point point2 = mock(Point.class);
        when(point2.getX()).thenReturn(40.0);
        when(point2.getY()).thenReturn(105.0);

        RsuOption rsuOption = new RsuOption();
        rsuOption.setTimDeposit(true);

        Rsu rsu1 = Rsu.builder()
                .id(1)
                .ipv4Address(InetAddress.getByName("10.10.10.10"))
                .snmpProtocol(snmpProtocol)
                .snmpCredential(snmpCredential1)
                .geography(point1)
                .rsuOption(rsuOption)
                .build();
        rsus.add(rsu1);

        Rsu rsu2 = Rsu.builder()
                .id(2)
                .ipv4Address(InetAddress.getByName("10.10.10.11"))
                .snmpProtocol(snmpProtocol)
                .snmpCredential(snmpCredential2)
                .geography(point2)
                .rsuOption(rsuOption)
                .build();
        rsus.add(rsu2);

        return rsus;
    }

    MockMvc initializeMockMvc() {
        return MockMvcBuilders.standaloneSetup(new RsuController(rsuService)).build();
    }
}
