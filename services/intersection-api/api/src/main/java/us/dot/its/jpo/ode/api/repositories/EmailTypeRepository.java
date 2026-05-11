package us.dot.its.jpo.ode.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import us.dot.its.jpo.ode.api.models.postgres.tables.EmailType;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailTypeRepository extends JpaRepository<EmailType, Integer> {
    List<EmailType> findAll();

    Optional<EmailType> findByEmailType(String emailType);
}