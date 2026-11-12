import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { ReferenceData } from '../app/core/models';
import { ReferenceDataService } from '../app/core/reference-data';

/**
 * What every page test needs: HTTP with a fake backend (HttpTestingController), a router, PrimeNG
 * without its theme (styles don't matter here), and the toast and confirm services.
 */
export function pageTestProviders(): (Provider | EnvironmentProviders)[] {
  return [
    provideHttpClient(),
    provideHttpClientTesting(),
    provideRouter([]),
    providePrimeNG({ ripple: false }),
    MessageService,
    ConfirmationService,
  ];
}

/** A small, fixed set of lookups: enough to fill every dropdown the tests use. */
export const REFERENCE_DATA: ReferenceData = {
  countries: [
    { code: 'DE', name: 'Germany', currency: 'EUR' },
    { code: 'IN', name: 'India', currency: 'INR' },
    { code: 'US', name: 'United States', currency: 'USD' },
  ],
  departments: [
    { id: 1, name: 'Engineering' },
    { id: 4, name: 'Sales' },
  ],
  jobTitles: [
    { id: 1, name: 'Software Engineer', departmentId: 1 },
    { id: 2, name: 'QA Engineer', departmentId: 1 },
    { id: 10, name: 'Account Executive', departmentId: 4 },
  ],
  jobLevels: [
    { code: 'L1', name: 'Associate' },
    { code: 'L2', name: 'Mid' },
    { code: 'L3', name: 'Senior' },
    { code: 'L4', name: 'Lead' },
    { code: 'L5', name: 'Principal' },
  ],
  salaryChangeReasons: ['PROMOTION', 'ANNUAL_REVIEW', 'MARKET_ADJUSTMENT', 'CORRECTION', 'OTHER'],
  fxRates: [
    { currency: 'EUR', rateToUsd: '1.1700', asOf: '2026-01-01' },
    { currency: 'INR', rateToUsd: '0.0113', asOf: '2026-01-01' },
    { currency: 'USD', rateToUsd: '1.0000', asOf: '2026-01-01' },
  ],
};

/** Loads the lookups through the real service, answering its request with REFERENCE_DATA. */
export async function loadReferenceData(): Promise<void> {
  const loaded = TestBed.inject(ReferenceDataService).ensureLoaded();
  TestBed.inject(HttpTestingController).expectOne('/api/reference').flush(REFERENCE_DATA);
  await loaded;
}
