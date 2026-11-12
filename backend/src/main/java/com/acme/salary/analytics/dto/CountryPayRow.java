package com.acme.salary.analytics.dto;

import com.acme.salary.analytics.PayStatistics;

/** Pay for one country (US-12): local currency, with the USD equivalent alongside. */
public record CountryPayRow(NamedCountry country, String currency, PayStatistics stats, PayStatistics statsUsd) {
}
