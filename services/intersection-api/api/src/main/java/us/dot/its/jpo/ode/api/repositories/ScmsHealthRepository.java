package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;

import java.net.InetAddress;
import java.util.List;

@Repository
public interface ScmsHealthRepository extends JpaRepository<ScmsHealth, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM ScmsHealth ro WHERE ro.rsu.ipv4Address = :ipv4Address")
    void removeScmsHealthByIpv4Address(@Param("ipv4Address") InetAddress ipv4Address);

    @Modifying
    @Transactional
    @Query("DELETE FROM ScmsHealth ro WHERE ro.rsu.ipv4Address IN :ipv4Addresses")
    void removeMultipleScmsHealthByIpv4Address(@Param("ipv4Addresses") List<InetAddress> ipv4Addresses);

    /**
     * Retrieves the latest SCMS health record for each RSU within a specific organization.
     * <p>
     * This query is functionally equivalent to the legacy Python implementation.
     * It achieves parity by:
     * <ul>
     *     <li>Using a <b>LEFT JOIN</b> to ensure all RSUs in the organization are returned, even those without health
     *     records (matching Python's LEFT JOIN).</li>
     *     <li>Using a <b>correlated subquery</b> to select only the most recent health record per RSU
     *     by selecting the record with the highest ID (since IDs are auto-incrementing, the highest ID
     *     corresponds to the latest record).</li>
     *     <li>Filtering by the <b>organization name</b> and sorting by <b>IPv4 address</b>.</li>
     * </ul>
     *
     * @param organization The name of the organization to filter by.
     * @return A list of projections containing RSU and their latest SCMS health data.
     */
    @Query("SELECT new us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection(rd, sh) FROM Rsu rd " +
            "JOIN rd.rsuOrganizations ro " +
            "JOIN ro.organization o " +
            "LEFT JOIN ScmsHealth sh ON sh.rsu = rd " +
            "AND sh.id = (SELECT MAX(sh2.id) FROM ScmsHealth sh2 WHERE sh2.rsu = rd) " +
            "WHERE o.name = :organization " +
            "ORDER BY rd.ipv4Address")
    List<ScmsHealthRsuProjection> findLatestScmsHealthByOrganization(@Param("organization") String organization);
}
