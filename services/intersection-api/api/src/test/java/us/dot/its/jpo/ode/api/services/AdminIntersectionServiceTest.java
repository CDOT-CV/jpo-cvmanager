package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.mappers.INetMapper;
import us.dot.its.jpo.ode.api.mappers.IntersectionMapper;
import us.dot.its.jpo.ode.api.models.admin.intersection.AllowedSelections;
import us.dot.its.jpo.ode.api.models.admin.intersection.Bbox;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionDto;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionListResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionPatch;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionSingleResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.RefPt;
import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.IntersectionOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuIntersection;
import us.dot.its.jpo.ode.api.repositories.IntersectionOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuIntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminIntersectionServiceTest {

    @Mock
    private IntersectionRepository intersectionRepository;

    @Mock
    private IntersectionOrganizationRepository intersectionOrganizationRepository;

    @Mock
    private RsuIntersectionRepository rsuIntersectionRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RsuRepository rsuRepository;

    @Mock
    private IntersectionMapper intersectionMapper;

    @Mock
    private INetMapper inetMapper;

    @InjectMocks
    private AdminIntersectionService adminIntersectionService;

    private Intersection makeIntersection(String number) {
        Intersection i = new Intersection();
        i.setId(1);
        i.setIntersectionNumber(number);
        i.setIntersectionOrganizations(Collections.emptyList());
        i.setRsuIntersections(Collections.emptyList());
        return i;
    }

    private Intersection makeIntersectionWithOrg(String number, String orgName) {
        Organization org = new Organization();
        org.setName(orgName);
        IntersectionOrganization io = new IntersectionOrganization();
        io.setOrganization(org);
        Intersection i = makeIntersection(number);
        i.setIntersectionOrganizations(List.of(io));
        return i;
    }

    private IntersectionDto makeDto(String id) {
        IntersectionDto dto = new IntersectionDto();
        dto.setIntersectionId(id);
        dto.setOrganizations(Collections.emptyList());
        return dto;
    }

    private RsuIntersectionRepository.IntersectionRsuProjection makeRsuProjection(
            String intersectionNumber, InetAddress ip) {
        RsuIntersectionRepository.IntersectionRsuProjection proj =
                mock(RsuIntersectionRepository.IntersectionRsuProjection.class);
        when(proj.getIntersectionNumber()).thenReturn(intersectionNumber);
        when(proj.getRsuIp()).thenReturn(ip);
        return proj;
    }

    private Organization makeOrg(String name) {
        Organization org = new Organization();
        org.setName(name);
        return org;
    }

    private Rsu makeRsu(InetAddress ip) {
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(ip);
        return rsu;
    }

    @Nested
    class GetAllIntersections {
        @Test
        void getAllIntersections_superuser_noOrgScope_usesUnfilteredQuery() {
            Intersection i = makeIntersection("1123");
            IntersectionDto dto = makeDto("1123");

            when(intersectionRepository.findAllWithOrgs()).thenReturn(List.of(i));
            when(intersectionMapper.toDto(i)).thenReturn(dto);
            when(rsuIntersectionRepository.findRsuIpsByIntersectionNumbers(List.of("1123")))
                    .thenReturn(Collections.emptyList());

            IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, true, List.of());

            verify(intersectionRepository).findAllWithOrgs();
            verify(intersectionRepository, never()).findAllByOrgNameWithOrgs(any());
            verify(intersectionRepository, never()).findAllByOrgNamesWithOrgs(any());
            assertNotNull(result.getIntersectionData());
            assertEquals(1, result.getIntersectionData().size());
        }

        @Test
        void getAllIntersections_withScopedOrg_usesSingleOrgQuery() {
            Intersection i = makeIntersectionWithOrg("1123", "OrgA");
            IntersectionDto dto = makeDto("1123");

            when(intersectionRepository.findAllByOrgNameWithOrgs("OrgA")).thenReturn(List.of(i));
            when(intersectionMapper.toDto(i)).thenReturn(dto);
            when(rsuIntersectionRepository.findRsuIpsByIntersectionNumbers(any()))
                    .thenReturn(Collections.emptyList());

            IntersectionListResponse result = adminIntersectionService.getAllIntersections("OrgA", false, List.of("OrgA"));

            verify(intersectionRepository).findAllByOrgNameWithOrgs("OrgA");
            assertEquals(1, result.getIntersectionData().size());
        }

        @Test
        void getAllIntersections_nonSuperuserNoScope_usesQualifiedOrgsQuery() {
            List<String> userOrgs = List.of("OrgA", "OrgB");
            Intersection i = makeIntersectionWithOrg("1123", "OrgA");
            IntersectionDto dto = makeDto("1123");

            when(intersectionRepository.findAllByOrgNamesWithOrgs(userOrgs)).thenReturn(List.of(i));
            when(intersectionMapper.toDto(i)).thenReturn(dto);
            when(rsuIntersectionRepository.findRsuIpsByIntersectionNumbers(any()))
                    .thenReturn(Collections.emptyList());

            IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, false, userOrgs);

            verify(intersectionRepository).findAllByOrgNamesWithOrgs(userOrgs);
            assertEquals(1, result.getIntersectionData().size());
        }

        @Test
        void getAllIntersections_noResults_returnsEmptyList() {
            when(intersectionRepository.findAllWithOrgs()).thenReturn(Collections.emptyList());

            IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, true, List.of());

            assertNotNull(result.getIntersectionData());
            assertTrue(result.getIntersectionData().isEmpty());
            verify(rsuIntersectionRepository, never()).findRsuIpsByIntersectionNumbers(any());
        }

        @Test
        void getAllIntersections_attachesRsuIpsToCorrectIntersection() throws UnknownHostException {
            Intersection i = makeIntersection("1123");
            IntersectionDto dto = makeDto("1123");
            InetAddress ip = InetAddress.getByName("192.168.1.1");

            // Create projection before nesting inside thenReturn to avoid UnfinishedStubbing
            RsuIntersectionRepository.IntersectionRsuProjection proj = makeRsuProjection("1123", ip);

            when(intersectionRepository.findAllWithOrgs()).thenReturn(List.of(i));
            when(intersectionMapper.toDto(i)).thenReturn(dto);
            when(rsuIntersectionRepository.findRsuIpsByIntersectionNumbers(List.of("1123")))
                    .thenReturn(List.of(proj));
            when(inetMapper.mapInetAddressToString(ip)).thenReturn("192.168.1.1");

            IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, true, List.of());

            assertEquals(List.of("192.168.1.1"), result.getIntersectionData().getFirst().getRsus());
        }

        @Test
        void getAllIntersections_nonSuperuserEmptyQualifiedOrgs_returnsEmptyList() {
            IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, false, List.of());

            assertNotNull(result.getIntersectionData());
            assertTrue(result.getIntersectionData().isEmpty());
            verify(intersectionRepository, never()).findAllByOrgNamesWithOrgs(any());
        }
    }

    @Nested
    class GetIntersection {
        @Test
        void getIntersection_notFound_returnsEmptyDataWithAllowedSelections() {
            when(intersectionRepository.findByIntersectionNumberWithOrgs("9999")).thenReturn(Optional.empty());
            when(organizationRepository.findAll()).thenReturn(List.of(makeOrg("OrgA")));
            when(rsuRepository.findAll()).thenReturn(Collections.emptyList());

            IntersectionSingleResponse result = adminIntersectionService.getIntersection(
                    "9999", null, true, List.of(), List.of());

            assertNotNull(result.getIntersectionDto());
            assertNull(result.getIntersectionDto().getIntersectionId());
            assertNotNull(result.getAllowedSelections());
            verify(intersectionMapper, never()).toDto(any());
        }

        @Test
        void getIntersection_foundAsSuperuser_returnsFullDataAndAllOrgs() throws UnknownHostException {
            Intersection i = makeIntersectionWithOrg("1123", "OrgA");
            IntersectionDto dto = makeDto("1123");
            InetAddress rsuIp = InetAddress.getByName("192.168.1.1");
            Organization org = makeOrg("OrgA");
            Rsu rsu = makeRsu(rsuIp);

            when(intersectionRepository.findByIntersectionNumberWithOrgs("1123")).thenReturn(Optional.of(i));
            when(intersectionMapper.toDto(i)).thenReturn(dto);
            when(rsuIntersectionRepository.findRsuIpsByIntersectionNumber("1123")).thenReturn(List.of(rsuIp));
            when(inetMapper.mapInetAddressToString(rsuIp)).thenReturn("192.168.1.1");
            when(organizationRepository.findAll()).thenReturn(List.of(org));
            when(inetMapper.mapInetAddressToString(rsuIp)).thenReturn("192.168.1.1");
            when(rsuRepository.findAll()).thenReturn(List.of(rsu));

            IntersectionSingleResponse result = adminIntersectionService.getIntersection(
                    "1123", null, true, List.of(), List.of());

            assertEquals("1123", result.getIntersectionDto().getIntersectionId());
            assertNotNull(result.getAllowedSelections());
            assertEquals(List.of("OrgA"), result.getAllowedSelections().getOrganizations());
        }

        @Test
        void getIntersection_scopedOrgMatches_returnsFilteredOrgList() {
            Intersection i = makeIntersectionWithOrg("1123", "OrgA");
            IntersectionDto dto = makeDto("1123");
            dto.setOrganizations(List.of("OrgA"));

            when(intersectionRepository.findByIntersectionNumberWithOrgs("1123")).thenReturn(Optional.of(i));
            when(intersectionMapper.toDto(i)).thenReturn(dto);
            when(rsuIntersectionRepository.findRsuIpsByIntersectionNumber("1123"))
                    .thenReturn(Collections.emptyList());
            when(rsuRepository.findAllowedRsuIpsInOrganizations(any())).thenReturn(Collections.emptyList());

            IntersectionSingleResponse result = adminIntersectionService.getIntersection(
                    "1123", "OrgA", false, List.of("OrgA"), List.of("OrgA"));

            assertEquals("1123", result.getIntersectionDto().getIntersectionId());
            assertEquals(List.of("OrgA"), result.getIntersectionDto().getOrganizations());
        }

        @Test
        void getIntersection_scopedOrgDoesNotMatch_returnsEmptyData() {
            // Intersection belongs to "OrgA", user is scoped to "OrgB"
            Intersection i = makeIntersectionWithOrg("1123", "OrgA");

            when(intersectionRepository.findByIntersectionNumberWithOrgs("1123")).thenReturn(Optional.of(i));
            when(rsuRepository.findAllowedRsuIpsInOrganizations(any())).thenReturn(Collections.emptyList());

            IntersectionSingleResponse result = adminIntersectionService.getIntersection(
                    "1123", "OrgB", false, List.of("OrgB"), List.of("OrgB"));

            assertNull(result.getIntersectionDto().getIntersectionId());
            verify(intersectionMapper, never()).toDto(any());
        }

        @Test
        void getIntersection_qualifiedOrgsMatch_returnsFilteredData() {
            // Intersection belongs to OrgA and OrgB; user qualifies for OrgA only
            Organization orgA = makeOrg("OrgA");
            Organization orgB = makeOrg("OrgB");
            IntersectionOrganization ioA = new IntersectionOrganization();
            ioA.setOrganization(orgA);
            IntersectionOrganization ioB = new IntersectionOrganization();
            ioB.setOrganization(orgB);
            Intersection i = makeIntersection("1123");
            i.setIntersectionOrganizations(List.of(ioA, ioB));

            IntersectionDto dto = makeDto("1123");
            dto.setOrganizations(List.of("OrgA", "OrgB"));

            when(intersectionRepository.findByIntersectionNumberWithOrgs("1123")).thenReturn(Optional.of(i));
            when(intersectionMapper.toDto(i)).thenReturn(dto);
            when(rsuIntersectionRepository.findRsuIpsByIntersectionNumber("1123"))
                    .thenReturn(Collections.emptyList());
            when(rsuRepository.findAllowedRsuIpsInOrganizations(any())).thenReturn(Collections.emptyList());

            IntersectionSingleResponse result = adminIntersectionService.getIntersection(
                    "1123", null, false, List.of("OrgA"), List.of("OrgA"));

            assertEquals("1123", result.getIntersectionDto().getIntersectionId());
            // Only the user's qualified org should appear
            assertEquals(List.of("OrgA"), result.getIntersectionDto().getOrganizations());
        }

        @Test
        void getIntersection_qualifiedOrgsNoMatch_returnsEmptyData() {
            // Intersection belongs to OrgB; user qualifies for OrgA only
            Intersection i = makeIntersectionWithOrg("1123", "OrgB");

            when(intersectionRepository.findByIntersectionNumberWithOrgs("1123")).thenReturn(Optional.of(i));
            when(rsuRepository.findAllowedRsuIpsInOrganizations(any())).thenReturn(Collections.emptyList());

            IntersectionSingleResponse result = adminIntersectionService.getIntersection(
                    "1123", null, false, List.of("OrgA"), List.of("OrgA"));

            assertNull(result.getIntersectionDto().getIntersectionId());
            verify(intersectionMapper, never()).toDto(any());
        }

        @Test
        void getIntersection_allowedSelectionsForNonSuperuser_usesOperatorOrgs() throws UnknownHostException {
            Intersection i = makeIntersectionWithOrg("1123", "OrgA");
            IntersectionDto dto = makeDto("1123");
            InetAddress rsuIp = InetAddress.getByName("10.0.0.1");

            when(intersectionRepository.findByIntersectionNumberWithOrgs("1123")).thenReturn(Optional.of(i));
            when(intersectionMapper.toDto(i)).thenReturn(dto);
            when(rsuIntersectionRepository.findRsuIpsByIntersectionNumber("1123"))
                    .thenReturn(Collections.emptyList());
            when(rsuRepository.findAllowedRsuIpsInOrganizations(List.of("OrgA"))).thenReturn(List.of(rsuIp));
            when(inetMapper.mapInetAddressToString(rsuIp)).thenReturn("10.0.0.1");

            IntersectionSingleResponse result = adminIntersectionService.getIntersection(
                    "1123", "OrgA", false, List.of("OrgA"), List.of("OrgA"));

            AllowedSelections allowed = result.getAllowedSelections();
            assertEquals(List.of("OrgA"), allowed.getOrganizations());
            assertEquals(List.of("10.0.0.1"), allowed.getRsus());
            verify(rsuRepository).findAllowedRsuIpsInOrganizations(List.of("OrgA"));
        }
    }

    @Nested
    class PatchIntersection {
        @Test
        void patchIntersection_basicUpdate_savesIntersectionWithNewNumber() {
            Intersection existing = makeIntersection("1000");
            IntersectionPatch patch = new IntersectionPatch(
                    1000, 1001, new RefPt(40.0, -105.0), null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());

            when(intersectionRepository.findByIntersectionNumber("1000")).thenReturn(Optional.of(existing));

            String result = adminIntersectionService.patchIntersection(patch);

            assertEquals("Intersection successfully modified", result);
            verify(intersectionRepository).save(existing);
            assertEquals("1001", existing.getIntersectionNumber());
        }

        @Test
        void patchIntersection_withOptionalFields_updatesWhenNonNull() {
            Intersection existing = makeIntersection("1000");
            Bbox bbox = new Bbox(39.9, -105.2, 40.1, -105.0);
            IntersectionPatch patch = new IntersectionPatch(
                    1000, 1000, new RefPt(40.0, -105.0), bbox, "Main St", "10.0.0.1",
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());

            when(intersectionRepository.findByIntersectionNumber("1000")).thenReturn(Optional.of(existing));
            when(inetMapper.mapStringToInetAddress("10.0.0.1")).thenReturn(null);

            adminIntersectionService.patchIntersection(patch);

            assertEquals("Main St", existing.getIntersectionName());
            verify(inetMapper).mapStringToInetAddress("10.0.0.1");
            // bbox is set via GeometryMapper (not mocked here — static method)
        }

        @Test
        void patchIntersection_nullOptionalFields_doesNotOverwriteExistingValues() {
            Intersection existing = makeIntersection("1000");
            existing.setIntersectionName("Existing Name");
            IntersectionPatch patch = new IntersectionPatch(
                    1000, 1000, new RefPt(40.0, -105.0), null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());

            when(intersectionRepository.findByIntersectionNumber("1000")).thenReturn(Optional.of(existing));

            adminIntersectionService.patchIntersection(patch);

            assertEquals("Existing Name", existing.getIntersectionName());
        }

        @Test
        void patchIntersection_orgsToAdd_loadsOrgsAndSavesAssociations() {
            Intersection existing = makeIntersection("1000");
            Organization org = makeOrg("OrgA");
            IntersectionPatch patch = new IntersectionPatch(
                    1000, 1000, new RefPt(40.0, -105.0), null, null, null,
                    List.of("OrgA"), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());

            when(intersectionRepository.findByIntersectionNumber("1000")).thenReturn(Optional.of(existing));
            when(organizationRepository.findByNameIn(List.of("OrgA"))).thenReturn(List.of(org));

            adminIntersectionService.patchIntersection(patch);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<IntersectionOrganization>> captor = ArgumentCaptor.forClass(List.class);
            verify(intersectionOrganizationRepository).saveAll(captor.capture());
            List<IntersectionOrganization> saved = captor.getValue();
            assertEquals(1, saved.size());
            assertEquals(org, saved.getFirst().getOrganization());
            assertEquals(existing, saved.getFirst().getIntersection());
        }

        @Test
        void patchIntersection_orgsToRemove_callsDeleteByIntersectionAndOrgNames() {
            Intersection existing = makeIntersection("1000");
            IntersectionPatch patch = new IntersectionPatch(
                    1000, 1000, new RefPt(40.0, -105.0), null, null, null,
                    Collections.emptyList(), List.of("OrgA"),
                    Collections.emptyList(), Collections.emptyList());

            when(intersectionRepository.findByIntersectionNumber("1000")).thenReturn(Optional.of(existing));

            adminIntersectionService.patchIntersection(patch);

            verify(intersectionOrganizationRepository)
                    .deleteByIntersectionNumberAndOrganizationNameIn("1000", List.of("OrgA"));
        }

        @Test
        void patchIntersection_rsusToAdd_loadsRsusAndSavesAssociations() throws UnknownHostException {
            Intersection existing = makeIntersection("1000");
            InetAddress ip = InetAddress.getByName("192.168.1.1");
            Rsu rsu = makeRsu(ip);
            IntersectionPatch patch = new IntersectionPatch(
                    1000, 1000, new RefPt(40.0, -105.0), null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    List.of("192.168.1.1"), Collections.emptyList());

            when(intersectionRepository.findByIntersectionNumber("1000")).thenReturn(Optional.of(existing));
            when(inetMapper.mapStringToInetAddress("192.168.1.1")).thenReturn(ip);
            when(rsuRepository.findByIpv4AddressIn(List.of(ip))).thenReturn(List.of(rsu));

            adminIntersectionService.patchIntersection(patch);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<RsuIntersection>> captor = ArgumentCaptor.forClass(List.class);
            verify(rsuIntersectionRepository).saveAll(captor.capture());
            List<RsuIntersection> saved = captor.getValue();
            assertEquals(1, saved.size());
            assertEquals(rsu, saved.getFirst().getRsu());
            assertEquals(existing, saved.getFirst().getIntersection());
        }

        @Test
        void patchIntersection_rsusToRemove_callsDeleteByIntersectionAndIps() throws UnknownHostException {
            Intersection existing = makeIntersection("1000");
            InetAddress ip = InetAddress.getByName("192.168.1.1");
            IntersectionPatch patch = new IntersectionPatch(
                    1000, 1000, new RefPt(40.0, -105.0), null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), List.of("192.168.1.1"));

            when(intersectionRepository.findByIntersectionNumber("1000")).thenReturn(Optional.of(existing));
            when(inetMapper.mapStringToInetAddress("192.168.1.1")).thenReturn(ip);

            adminIntersectionService.patchIntersection(patch);

            verify(rsuIntersectionRepository)
                    .deleteByIntersectionNumberAndRsuIpv4AddressIn("1000", List.of(ip));
        }

        @Test
        void patchIntersection_intersectionNotFound_throws404() {
            IntersectionPatch patch = new IntersectionPatch(
                    9999, 9999, new RefPt(40.0, -105.0), null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());

            when(intersectionRepository.findByIntersectionNumber("9999")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> adminIntersectionService.patchIntersection(patch));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        void patchIntersection_emptyRelationshipLists_skipsAllAssociationSteps() {
            Intersection existing = makeIntersection("1000");
            IntersectionPatch patch = new IntersectionPatch(
                    1000, 1000, new RefPt(40.0, -105.0), null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());

            when(intersectionRepository.findByIntersectionNumber("1000")).thenReturn(Optional.of(existing));

            adminIntersectionService.patchIntersection(patch);

            verify(organizationRepository, never()).findByNameIn(any());
            verify(intersectionOrganizationRepository, never()).saveAll(any());
            verify(intersectionOrganizationRepository, never())
                    .deleteByIntersectionNumberAndOrganizationNameIn(any(), any());
            verify(rsuRepository, never()).findByIpv4AddressIn(any());
            verify(rsuIntersectionRepository, never()).saveAll(any());
            verify(rsuIntersectionRepository, never())
                    .deleteByIntersectionNumberAndRsuIpv4AddressIn(any(), any());
        }
    }

    @Nested
    class DeleteIntersection {
        @Test
        void deleteIntersection_existingIntersection_deletesInOrderAndReturnsMessage() {
            Intersection existing = makeIntersection("1123");
            when(intersectionRepository.findByIntersectionNumber("1123")).thenReturn(Optional.of(existing));

            String result = adminIntersectionService.deleteIntersection("1123");

            assertEquals("Intersection successfully deleted", result);
            verify(intersectionOrganizationRepository)
                    .deleteIntersectionOrganizationByIntersection_IntersectionNumber("1123");
            verify(rsuIntersectionRepository).deleteByIntersection_IntersectionNumber("1123");
            verify(intersectionRepository).delete(existing);
        }

        @Test
        void deleteIntersection_notFound_throws404() {
            when(intersectionRepository.findByIntersectionNumber("9999")).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> adminIntersectionService.deleteIntersection("9999"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
            verify(intersectionOrganizationRepository, never())
                    .deleteIntersectionOrganizationByIntersection_IntersectionNumber(any());
            verify(rsuIntersectionRepository, never()).deleteByIntersection_IntersectionNumber(any());
            verify(intersectionRepository, never()).delete(any(Intersection.class));
        }
    }
}
