package com.acme.salary.salary;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {

    /** Newest first; id breaks ties between two changes on the same effective date. */
    List<SalaryHistory> findByEmployeeIdOrderByEffectiveDateDescIdDesc(Long employeeId);
}
