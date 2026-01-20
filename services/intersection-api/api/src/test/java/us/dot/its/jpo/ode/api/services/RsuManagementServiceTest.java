package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import us.dot.its.jpo.ode.api.models.devices.management.ModifyRsuAllowedSelections;
import us.dot.its.jpo.ode.api.models.postgres.derived.RsuModelWithManufacturer;
import us.dot.its.jpo.ode.api.models.postgres.derived.UserOrgRole;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialsRepository;
import us.dot.its.jpo.ode.api.repositories.RsuModelsRepository;
import us.dot.its.jpo.ode.api.repositories.RsusRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialsRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpProtocolsRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RsuManagementServiceTest {

    @Mock
    private RsusRepository rsusRepository;

    @Mock
    private RsuModelsRepository rsuModelsRepository;

    @Mock
    private RsuCredentialsRepository rsuCredentialsRepository;

    @Mock
    private SnmpCredentialsRepository snmpCredentialsRepository;

    @Mock
    private SnmpProtocolsRepository snmpProtocolsRepository;

    @Mock
    private PostgresService postgresService;

    @InjectMocks
    private RsuManagementService rsuManagementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllowedSelections_withAllData() {
        // Arrange
        String username = "testuser@example.com";

        List<String> primaryRoutes = Arrays.asList("I-25", "I-70", "US-36");
        List<RsuModelWithManufacturer> models = Arrays.asList(
                new RsuModelWithManufacturer("Commsignia", "ITS-RS4-M"),
                new RsuModelWithManufacturer("Yunex", "RSU-10X")
        );
        List<String> sshCredentials = Arrays.asList("group1", "group2");
        List<String> snmpCredentials = Arrays.asList("snmp_group1", "snmp_group2");
        List<String> snmpVersions = Arrays.asList("1218", "41");
        List<UserOrgRole> userOrgRoles = Arrays.asList(
                new UserOrgRole("testuser@example.com", "CDOT", "admin"),
                new UserOrgRole("testuser@example.com", "City of Denver", "user")
        );

        when(rsusRepository.findDistinctPrimaryRoutes()).thenReturn(primaryRoutes);
        when(rsuModelsRepository.findAllModelsWithManufacturers()).thenReturn(models);
        when(rsuCredentialsRepository.findAllNicknames()).thenReturn(sshCredentials);
        when(snmpCredentialsRepository.findAllNicknames()).thenReturn(snmpCredentials);
        when(snmpProtocolsRepository.findAllNicknames()).thenReturn(snmpVersions);
        when(postgresService.findUserOrgRoles(username)).thenReturn(userOrgRoles);

        // Act
        ModifyRsuAllowedSelections result = rsuManagementService.getAllowedSelections(username);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getPrimaryRoutes().size());
        assertEquals("I-25", result.getPrimaryRoutes().get(0));
        assertEquals("I-70", result.getPrimaryRoutes().get(1));
        assertEquals("US-36", result.getPrimaryRoutes().get(2));

        assertEquals(2, result.getRsuModels().size());
        assertEquals("Commsignia ITS-RS4-M", result.getRsuModels().get(0));
        assertEquals("Yunex RSU-10X", result.getRsuModels().get(1));

        assertEquals(2, result.getSshCredentialGroups().size());
        assertEquals("group1", result.getSshCredentialGroups().get(0));

        assertEquals(2, result.getSnmpCredentialGroups().size());
        assertEquals("snmp_group1", result.getSnmpCredentialGroups().get(0));

        assertEquals(2, result.getSnmpVersionGroups().size());
        assertEquals("1218", result.getSnmpVersionGroups().get(0));

        assertEquals(2, result.getOrganizations().size());
        assertEquals("CDOT", result.getOrganizations().get(0));
        assertEquals("City of Denver", result.getOrganizations().get(1));

        // Verify all repository methods were called exactly once
        verify(rsusRepository, times(1)).findDistinctPrimaryRoutes();
        verify(rsuModelsRepository, times(1)).findAllModelsWithManufacturers();
        verify(rsuCredentialsRepository, times(1)).findAllNicknames();
        verify(snmpCredentialsRepository, times(1)).findAllNicknames();
        verify(snmpProtocolsRepository, times(1)).findAllNicknames();
        verify(postgresService, times(1)).findUserOrgRoles(username);
    }

    @Test
    void testGetAllowedSelections_withEmptyLists() {
        // Arrange
        String username = "newuser@example.com";

        when(rsusRepository.findDistinctPrimaryRoutes()).thenReturn(Collections.emptyList());
        when(rsuModelsRepository.findAllModelsWithManufacturers()).thenReturn(Collections.emptyList());
        when(rsuCredentialsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(snmpCredentialsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(snmpProtocolsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(postgresService.findUserOrgRoles(username)).thenReturn(Collections.emptyList());

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

        verify(rsusRepository, times(1)).findDistinctPrimaryRoutes();
        verify(rsuModelsRepository, times(1)).findAllModelsWithManufacturers();
        verify(rsuCredentialsRepository, times(1)).findAllNicknames();
        verify(snmpCredentialsRepository, times(1)).findAllNicknames();
        verify(snmpProtocolsRepository, times(1)).findAllNicknames();
        verify(postgresService, times(1)).findUserOrgRoles(username);
    }

    @Test
    void testGetAllowedSelections_modelStringFormatting() {
        // Arrange
        String username = "testuser@example.com";

        List<RsuModelWithManufacturer> models = Arrays.asList(
                new RsuModelWithManufacturer("Manufacturer A", "Model X"),
                new RsuModelWithManufacturer("Mfg B", "Model Y")
        );

        when(rsusRepository.findDistinctPrimaryRoutes()).thenReturn(Collections.emptyList());
        when(rsuModelsRepository.findAllModelsWithManufacturers()).thenReturn(models);
        when(rsuCredentialsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(snmpCredentialsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(snmpProtocolsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(postgresService.findUserOrgRoles(username)).thenReturn(Collections.emptyList());

        // Act
        ModifyRsuAllowedSelections result = rsuManagementService.getAllowedSelections(username);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getRsuModels().size());
        assertEquals("Manufacturer A Model X", result.getRsuModels().get(0));
        assertEquals("Mfg B Model Y", result.getRsuModels().get(1));
    }

    @Test
    void testGetAllowedSelections_organizationNameExtraction() {
        // Arrange
        String username = "multiorg@example.com";

        List<UserOrgRole> userOrgRoles = Arrays.asList(
                new UserOrgRole("multiorg@example.com", "Org One", "admin"),
                new UserOrgRole("multiorg@example.com", "Org Two", "user"),
                new UserOrgRole("multiorg@example.com", "Org Three", "viewer")
        );

        when(rsusRepository.findDistinctPrimaryRoutes()).thenReturn(Collections.emptyList());
        when(rsuModelsRepository.findAllModelsWithManufacturers()).thenReturn(Collections.emptyList());
        when(rsuCredentialsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(snmpCredentialsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(snmpProtocolsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(postgresService.findUserOrgRoles(username)).thenReturn(userOrgRoles);

        // Act
        ModifyRsuAllowedSelections result = rsuManagementService.getAllowedSelections(username);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getOrganizations().size());
        assertEquals("Org One", result.getOrganizations().get(0));
        assertEquals("Org Two", result.getOrganizations().get(1));
        assertEquals("Org Three", result.getOrganizations().get(2));
    }

    @Test
    void testGetAllowedSelections_singleItemLists() {
        // Arrange
        String username = "singleuser@example.com";

        when(rsusRepository.findDistinctPrimaryRoutes()).thenReturn(List.of("I-25"));
        when(rsuModelsRepository.findAllModelsWithManufacturers()).thenReturn(
                List.of(new RsuModelWithManufacturer("Commsignia", "ITS-RS4-M"))
        );
        when(rsuCredentialsRepository.findAllNicknames()).thenReturn(List.of("default"));
        when(snmpCredentialsRepository.findAllNicknames()).thenReturn(List.of("snmp_default"));
        when(snmpProtocolsRepository.findAllNicknames()).thenReturn(List.of("41"));
        when(postgresService.findUserOrgRoles(username)).thenReturn(
                List.of(new UserOrgRole("singleuser@example.com", "CDOT", "admin"))
        );

        // Act
        ModifyRsuAllowedSelections result = rsuManagementService.getAllowedSelections(username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getPrimaryRoutes().size());
        assertEquals("I-25", result.getPrimaryRoutes().get(0));
        
        assertEquals(1, result.getRsuModels().size());
        assertEquals("Commsignia ITS-RS4-M", result.getRsuModels().get(0));
        
        assertEquals(1, result.getSshCredentialGroups().size());
        assertEquals("default", result.getSshCredentialGroups().get(0));
        
        assertEquals(1, result.getSnmpCredentialGroups().size());
        assertEquals("snmp_default", result.getSnmpCredentialGroups().get(0));
        
        assertEquals(1, result.getSnmpVersionGroups().size());
        assertEquals("41", result.getSnmpVersionGroups().get(0));
        
        assertEquals(1, result.getOrganizations().size());
        assertEquals("CDOT", result.getOrganizations().get(0));
    }

    @Test
    void testGetAllowedSelections_verifyNoInteractionsBetweenRepositories() {
        // Arrange
        String username = "testuser@example.com";

        when(rsusRepository.findDistinctPrimaryRoutes()).thenReturn(Collections.emptyList());
        when(rsuModelsRepository.findAllModelsWithManufacturers()).thenReturn(Collections.emptyList());
        when(rsuCredentialsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(snmpCredentialsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(snmpProtocolsRepository.findAllNicknames()).thenReturn(Collections.emptyList());
        when(postgresService.findUserOrgRoles(username)).thenReturn(Collections.emptyList());

        // Act
        rsuManagementService.getAllowedSelections(username);

        // Assert - verify each repository is called independently
        verify(rsusRepository).findDistinctPrimaryRoutes();
        verifyNoMoreInteractions(rsusRepository);
        
        verify(rsuModelsRepository).findAllModelsWithManufacturers();
        verifyNoMoreInteractions(rsuModelsRepository);
        
        verify(rsuCredentialsRepository).findAllNicknames();
        verifyNoMoreInteractions(rsuCredentialsRepository);
        
        verify(snmpCredentialsRepository).findAllNicknames();
        verifyNoMoreInteractions(snmpCredentialsRepository);
        
        verify(snmpProtocolsRepository).findAllNicknames();
        verifyNoMoreInteractions(snmpProtocolsRepository);
        
        verify(postgresService).findUserOrgRoles(username);
        verifyNoMoreInteractions(postgresService);
    }
}
