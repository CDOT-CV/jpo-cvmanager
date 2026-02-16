package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import us.dot.its.jpo.ode.api.controllers.credentials.RsuCredentialController;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RsuCredentialManagementServiceTest {

    @Mock
    RsuCredentialRepository mockRsuCredentialRepository;

    @Mock
    OrganizationRepository mockOrganizationRepository;

    @InjectMocks
    RsuCredentialManagementService rsuCredentialManagementService;

    @Test
    void testCreate_Success() throws RsuCredentialManagementService.RsuCredentialAlreadyExistsException, RsuCredentialManagementService.OrganizationNotFoundException {
        // Arrange
        String nickname = "nickname";
        String username = "username";
        String password = "password";
        String organization = "organization";
        int ownerOrganizationId = 1;
        RsuCredentialController.RsuCredentialCreateRequest request = new RsuCredentialController.RsuCredentialCreateRequest(nickname, username, password, organization);

        Organization mockOrganization = mock(Organization.class);
        when(mockOrganization.getId()).thenReturn(ownerOrganizationId);
        when(mockOrganizationRepository.findByName(organization)).thenReturn(Optional.of(mockOrganization));

        RsuCredential mockRsuCredential = new RsuCredential();
        mockRsuCredential.setNickname(nickname);
        mockRsuCredential.setUsername(username);
        mockRsuCredential.setPassword(password);
        mockRsuCredential.setOwnerOrganizationId(ownerOrganizationId);
        when(mockRsuCredentialRepository.save(any())).thenReturn(mockRsuCredential);

        // Act
        RsuCredential rsuCredential = rsuCredentialManagementService.create(request);

        // Assert
        assertNotNull(rsuCredential);
        assertEquals(nickname, rsuCredential.getNickname());
        assertEquals(username, rsuCredential.getUsername());
        assertEquals(password, rsuCredential.getPassword());
        assertEquals(1, rsuCredential.getOwnerOrganizationId());
        verify(mockRsuCredentialRepository).save(any());
    }

    @Test
    void testGetByNickname_Success() throws RsuCredentialManagementService.RsuCredentialNotFoundException {
        // Arrange
        String nickname = "nickname";
        RsuCredential mockRsuCredential = new RsuCredential();
        when(mockRsuCredentialRepository.findByNickname(nickname)).thenReturn(Optional.of(mockRsuCredential));

        // Act
        RsuCredential rsuCredential = rsuCredentialManagementService.getByNickname(nickname);

        // Assert
        assertNotNull(rsuCredential);
        assertEquals(mockRsuCredential, rsuCredential);
        verify(mockRsuCredentialRepository).findByNickname(nickname);
    }

    @Test
    void testUpdate_Success() {
        // TODO: implement
    }

    @Test
    void testDeleteByNickname_Success() {
        // TODO: implement
    }

}