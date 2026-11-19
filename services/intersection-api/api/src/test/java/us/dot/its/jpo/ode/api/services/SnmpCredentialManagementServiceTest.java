package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.controllers.credentials.SnmpCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;

import us.dot.its.jpo.ode.api.models.credentials.CredentialSummaryDTO;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnmpCredentialManagementServiceTest {

    @Mock
    SnmpCredentialRepository mockSnmpCredentialRepository;

    @Mock
    OrganizationRepository mockOrganizationRepository;

    @Mock
    PermissionService mockPermissionService;

    @InjectMocks
    SnmpCredentialManagementService snmpCredentialManagementService;

    @Test
    void testCreate_Success() throws SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException, EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int ownerOrganizationId = 1;
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(nickname, username, password, organization);

        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(ownerOrganizationId);
        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

        SnmpCredential expectedSnmpCredential = new SnmpCredential();
        expectedSnmpCredential.setNickname(nickname);
        expectedSnmpCredential.setUsername(username);
        expectedSnmpCredential.setPassword(password);
        expectedSnmpCredential.setOwnerOrganization(mockOrganization);
        when(mockSnmpCredentialRepository.save(any())).thenReturn(expectedSnmpCredential);

        // Act
        SnmpCredential snmpCredential = snmpCredentialManagementService.create(request);

        // Assert
        assertEquals(nickname, snmpCredential.getNickname());
        assertEquals(username, snmpCredential.getUsername());
        assertEquals(password, snmpCredential.getPassword());
        assertEquals(ownerOrganizationId, snmpCredential.getOwnerOrganization().getId());
        verify(mockSnmpCredentialRepository).save(any());
    }

    @Test
    void testCreate_Failure_AlreadyExists() {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(nickname, username, password, organization);

        when(mockSnmpCredentialRepository.existsByNickname(nickname)).thenReturn(true);

        // Act & Assert
        assertThrows(SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException.class, () -> snmpCredentialManagementService.create(request));
    }

    @Test
    void testCreate_Failure_OrganizationNotFound() {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        SnmpCredentialController.SnmpCredentialCreateRequest request = new SnmpCredentialController.SnmpCredentialCreateRequest(nickname, username, password, organization);

        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialManagementService.create(request));
    }

    @Test
    void testGetByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        int organizationId = 1;
        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        lenient().when(mockOrganization.getName()).thenReturn(organization);

        SnmpCredential expectedSnmpCredential = new SnmpCredential();
        expectedSnmpCredential.setOwnerOrganization(mockOrganization);
        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(expectedSnmpCredential));

        // Act
        SnmpCredential snmpCredential = snmpCredentialManagementService.getByNickname(nickname);

        // Assert
        assertNotNull(snmpCredential);
        assertEquals(expectedSnmpCredential, snmpCredential);
        verify(mockSnmpCredentialRepository).findByNickname(nickname);
    }

    @Test
    void testGetByNickname_Failure_NotFound() {
        // Arrange
        String nickname = "nickname";
        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialManagementService.getByNickname(nickname));
    }

    @Test
    void testUpdate_ChangePassword_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int organizationId = 1;
        String newPassword = "mynewpassword";
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setPassword(newPassword);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        
        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        lenient().when(mockOrganization.getName()).thenReturn(organization);
        existingCredential.setOwnerOrganization(mockOrganization);

        SnmpCredential expectedCredential = new SnmpCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(username);
        expectedCredential.setPassword(newPassword);
        expectedCredential.setOwnerOrganization(mockOrganization);

        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockSnmpCredentialRepository.save(any())).thenReturn(expectedCredential);

        // Act
        SnmpCredential updatedCredential = snmpCredentialManagementService.update(patch);

        // Assert
        assertNotNull(updatedCredential);
        assertEquals(nickname, updatedCredential.getNickname());
        assertEquals(username, updatedCredential.getUsername());
        assertEquals(newPassword, updatedCredential.getPassword());
        assertEquals(organizationId, updatedCredential.getOwnerOrganization().getId());
        verify(mockSnmpCredentialRepository).findByNickname(nickname);
        verify(mockSnmpCredentialRepository).save(any());
    }

    @Test
    void testUpdate_ChangeOrganization_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String oldOrganization = "oldOrganization";
        int oldOrganizationId = 1;
        String newOrganization = "newOrganization";
        int newOrganizationId = 2;
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setOrganization(newOrganization);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        
        Organization mockOldOrganization = mock(Organization.class);
        lenient().when(mockOldOrganization.getId()).thenReturn(oldOrganizationId);
        lenient().when(mockOldOrganization.getName()).thenReturn(oldOrganization);
        existingCredential.setOwnerOrganization(mockOldOrganization);

        Organization mockNewOrganization = mock(Organization.class);
        lenient().when(mockNewOrganization.getId()).thenReturn(newOrganizationId);
        lenient().when(mockNewOrganization.getName()).thenReturn(newOrganization);

        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockSnmpCredentialRepository.save(any())).thenReturn(existingCredential);

        when(mockOrganizationRepository.findByName(newOrganization)).thenReturn(Optional.of(mockNewOrganization));

        // Act
        SnmpCredential updatedCredential = snmpCredentialManagementService.update(patch);

        // Assert
        assertNotNull(updatedCredential);
        assertEquals(nickname, updatedCredential.getNickname());
        assertEquals(username, updatedCredential.getUsername());
        assertEquals(password, updatedCredential.getPassword());
        assertEquals(newOrganizationId, updatedCredential.getOwnerOrganization().getId());
        verify(mockSnmpCredentialRepository).findByNickname(nickname);
    }

    @Test
    void testUpdate_ChangeUsername_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int organizationId = 1;
        String newUsername = "newUsername";
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setUsername(newUsername);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        
        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        lenient().when(mockOrganization.getName()).thenReturn(organization);
        existingCredential.setOwnerOrganization(mockOrganization);

        SnmpCredential expectedCredential = new SnmpCredential();
        expectedCredential.setNickname(nickname);
        expectedCredential.setUsername(newUsername);
        expectedCredential.setPassword(password);
        expectedCredential.setOwnerOrganization(mockOrganization);

        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockSnmpCredentialRepository.save(any())).thenReturn(expectedCredential);

        // Act
        SnmpCredential snmpCredential = snmpCredentialManagementService.update(patch);

        // Assert
        assertNotNull(snmpCredential);
        assertEquals(nickname, snmpCredential.getNickname());
        assertEquals(newUsername, snmpCredential.getUsername());
        assertEquals(password, snmpCredential.getPassword());
        assertEquals(organizationId, snmpCredential.getOwnerOrganization().getId());
        verify(mockSnmpCredentialRepository).findByNickname(nickname);
        verify(mockSnmpCredentialRepository).save(any());
    }

    @Test
    void testUpdate_ChangeOrganization_Failure_OrganizationNotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int organizationId = 1;
        String newOrganization = "newOrganization";
        SnmpCredentialController.SnmpCredentialPatch patch = new SnmpCredentialController.SnmpCredentialPatch(nickname);
        patch.setOrganization(newOrganization);

        SnmpCredential existingCredential = new SnmpCredential();
        existingCredential.setNickname(nickname);
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        
        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        lenient().when(mockOrganization.getName()).thenReturn(organization);
        existingCredential.setOwnerOrganization(mockOrganization);

        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));
        when(mockOrganizationRepository.findByName(newOrganization)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialManagementService.update(patch));
    }

    @Test
    void testDeleteByNickname_Success() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        String organization = "organization";
        int organizationId = 1;

        SnmpCredential existingCredential = new SnmpCredential();
        Organization mockOrganization = mock(Organization.class);
        lenient().when(mockOrganization.getId()).thenReturn(organizationId);
        lenient().when(mockOrganization.getName()).thenReturn(organization);
        existingCredential.setOwnerOrganization(mockOrganization);
        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(existingCredential));

        // Act
        snmpCredentialManagementService.deleteByNickname(nickname);

        // Assert
        verify(mockSnmpCredentialRepository).delete(existingCredential);
    }

    @Test
    void testDeleteByNickname_Failure_CredentialNotFound() throws EntityNotFoundException {
        // Arrange
        String nickname = "nickname";
        when(mockSnmpCredentialRepository.findByNickname(nickname)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> snmpCredentialManagementService.deleteByNickname(nickname));
    }

    @Test
    void testListForOrganization_Success() throws Exception {
        // Arrange
        String organizationName = "organization";
        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getName()).thenReturn(organizationName);
        Organization mockOtherOrganization = mock(Organization.class);
        when(mockOtherOrganization.getName()).thenReturn("otherOrganization");
        when(mockOrganizationRepository.findByName(organizationName)).thenReturn(Optional.of(mockOrganization));

        SnmpCredential ownedUnused = new SnmpCredential();
        ownedUnused.setNickname("owned");
        ownedUnused.setUsername("ownedUser");
        ownedUnused.setPassword("ownedPassword");
        ownedUnused.setOwnerOrganization(mockOrganization);

        SnmpCredential usedNotOwned = new SnmpCredential();
        usedNotOwned.setNickname("foreign");
        usedNotOwned.setUsername("foreignUser");
        usedNotOwned.setPassword("foreignPassword");
        usedNotOwned.setOwnerOrganization(mockOtherOrganization);

        SnmpCredentialRepository.CredentialRsuUsage usage1 = mock(SnmpCredentialRepository.CredentialRsuUsage.class);
        when(usage1.getNickname()).thenReturn("foreign");
        when(usage1.getIpv4Address()).thenReturn(InetAddress.getByName("10.0.0.1"));
        SnmpCredentialRepository.CredentialRsuUsage usage2 = mock(SnmpCredentialRepository.CredentialRsuUsage.class);
        when(usage2.getNickname()).thenReturn("foreign");
        when(usage2.getIpv4Address()).thenReturn(InetAddress.getByName("10.0.0.2"));

        when(mockSnmpCredentialRepository.findRsuUsageInOrganization(mockOrganization))
                .thenReturn(List.of(usage1, usage2));
        when(mockSnmpCredentialRepository.findOwnedOrUsedByOrganization(mockOrganization))
                .thenReturn(List.of(usedNotOwned, ownedUnused));

        // Act
        List<CredentialSummaryDTO> result = snmpCredentialManagementService.listForOrganization(organizationName);

        // Assert
        assertEquals(List.of(
                new CredentialSummaryDTO("foreign", "foreignUser", "otherOrganization",
                        List.of("10.0.0.1", "10.0.0.2")),
                new CredentialSummaryDTO("owned", "ownedUser", organizationName, List.of())), result);
    }

    @Test
    void testListForOrganization_Failure_OrganizationNotFound() {
        // Arrange
        when(mockOrganizationRepository.findByName("missing")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class,
                () -> snmpCredentialManagementService.listForOrganization("missing"));
    }
}