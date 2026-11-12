package com.acme.salary.analytics;

import com.acme.salary.reference.JobLevel;

/**
 * One active employee's current salary with the attributes analytics groups by. A small projection,
 * so loading all 10k of them is cheap (no entities, no joins beyond country).
 */
public record SalaryPoint(String countryCode, String currency, Long departmentId, Long jobTitleId,
                          JobLevel jobLevel, long amount) {
}
