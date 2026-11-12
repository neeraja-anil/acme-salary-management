package com.acme.salary.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Converts whole-unit local amounts to whole US dollars using the static {@code fx_rate} table.
 * Rates only change through a migration, so they are loaded once and cached.
 */
@Component
public class CurrencyConverter {

    private final FxRateRepository fxRateRepository;
    private volatile Map<String, BigDecimal> ratesToUsd;

    public CurrencyConverter(FxRateRepository fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    /** Converts {@code amount} in {@code currency} to USD, rounded half-up to whole dollars. */
    public long toUsd(long amount, String currency) {
        BigDecimal rate = rates().get(currency);
        if (rate == null) {
            throw new IllegalStateException("No FX rate configured for currency " + currency);
        }
        return BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Every currency's rate to USD (e.g. GBP → 1.3400). Unmodifiable. */
    public Map<String, BigDecimal> ratesToUsd() {
        return rates();
    }

    private Map<String, BigDecimal> rates() {
        Map<String, BigDecimal> cached = ratesToUsd;
        if (cached == null) {
            cached = fxRateRepository.findAll().stream()
                    .collect(Collectors.toUnmodifiableMap(FxRate::getCurrencyCode, FxRate::getRateToUsd));
            ratesToUsd = cached;
        }
        return cached;
    }
}
