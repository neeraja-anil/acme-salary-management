package com.acme.salary.employee;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /** Directory search. Fetches the lookups in the same query to avoid N+1 selects per page. */
    @Override
    @EntityGraph(attributePaths = {"country", "department", "jobTitle"})
    Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"country", "department", "jobTitle"})
    Optional<Employee> findWithDetailsById(Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Highest code issued so far; codes are fixed-width ("EMP-000042") so text order = numeric order. */
    @Query("select max(e.employeeCode) from Employee e")
    Optional<String> findMaxEmployeeCode();
}
