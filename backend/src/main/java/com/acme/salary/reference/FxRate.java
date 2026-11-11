package com.acme.salary.reference;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.acme.salary.common.persistence.BigDecimalStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Static rate used to convert a currency to USD for org-wide reporting. */
@Entity
@Table(name = "fx_rate")
public class FxRate {

    @Id
    @Column(name = "currency_code")
    private String currencyCode;

    @Convert(converter = BigDecimalStringConverter.class)
    @Column(name = "rate_to_usd", nullable = false)
    private BigDecimal rateToUsd;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    protected FxRate() {
    }

    public FxRate(String currencyCode, BigDecimal rateToUsd, LocalDate asOfDate) {
        this.currencyCode = currencyCode;
        this.rateToUsd = rateToUsd;
        this.asOfDate = asOfDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getRateToUsd() {
        return rateToUsd;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }
}
