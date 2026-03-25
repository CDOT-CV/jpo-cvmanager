package us.dot.its.jpo.ode.api.services;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

import us.dot.its.jpo.ode.api.models.admin.intersection.AllowedSelections;
import us.dot.its.jpo.ode.api.models.admin.intersection.Bbox;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionListResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionPatch;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionSingleResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.RefPt;
import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.IntersectionOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuIntersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class AdminIntersectionServiceTest {
  @Autowired
  private AdminIntersectionService adminIntersectionService;

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

  private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

  @Nested
  class GetAllIntersections {

    @Test
    void superuser_noOrgScope_returnsAllIntersections() {
      Organization org = saveOrg("OrgA-" + uuid());
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, org);

      IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, true, List.of());

      assertNotNull(result.getIntersectionData());
      assertEquals(1, result.getIntersectionData().size());
      assertEquals("1123", result.getIntersectionData().getFirst().getIntersectionId());
    }

    @Test
    void withScopedOrg_returnsScopedIntersections() {
      String orgName = "OrgA-" + uuid();
      Organization org = saveOrg(orgName);
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, org);


      IntersectionListResponse result = adminIntersectionService.getAllIntersections(orgName, false, List.of(orgName));

      assertEquals(1, result.getIntersectionData().size());
      assertEquals("1123", result.getIntersectionData().getFirst().getIntersectionId());
    }

    @Test
    void nonSuperuser_multipleQualifiedOrgs_returnsMatchingIntersections() {
      String orgAName = "OrgA-" + uuid();
      String orgBName = "OrgB-" + uuid();
      Organization orgA = saveOrg(orgAName);
      Organization orgB = saveOrg(orgBName);

      Intersection i1 = saveIntersection("1001");
      linkIntersectionToOrg(i1, orgA);
      Intersection i2 = saveIntersection("1002");
      linkIntersectionToOrg(i2, orgB);
      saveIntersection("1003"); // no org — should be excluded


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
      Organization org = saveOrg("OrgA-" + uuid());
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, org);
      Rsu rsu = saveRsu("192.168.1.1", org);
      linkRsuToIntersection(rsu, i);


      IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, true, List.of());

      assertEquals(1, result.getIntersectionData().size());
      assertEquals(List.of("192.168.1.1"), result.getIntersectionData().getFirst().getRsus());
    }

    @Test
    void nonSuperuser_emptyQualifiedOrgs_returnsEmptyList() {
      saveIntersection("1123");


      IntersectionListResponse result = adminIntersectionService.getAllIntersections(null, false, List.of());

      assertNotNull(result.getIntersectionData());
      assertTrue(result.getIntersectionData().isEmpty());
    }
  }

  @Nested
  class GetIntersection {

    @Test
    void notFound_returnsEmptyDtoWithAllowedSelections() {
      saveOrg("OrgA-" + uuid());


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "9999", null, true, List.of(), List.of());

      assertNotNull(result.getIntersectionDto());
      assertNull(result.getIntersectionDto().getIntersectionId());
      assertNotNull(result.getAllowedSelections());
    }

    @Test
    void foundAsSuperuser_returnsFullDataWithAllOrgs() throws UnknownHostException {
      String orgName = "OrgA-" + uuid();
      Organization org = saveOrg(orgName);
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, org);
      Rsu rsu = saveRsu("192.168.1.1", org);
      linkRsuToIntersection(rsu, i);


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", null, true, List.of(), List.of());

      assertEquals("1123", result.getIntersectionDto().getIntersectionId());
      assertNotNull(result.getAllowedSelections());
      assertTrue(result.getAllowedSelections().getOrganizations().contains(orgName));
      assertEquals(List.of("192.168.1.1"), result.getIntersectionDto().getRsus());
    }

    @Test
    void scopedOrgMatches_returnsFilteredOrgList() {
      String orgName = "OrgA-" + uuid();
      Organization org = saveOrg(orgName);
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, org);


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", orgName, false, List.of(orgName), List.of(orgName));

      assertEquals("1123", result.getIntersectionDto().getIntersectionId());
      assertEquals(List.of(orgName), result.getIntersectionDto().getOrganizations());
    }

    @Test
    void scopedOrgDoesNotMatch_returnsEmptyDto() {
      String orgAName = "OrgA-" + uuid();
      String orgBName = "OrgB-" + uuid();
      Organization orgA = saveOrg(orgAName);
      saveOrg(orgBName);
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, orgA);


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", orgBName, false, List.of(orgBName), List.of(orgBName));

      assertNull(result.getIntersectionDto().getIntersectionId());
    }

    @Test
    void qualifiedOrgsMatch_returnsOnlyUserQualifiedOrgs() {
      String orgAName = "OrgA-" + uuid();
      String orgBName = "OrgB-" + uuid();
      Organization orgA = saveOrg(orgAName);
      Organization orgB = saveOrg(orgBName);
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, orgA);
      linkIntersectionToOrg(i, orgB);


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", null, false, List.of(orgAName), List.of(orgAName));

      assertEquals("1123", result.getIntersectionDto().getIntersectionId());
      assertEquals(List.of(orgAName), result.getIntersectionDto().getOrganizations());
    }

    @Test
    void qualifiedOrgsNoMatch_returnsEmptyDto() {
      String orgAName = "OrgA-" + uuid();
      String orgBName = "OrgB-" + uuid();
      saveOrg(orgAName);
      Organization orgB = saveOrg(orgBName);
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, orgB);


      IntersectionSingleResponse result = adminIntersectionService.getIntersection(
        "1123", null, false, List.of(orgAName), List.of(orgAName));

      assertNull(result.getIntersectionDto().getIntersectionId());
    }

    @Test
    void nonSuperuser_allowedSelectionsUsesOperatorOrgs() throws UnknownHostException {
      String orgName = "OrgA-" + uuid();
      Organization org = saveOrg(orgName);
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, org);
      Rsu rsu = saveRsu("10.0.0.1", org);
      linkRsuToOrg(rsu, org);


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
      saveIntersection("1000");


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
      saveIntersection("1000");


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
      Intersection existing = saveIntersection("1000");
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
      String orgName = "OrgA-" + uuid();
      saveOrg(orgName);
      saveIntersection("1000");


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
      String orgName = "OrgA-" + uuid();
      Organization org = saveOrg(orgName);
      Intersection i = saveIntersection("1000");
      linkIntersectionToOrg(i, org);


      IntersectionPatch patch = new IntersectionPatch(
        1000, 1000, new RefPt(40.0, -105.0), null, null, null,
        Collections.emptyList(), List.of(orgName),
        Collections.emptyList(), Collections.emptyList());

      adminIntersectionService.patchIntersection(patch);


      assertTrue(intersectionRepository.findAllByOrgNameWithOrgs(orgName).isEmpty());
    }

    @Test
    void rsusToAdd_createsAssociations() throws UnknownHostException {
      Organization org = saveOrg("OrgA-" + uuid());
      saveIntersection("1000");
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
      Organization org = saveOrg("OrgA-" + uuid());
      Intersection intersection = saveIntersection("1000");
      Rsu rsu = saveRsu("192.168.1.1", org);
      linkRsuToIntersection(rsu, intersection);


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
    void emptyRelationshipLists_noAssociationsCreatedOrRemoved() {
      saveIntersection("1000");


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
      Organization org = saveOrg("OrgA-" + uuid());
      Intersection i = saveIntersection("1123");
      linkIntersectionToOrg(i, org);
      Rsu rsu = saveRsu("192.168.1.1", org);
      linkRsuToIntersection(rsu, i);


      String result = adminIntersectionService.deleteIntersection("1123");


      assertEquals("Intersection successfully deleted", result);
      assertFalse(intersectionRepository.findByIntersectionNumber("1123").isPresent());
      assertTrue(rsuIntersectionRepository.findRsuIpsByIntersectionNumber("1123").isEmpty());
    }

    @Test
    void notFound_throws404() {
      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> adminIntersectionService.deleteIntersection("9999"));
      assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
  }


  private static Point point(double lon, double lat) {
    return GF.createPoint(new Coordinate(lon, lat));
  }

  private Organization saveOrg(String name) {
    Organization org = new Organization();
    org.setName(name);
    return organizationRepository.save(org);
  }

  private Intersection saveIntersection(String number) {
    Intersection i = new Intersection();
    i.setIntersectionNumber(number);
    i.setRefPt(point(-105.0, 40.0));
    return intersectionRepository.save(i);
  }

  private IntersectionOrganization linkIntersectionToOrg(Intersection intersection, Organization org) {
    IntersectionOrganization io = new IntersectionOrganization();
    io.setIntersection(intersection);
    io.setOrganization(org);
    return intersectionOrganizationRepository.save(io);
  }

  /**
   * Builds and persists a minimal Rsu with all required FK dependencies.
   * Manufacturer is persisted via EntityManager since no dedicated repository exists.
   */
  private Rsu saveRsu(String ip, Organization credentialOwner) throws UnknownHostException {
    Manufacturer mfr = new Manufacturer();

    RsuModel model = new RsuModel();
    model.setName("Model-" + uuid());
    model.setSupportedRadio("DSRC");
    model.setManufacturer(mfr);
    rsuModelRepository.save(model);

    RsuCredential cred = new RsuCredential();
    cred.setUsername("user-" + uuid());
    cred.setPassword("pass");
    cred.setNickname("cred-" + uuid());
    cred.setOwnerOrganization(credentialOwner);
    rsuCredentialRepository.save(cred);

    SnmpCredential snmpCred = new SnmpCredential();
    snmpCred.setUsername("snmpuser-" + uuid());
    snmpCred.setPassword("snmppass");
    snmpCred.setNickname("snmp-" + uuid());
    snmpCred.setOwnerOrganization(credentialOwner);
    snmpCredentialRepository.save(snmpCred);

    SnmpProtocol proto = new SnmpProtocol();
    proto.setProtocolCode("NTCIP1218");
    proto.setNickname("NTCIP-" + uuid());
    snmpProtocolRepository.save(proto);

    Rsu rsu = new Rsu();
    rsu.setIpv4Address(InetAddress.getByName(ip));
    rsu.setGeography(point(-105.0, 40.0));
    rsu.setMilepost(0.0);
    rsu.setSerialNumber("SN-" + uuid());
    rsu.setIssScmsId("ISS-" + uuid());
    rsu.setPrimaryRoute("CO-470");
    rsu.setModel(model);
    rsu.setCredential(cred);
    rsu.setSnmpCredential(snmpCred);
    rsu.setSnmpProtocol(proto);
    return rsuRepository.save(rsu);
  }

  private RsuIntersection linkRsuToIntersection(Rsu rsu, Intersection intersection) {
    RsuIntersection ri = new RsuIntersection();
    ri.setRsu(rsu);
    ri.setIntersection(intersection);
    return rsuIntersectionRepository.save(ri);
  }

  private RsuOrganization linkRsuToOrg(Rsu rsu, Organization org) {
    RsuOrganization ro = new RsuOrganization();
    ro.setRsu(rsu);
    ro.setOrganization(org);
    return rsuOrganizationRepository.save(ro);
  }

  private static String uuid() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

}
