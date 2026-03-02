package com.trihydro.rsuinfobridge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.trihydro.rsuinfobridge.models.tables.Rsu;

import java.util.List;

@Repository
public interface RsuRepository extends JpaRepository<Rsu, Integer> {
    @Query("SELECT rsu FROM Rsu rsu WHERE rsu.rsuOption.timDeposit = :rsuOptionTimDeposit")
    List<Rsu> findByRsuOption_TimDeposit(Boolean rsuOptionTimDeposit);
}
