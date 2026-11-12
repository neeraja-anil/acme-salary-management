import { Signal, WritableSignal, linkedSignal } from '@angular/core';

/** No table shows more rows than this at once. */
export const TABLE_PAGE_SIZE = 20;

/** "Showing 1–20 of 240", under every paged table. */
export const PAGE_REPORT = 'Showing {first}–{last} of {totalRecords}';

/**
 * Pages a list that the server returns in full (pay tables, salary history) with the table's own
 * paginator. Server-paged tables (directory, recent changes) ask the API for one page instead.
 *
 * Usage: `pager = new ClientPager(this.rows)`, then on the p-table bind
 * `[paginator]="true" [rows]="pager.size" [first]="pager.first()" (firstChange)="pager.first.set($event)"`.
 * Goes back to the first page whenever the list changes. The table itself goes back to the first page when
 * it is sorted, and `firstChange` reports that too.
 */
export class ClientPager<T> {
  readonly size = TABLE_PAGE_SIZE;
  /** Index of the first row shown. */
  readonly first: WritableSignal<number>;

  constructor(all: Signal<readonly T[]>) {
    this.first = linkedSignal({ source: all, computation: () => 0 });
  }
}
