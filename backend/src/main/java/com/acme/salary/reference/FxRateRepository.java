package com.acme.salary.reference;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, String> {

    List<FxRate> findAllByOrderByCurrencyCodeAsc();
}
