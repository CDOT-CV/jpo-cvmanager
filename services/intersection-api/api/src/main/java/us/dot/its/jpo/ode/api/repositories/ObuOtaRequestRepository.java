package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import us.dot.its.jpo.ode.api.models.postgres.tables.ObuOtaRequest;

public interface ObuOtaRequestRepository
        extends JpaRepository<ObuOtaRequest, Integer>, JpaSpecificationExecutor<ObuOtaRequest> {

    /** Loads each request's manufacturer with the page instead of one query per row. */
    @Override
    @EntityGraph(attributePaths = "manufacturer")
    Page<ObuOtaRequest> findAll(Specification<ObuOtaRequest> specification, Pageable pageable);
}
