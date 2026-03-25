package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.fixtures.TestFixtures;
import us.dot.its.jpo.ode.api.models.admin.intersection.AllowedSelections;
import us.dot.its.jpo.ode.api.models.admin.intersection.Bbox;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionListResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionPatch;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionSingleResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.RefPt;
import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.SnmpProtocol;
import us.dot.its.jpo.ode.api.repositories.IntersectionOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuCredentialRepository;
import us.dot.its.jpo.ode.api.repositories.RsuIntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuModelRepository;
import us.dot.its.jpo.ode.api.repositories.RsuOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpCredentialRepository;
import us.dot.its.jpo.ode.api.repositories.SnmpProtocolRepository;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration-test")
class AdminIntersectionServiceTest {
  @Autowired
  private AdminIntersectionService adminIntersectionService;

  private final TestFixtures fixtures = new TestFixtures();

  @Autowired
  private IntersectionRepository intersectionRepository;

  @Autowired
  private IntersectionOrganizationRepository intersectionOrganizationRepository;

  @Autowired
  private RsuIntersectionRepository rsuIntersectionRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private RsuRepository rsuRepository;

  @Autowired
  private RsuOrganizationRepository rsuOrganizationRepository;

  @Autowired
  private RsuCredentialRepository rsuCredentialRepository;

  @Autowired
  private SnmpCredentialRepository snmpCredentialRepository;

  @Autowired
  private SnmpProtocolRepository snmpProtocolRepository;

  @Autowired
  private RsuModelRepository rsuModelRepository;
  @Autowired
  private ManufacturerRepository manufacturerRepository;

  /**
   * Clears all relevant tables in reverse FK-dependency order before each test so that
   * tests never see each other's data and hardcoded identifiers ("1123", "1000", etc.)
   * never produce duplicate-key violations.
   *
   * Deletion order (leaf tables first):
   *   rsu_intersection → intersection_organization → rsu_organization
   *   → rsus → rsu_credentials → snmp_credentials → snmp_protocols → rsu_models
   *   → intersections → organizations
   *
   * Note: manufacturer rows are left as orphans (no unique constraint in test data).
   */
  @BeforeEach
  void clearDatabase() {
    rsuIntersectionRepository.deleteAll();
    intersectionOrganizationRepository.deleteAll();
    rsuOrganizationRepository.deleteAll();
    rsuRepository.deleteAll();
    rsuCredentialRepository.deleteAll();
    snmpCredentialRepository.deleteAll();
    snmpProtocolRepository.deleteAll();
    rsuModelRepository.deleteAll();
    intersectionRepository.deleteAll();
    organizationRepository.deleteAll();
  }

  @Nested
  class GetAllIntersections {

    @Test
    void superuser_noOrgScope_returnsAllIntersections() {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, org));

      IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, true, List.of());

      assertNotNull(result.getIntersectionData());
      assertEquals(1, result.getIntersectionData().size());
      assertEquals("1123", result.getIntersectionData().getFirst().getIntersectionId());
    }

    @Test
    void withScopedOrg_returnsScopedIntersections() {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      String orgName = org.getName();
      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, org));


      IntersectionListResponse result = adminIntersectionService.getAllIntersections(orgName, false, List.of(orgName));

      assertEquals(1, result.getIntersectionData().size());
      assertEquals("1123", result.getIntersectionData().getFirst().getIntersectionId());
    }

    @Test
    void nonSuperuser_multipleQualifiedOrgs_returnsMatchingIntersections() {
      Organization orgA = organizationRepository.save(fixtures.createRandomOrg());
      Organization orgB = organizationRepository.save(fixtures.createRandomOrg());
      String orgAName = orgA.getName();
      String orgBName = orgB.getName();

      Intersection i1 = intersectionRepository.save(fixtures.createIntersection("1001"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i1, orgA));
      Intersection i2 = intersectionRepository.save(fixtures.createIntersection("1002"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i2, orgB));
      intersectionRepository.save(fixtures.createIntersection("1003")); // no org — should be excluded


      IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, false, List.of(orgAName, orgBName));

      assertEquals(2, result.getIntersectionData().size());
    }

    @Test
    void noResults_returnsEmptyList() {
      IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, true, List.of());

      assertNotNull(result.getIntersectionData());
      assertTrue(result.getIntersectionData().isEmpty());
    }

    @Test
    void attachesRsuIpsToCorrectIntersection() throws UnknownHostException {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, org));
      Rsu rsu = saveRsu("192.168.1.1", org);
      rsuIntersectionRepository.save(fixtures.createRsuIntersection(rsu, i));


      IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, true, List.of());

      assertEquals(1, result.getIntersectionData().size());
      assertEquals(List.of("192.168.1.1"), result.getIntersectionData().getFirst().getRsus());
    }

    @Test
    void nonSuperuser_emptyQualifiedOrgs_returnsEmptyList() {
      intersectionRepository.save(fixtures.createIntersection("1123"));


      IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, false, List.of());

      assertNotNull(result.getIntersectionData());
      assertTrue(result.getIntersectionData().isEmpty());
    }
  }

  @Nested
  class GetIntersection {

    @Test
    void notFound_returnsEmptyDtoWithAllowedSelections() {
      organizationRepository.save(fixtures.createRandomOrg());


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "9999", null, true, List.of(), List.of());

      assertNotNull(result.getIntersectionDto());
      assertNull(result.getIntersectionDto().getIntersectionId());
      assertNotNull(result.getAllowedSelections());
    }

    @Test
    void foundAsSuperuser_returnsFullDataWithAllOrgs() throws UnknownHostException {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      String orgName = org.getName();
      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, org));
      Rsu rsu = saveRsu("192.168.1.1", org);
      rsuIntersectionRepository.save(fixtures.createRsuIntersection(rsu, i));


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", null, true, List.of(), List.of());

      assertEquals("1123", result.getIntersectionDto().getIntersectionId());
      assertNotNull(result.getAllowedSelections());
      assertTrue(result.getAllowedSelections().getOrganizations().contains(orgName));
      assertEquals(List.of("192.168.1.1"), result.getIntersectionDto().getRsus());
    }

    @Test
    void scopedOrgMatches_returnsFilteredOrgList() {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      String orgName = org.getName();
      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, org));


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", orgName, false, List.of(orgName), List.of(orgName));

      assertEquals("1123", result.getIntersectionDto().getIntersectionId());
      assertEquals(List.of(orgName), result.getIntersectionDto().getOrganizations());
    }

    @Test
    void scopedOrgDoesNotMatch_returnsEmptyDto() {
      Organization orgA = organizationRepository.save(fixtures.createRandomOrg());
      Organization orgB = organizationRepository.save(fixtures.createRandomOrg());
      String orgBName = orgB.getName();

      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, orgA));


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", orgBName, false, List.of(orgBName), List.of(orgBName));

      assertNull(result.getIntersectionDto().getIntersectionId());
    }

    @Test
    void qualifiedOrgsMatch_returnsOnlyUserQualifiedOrgs() {
      Organization orgA = organizationRepository.save(fixtures.createRandomOrg());
      Organization orgB = organizationRepository.save(fixtures.createRandomOrg());
      String orgAName = orgA.getName();

      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, orgA));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, orgB));


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", null, false, List.of(orgAName), List.of(orgAName));

      assertEquals("1123", result.getIntersectionDto().getIntersectionId());
      assertEquals(List.of(orgAName), result.getIntersectionDto().getOrganizations());
    }

    @Test
    void qualifiedOrgsNoMatch_returnsEmptyDto() {
      Organization orgA = organizationRepository.save(fixtures.createRandomOrg());
      Organization orgB = organizationRepository.save(fixtures.createRandomOrg());
      String orgAName = orgA.getName();

      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, orgB));


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", null, false, List.of(orgAName), List.of(orgAName));

      assertNull(result.getIntersectionDto().getIntersectionId());
    }

    @Test
    void nonSuperuser_allowedSelectionsUsesOperatorOrgs() throws UnknownHostException {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      String orgName = org.getName();
      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, org));
      Rsu rsu = saveRsu("10.0.0.1", org);
      rsuOrganizationRepository.save(fixtures.createRsuOrganization(rsu, org));


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", orgName, false, List.of(orgName), List.of(orgName));

      AllowedSelections allowed = result.getAllowedSelections();
      assertEquals(List.of(orgName), allowed.getOrganizations());
      assertEquals(List.of("10.0.0.1"), allowed.getRsus());
    }
  }

  @Nested
  class PatchIntersection {

    @Test
    void basicUpdate_renumbersIntersection() {
      intersectionRepository.save(fixtures.createIntersection("1000"));


      IntersectionPatch patch = new IntersectionPatch(
        1000, 1001, new RefPt(40.0, -105.0), null, null, null,
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());

      String result = adminIntersectionService.patchIntersection(patch);


      assertEquals("Intersection successfully modified", result);
      assertTrue(intersectionRepository.findByIntersectionNumber("1001").isPresent());
      assertFalse(intersectionRepository.findByIntersectionNumber("1000").isPresent());
    }

    @Test
    void withOptionalFields_updatesNonNullFields() {
      intersectionRepository.save(fixtures.createIntersection("1000"));


      Bbox bbox = new Bbox(39.9, -105.2, 40.1, -105.0);
      IntersectionPatch patch = new IntersectionPatch(
        1000, 1000, new RefPt(40.0, -105.0), bbox, "Main St", null,
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());

      adminIntersectionService.patchIntersection(patch);


      Intersection updated = intersectionRepository.findByIntersectionNumber("1000").orElseThrow();
      assertEquals("Main St", updated.getIntersectionName());
      assertNotNull(updated.getBbox());
    }

    @Test
    void nullOptionalFields_preservesExistingValues() {
      Intersection existing = intersectionRepository.save(fixtures.createIntersection("1000"));
      existing.setIntersectionName("Existing Name");
      intersectionRepository.save(existing);


      IntersectionPatch patch = new IntersectionPatch(
        1000, 1000, new RefPt(40.0, -105.0), null, null, null,
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());

      adminIntersectionService.patchIntersection(patch);


      Intersection updated = intersectionRepository.findByIntersectionNumber("1000").orElseThrow();
      assertEquals("Existing Name", updated.getIntersectionName());
    }

    @Test
    void orgsToAdd_createsAssociations() {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      String orgName = org.getName();
      intersectionRepository.save(fixtures.createIntersection("1000"));


      IntersectionPatch patch = new IntersectionPatch(
        1000, 1000, new RefPt(40.0, -105.0), null, null, null,
        List.of(orgName), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());

      adminIntersectionService.patchIntersection(patch);


      List<Intersection> result = intersectionRepository.findAllByOrgNameWithOrgs(orgName);
      assertEquals(1, result.size());
      assertEquals("1000", result.getFirst().getIntersectionNumber());
    }

    @Test
    void orgsToRemove_deletesAssociations() {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      String orgName = org.getName();
      Intersection i = intersectionRepository.save(fixtures.createIntersection("1000"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, org));


      IntersectionPatch patch = new IntersectionPatch(
        1000, 1000, new RefPt(40.0, -105.0), null, null, null,
        Collections.emptyList(), List.of(orgName),
        Collections.emptyList(), Collections.emptyList());

      adminIntersectionService.patchIntersection(patch);


      assertTrue(intersectionRepository.findAllByOrgNameWithOrgs(orgName).isEmpty());
    }

    @Test
    void rsusToAdd_createsAssociations() throws UnknownHostException {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      intersectionRepository.save(fixtures.createIntersection("1000"));
      saveRsu("192.168.1.1", org);


      IntersectionPatch patch = new IntersectionPatch(
        1000, 1000, new RefPt(40.0, -105.0), null, null, null,
        Collections.emptyList(), Collections.emptyList(),
        List.of("192.168.1.1"), Collections.emptyList());

      adminIntersectionService.patchIntersection(patch);


      assertEquals(1, rsuIntersectionRepository.findRsuIpsByIntersectionNumber("1000").size());
    }

    @Test
    void rsusToRemove_deletesAssociations() throws UnknownHostException {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      Intersection intersection = intersectionRepository.save(fixtures.createIntersection("1000"));
      Rsu rsu = saveRsu("192.168.1.1", org);
      rsuIntersectionRepository.save(fixtures.createRsuIntersection(rsu, intersection));


      IntersectionPatch patch = new IntersectionPatch(
        1000, 1000, new RefPt(40.0, -105.0), null, null, null,
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), List.of("192.168.1.1"));

      adminIntersectionService.patchIntersection(patch);


      assertTrue(rsuIntersectionRepository.findRsuIpsByIntersectionNumber("1000").isEmpty());
    }

    @Test
    void intersectionNotFound_throws404() {
      IntersectionPatch patch = new IntersectionPatch(
        9999, 9999, new RefPt(40.0, -105.0), null, null, null,
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> adminIntersectionService.patchIntersection(patch));
      assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void emptyRelationshipLists_noAssociations_createdOrRemoved() {
      intersectionRepository.save(fixtures.createIntersection("1000"));


      IntersectionPatch patch = new IntersectionPatch(
        1000, 1000, new RefPt(40.0, -105.0), null, null, null,
        Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList());

      adminIntersectionService.patchIntersection(patch);


      assertTrue(intersectionOrganizationRepository.findAll().isEmpty());
      assertTrue(rsuIntersectionRepository.findAll().isEmpty());
    }
  }

  @Nested
  class DeleteIntersection {

    @Test
    void existingIntersection_deletesRelationshipsAndReturnsMessage() throws UnknownHostException {
      Organization org = organizationRepository.save(fixtures.createRandomOrg());
      Intersection i = intersectionRepository.save(fixtures.createIntersection("1123"));
      intersectionOrganizationRepository.save(fixtures.createIntersectionOrganization(i, org));
      Rsu rsu = saveRsu("192.168.1.1", org);
      rsuIntersectionRepository.save(fixtures.createRsuIntersection(rsu, i));


      String result = adminIntersectionService.deleteIntersection("1123");


      assertEquals("Intersection successfully deleted", result);
      assertFalse(intersectionRepository.findByIntersectionNumber("1123").isPresent());
      assertTrue(rsuIntersectionRepository.findRsuIpsByIntersectionNumber("1123").isEmpty());
      assertTrue(intersectionOrganizationRepository.findAll().isEmpty());
    }

    @Test
    void notFound_throws404() {
      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> adminIntersectionService.deleteIntersection("9999"));
      assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
  }

  private Rsu saveRsu(String ip, Organization org) throws UnknownHostException {
    Manufacturer mfr = manufacturerRepository.save(fixtures.createRandomManufacturer());
    RsuModel model = rsuModelRepository.save(fixtures.createRandomRsuModel(mfr));
    RsuCredential cred = rsuCredentialRepository.save(fixtures.createRandomRsuCredential(org));
    SnmpCredential snmpCred = snmpCredentialRepository.save(fixtures.createRandomSnmpCredential(org));
    SnmpProtocol proto = snmpProtocolRepository.save(fixtures.createRandomSnmpProtocol());
    return rsuRepository.save(fixtures.createRsu(ip, model, cred, snmpCred, proto));
  }

}
