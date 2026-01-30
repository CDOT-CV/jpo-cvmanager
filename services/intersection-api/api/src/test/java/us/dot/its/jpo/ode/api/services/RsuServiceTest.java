package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import us.dot.its.jpo.ode.api.mappers.RsuInfoMapper;
import us.dot.its.jpo.ode.api.models.devices.management.ModifyRsuAllowedSelections;
import us.dot.its.jpo.ode.api.models.postgres.dtos.RsuInfoDto;
import us.dot.its.jpo.ode.api.models.SimplePosition;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpProtocolRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RsuManagementServiceTest {

    @Mock
    private RsuRepository rsuRepository;

    @Mock
    private RsuCredentialRepository rsuCredentialRepository;

    @Mock
    private SnmpCredentialRepository snmpCredentialRepository;

    @Mock
    private SnmpProtocolRepository snmpProtocolRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RsuInfoMapper rsuMapper;

    @InjectMocks
    private RsuManagementService rsuManagementService;

    @Test
    void testGetRsuInfo_Success() throws UnknownHostException {
        // Arrange
        String ipAddress = "192.168.1.100";
        InetAddress inetAddress = InetAddress.getByName(ipAddress);

        Rsu mockRsu = new Rsu();
        RsuInfoDto mockDto = new RsuInfoDto(
                ipAddress,
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU123",
                "SCMS123",
                "Model X",
                "ssh-group",
                "snmp-group",
                "v3",
                Arrays.asList("Org1", "Org2"));

        when(rsuRepository.findByIpv4Address(inetAddress)).thenReturn(mockRsu);
        when(rsuMapper.toDto(mockRsu)).thenReturn(mockDto);

        // Act
        RsuInfoDto result = rsuManagementService.getRsuInfo(ipAddress);

        // Assert
        assertNotNull(result);
        assertEquals(ipAddress, result.getIpv4Address());
        assertEquals(123.4, result.getMilepost());
        assertEquals("I-25", result.getPrimaryRoute());
        verify(rsuRepository).findByIpv4Address(inetAddress);
        verify(rsuMapper).toDto(mockRsu);
    }

    @Test
    void testGetRsuInfo_NotFound() throws UnknownHostException {
        // Arrange
        String ipAddress = "192.168.1.100";
        InetAddress inetAddress = InetAddress.getByName(ipAddress);

        when(rsuRepository.findByIpv4Address(inetAddress)).thenReturn(null);

        // Act
        RsuInfoDto result = rsuManagementService.getRsuInfo(ipAddress);

        // Assert
        assertNull(result);
        verify(rsuRepository).findByIpv4Address(inetAddress);
        verify(rsuMapper, never()).toDto(any());
    }

    @Test
    void testGetRsuInfo_InvalidIpAddress() {
        // Arrange
        String invalidIpAddress = "invalid-ip";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> rsuManagementService.getRsuInfo(invalidIpAddress));

        assertTrue(exception.getMessage().contains("Invalid IP address"));
        assertTrue(exception.getCause() instanceof UnknownHostException);
        verify(rsuRepository, never()).findByIpv4Address(any());
    }

    @Test
    void testGetAllRsuInfo_Success() {
        // Arrange
        String orgName = "TestOrg";
        Pageable pageable = PageRequest.of(0, 10);

        Rsu rsu1 = new Rsu();
        Rsu rsu2 = new Rsu();
        List<Rsu> rsuList = Arrays.asList(rsu1, rsu2);
        Page<Rsu> rsuPage = new PageImpl<>(rsuList, pageable, 2);

        RsuInfoDto dto1 = new RsuInfoDto(
                "192.168.1.100",
                new SimplePosition(39.7392, -105.0844),
                123.4,
                "I-25",
                "RSU1",
                "SCMS1",
                "Model X",
                "ssh1",
                "snmp1",
                "v3",
                Arrays.asList("TestOrg"));
        RsuInfoDto dto2 = new RsuInfoDto(
                "192.168.1.101",
                new SimplePosition(39.7400, -105.0850),
                124.5,
                "I-70",
                "RSU2",
                "SCMS2",
                "Model Y",
                "ssh2",
                "snmp2",
                "v2c",
                Arrays.asList("TestOrg"));

        when(rsuRepository.findAllByOrganization(orgName, pageable)).thenReturn(rsuPage);
        when(rsuMapper.toDto(rsu1)).thenReturn(dto1);
        when(rsuMapper.toDto(rsu2)).thenReturn(dto2);

        // Act
        Page<RsuInfoDto> result = rsuManagementService.getAllRsuInfo(orgName, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals("192.168.1.100", result.getContent().get(0).getIpv4Address());
        assertEquals("192.168.1.101", result.getContent().get(1).getIpv4Address());
        verify(rsuRepository).findAllByOrganization(orgName, pageable);
        verify(rsuMapper, times(2)).toDto(any(Rsu.class));
    }

    @Test
    void testGetAllRsuInfo_EmptyResult() {
        // Arrange
        String orgName = "EmptyOrg";
        Pageable pageable = PageRequest.of(0, 10);
        Page<Rsu> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(rsuRepository.findAllByOrganization(orgName, pageable)).thenReturn(emptyPage);

        // Act
        Page<RsuInfoDto> result = rsuManagementService.getAllRsuInfo(orgName, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
        verify(rsuRepository).findAllByOrganization(orgName, pageable);
        verify(rsuMapper, never()).toDto(any());
    }

    @Test
    void testGetAllowedSelections_Success() {
        // Arrange
        String username = "testuser@example.com";

        List<String> primaryRoutes = Arrays.asList("I-25", "I-70", "US-36");

        List<RsuRepository.RsuModelProjection> rsuModels = Arrays.asList(
                createRsuModelProjection("Commsignia", "ITS-RS4-M"),
                createRsuModelProjection("Yunex", "RSU-2X"));

        List<String> sshCredentials = Arrays.asList("ssh-group-1", "ssh-group-2");
        List<String> snmpCredentials = Arrays.asList("snmp-group-1", "snmp-group-2");
        List<String> snmpVersions = Arrays.asList("v2c", "v3");

        List<UserRepository.UserOrgRoleProjection> userOrgRoles = Arrays.asList(
                createUserOrgRoleProjection("testuser@example.com", "Org1", "Admin"),
                createUserOrgRoleProjection("testuser@example.com", "Org2", "User"));

        when(rsuRepository.findAllPrimaryRoutes()).thenReturn(primaryRoutes);
        when(rsuRepository.findAllRsuModels()).thenReturn(rsuModels);
        when(rsuCredentialRepository.findAllNicknames()).thenReturn(sshCredentials);
        when(snmpCredentialRepository.findAllNicknames()).thenReturn(snmpCredentials);
        when(snmpProtocolRepository.findAllNicknames()).thenReturn(snmpVersions);
        when(userRepository.findUserOrgRoles(username)).thenReturn(userOrgRoles);

        // Act
        ModifyRsuAllowedSelections result = rsuManagementService.getAllowedSelections(username);

        // Assert
        assertNotNull(result);

        assertEquals(3, result.getPrimaryRoutes().size());
        assertTrue(result.getPrimaryRoutes().contains("I-25"));

        assertEquals(2, result.getRsuModels().size());
        assertTrue(result.getRsuModels().contains("Commsignia ITS-RS4-M"));
        assertTrue(result.getRsuModels().contains("Yunex RSU-2X"));

        assertEquals(2, result.getSshCredentialGroups().size());
        assertTrue(result.getSshCredentialGroups().contains("ssh-group-1"));

        assertEquals(2, result.getSnmpCredentialGroups().size());
        assertTrue(result.getSnmpCredentialGroups().contains("snmp-group-1"));

        assertEquals(2, result.getSnmpVersionGroups().size());
        assertTrue(result.getSnmpVersionGroups().contains("v2c"));

        assertEquals(2, result.getOrganizations().size());
        assertTrue(result.getOrganizations().contains("Org1"));
        assertTrue(result.getOrganizations().contains("Org2"));

        verify(rsuRepository).findAllPrimaryRoutes();
        verify(rsuRepository).findAllRsuModels();
        verify(rsuCredentialRepository).findAllNicknames();
        verify(snmpCredentialRepository).findAllNicknames();
        verify(snmpProtocolRepository).findAllNicknames();
        verify(userRepository).findUserOrgRoles(username);
    }

    @Test
    void testGetAllowedSelections_EmptyResults() {
        // Arrange
        String username = "newuser@example.com";

        when(rsuRepository.findAllPrimaryRoutes()).thenReturn(List.of());
        when(rsuRepository.findAllRsuModels()).thenReturn(List.of());
        when(rsuCredentialRepository.findAllNicknames()).thenReturn(List.of());
        when(snmpCredentialRepository.findAllNicknames()).thenReturn(List.of());
        when(snmpProtocolRepository.findAllNicknames()).thenReturn(List.of());
        when(userRepository.findUserOrgRoles(username)).thenReturn(List.of());

        // Act
        ModifyRsuAllowedSelections result = rsuManagementService.getAllowedSelections(username);

        // Assert
        assertNotNull(result);
        assertTrue(result.getPrimaryRoutes().isEmpty());
        assertTrue(result.getRsuModels().isEmpty());
        assertTrue(result.getSshCredentialGroups().isEmpty());
        assertTrue(result.getSnmpCredentialGroups().isEmpty());
        assertTrue(result.getSnmpVersionGroups().isEmpty());
        assertTrue(result.getOrganizations().isEmpty());
    }

    // Helper methods to create mock projections
    private RsuRepository.RsuModelProjection createRsuModelProjection(String manufacturer, String model) {
        return new RsuRepository.RsuModelProjection() {
            @Override
            public String getManufacturer() {
                return manufacturer;
            }

            @Override
            public String getModel() {
                return model;
            }
        };
    }

    private UserRepository.UserOrgRoleProjection createUserOrgRoleProjection(String email, String org, String role) {
        return new UserRepository.UserOrgRoleProjection() {
            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public String getOrganizationName() {
                return org;
            }

            @Override
            public String getRoleName() {
                return role;
            }
        };
    }
}