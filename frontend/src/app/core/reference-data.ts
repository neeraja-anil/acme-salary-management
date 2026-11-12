import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, of, tap } from 'rxjs';

import { Country, ReferenceData } from './models';

/**
 * Countries, departments, job titles, levels, reasons and FX rates. Loaded once, right after sign-in
 * (see auth.guards.ts), because almost every screen needs them and they only change with a deployment.
 */
@Injectable({ providedIn: 'root' })
export class ReferenceDataService {
  private readonly http = inject(HttpClient);
  private readonly loaded = signal<ReferenceData | null>(null);

  readonly data = this.loaded.asReadonly();
  readonly loadFailed = signal(false);

  private pending: Promise<unknown> | null = null;

  /** Loads the lookups once; later calls reuse the first answer. A failed load is tried again next time. */
  ensureLoaded(): Promise<unknown> {
    if (this.loaded()) {
      return Promise.resolve();
    }
    this.pending ??= this.load().finally(() => (this.pending = null));
    return this.pending;
  }

  /** Never rejects: on failure the shell shows an error instead of the app. */
  private load(): Promise<unknown> {
    this.loadFailed.set(false);
    return firstValueFrom(
      this.http.get<ReferenceData>('/api/reference').pipe(
        tap((data) => this.loaded.set(data)),
        catchError(() => {
          this.loadFailed.set(true);
          return of(null);
        }),
      ),
    );
  }

  /** For use after start-up, when the shell guarantees the data is there. */
  get snapshot(): ReferenceData {
    const data = this.loaded();
    if (!data) {
      throw new Error('Reference data is not loaded');
    }
    return data;
  }

  country(code: string): Country | undefined {
    return this.snapshot.countries.find((c) => c.code === code);
  }

  jobTitlesFor(departmentIds: readonly number[]): ReferenceData['jobTitles'] {
    const titles = this.snapshot.jobTitles;
    return departmentIds.length === 0 ? titles : titles.filter((t) => departmentIds.includes(t.departmentId));
  }
}
