import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TabsModule } from 'primeng/tabs';
import { map } from 'rxjs';

import { ByCountryTab } from './by-country-tab';
import { ByDepartmentTab } from './by-department-tab';
import { PayBandsTab } from './pay-bands-tab';
import { RecentChangesTab } from './recent-changes-tab';

const TABS = ['country', 'department', 'job-title', 'recent'] as const;

/** Pay insights (US-12 to US-15). The open tab is kept in the URL (?tab=recent) so it can be linked to. */
@Component({
  selector: 'app-insights-page',
  imports: [TabsModule, ByCountryTab, ByDepartmentTab, PayBandsTab, RecentChangesTab],
  template: `
    <div class="page-header">
      <div>
        <h1>Pay insights</h1>
        <p class="subtitle">What we pay across countries, departments and job titles, and how it has changed.</p>
      </div>
    </div>
    <p-tabs [value]="selected()" (valueChange)="select($event)" [lazy]="true">
      <p-tablist>
        <p-tab value="country">By country</p-tab>
        <p-tab value="department">By department</p-tab>
        <p-tab value="job-title">By job title</p-tab>
        <p-tab value="recent">Recent changes</p-tab>
      </p-tablist>
      <p-tabpanels>
        <p-tabpanel value="country"><app-by-country-tab /></p-tabpanel>
        <p-tabpanel value="department"><app-by-department-tab /></p-tabpanel>
        <p-tabpanel value="job-title"><app-pay-bands-tab /></p-tabpanel>
        <p-tabpanel value="recent"><app-recent-changes-tab /></p-tabpanel>
      </p-tabpanels>
    </p-tabs>
  `,
})
export class InsightsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly tab = toSignal(this.route.queryParamMap.pipe(map((params) => params.get('tab'))));
  /** Unknown values in the URL fall back to the first tab. */
  protected readonly selected = computed(() => {
    const tab = this.tab();
    return TABS.includes(tab as (typeof TABS)[number]) ? (tab as (typeof TABS)[number]) : 'country';
  });

  protected select(tab: string | number | undefined): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { tab }, replaceUrl: true });
  }
}
