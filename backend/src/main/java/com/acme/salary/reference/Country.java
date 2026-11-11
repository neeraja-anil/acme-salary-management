package com.acme.salary.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A country ACME employs in. The country fixes the salary currency of its employees. */
@Entity
@Table(name = "country")
public class Country {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    protected Country() {
    }

    public Country(String code, String name, String currencyCode) {
        this.code = code;
        this.name = name;
        this.currencyCode = currencyCode;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
}
