import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { loadingInterceptor, unexpectedErrorInterceptor } from './core/http.interceptors';
import { ReferenceDataService } from './core/reference-data';
import { AcmePreset } from './theme';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Route parameters (e.g. :id) are passed to page components as inputs.
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([loadingInterceptor, unexpectedErrorInterceptor])),
    providePrimeNG({
      theme: {
        preset: AcmePreset,
        options: {
          // Light only for now: a class the app never sets, so components can't follow the OS into dark mode.
          darkModeSelector: '.app-dark',
          // PrimeNG's styles go in a cascade layer, so styles.scss (unlayered) wins without specificity tricks.
          cssLayer: { name: 'primeng' },
        },
      },
      ripple: false,
      // Dates are typed and shown as DD/MM/YYYY (ui-design.md section 6); weeks start on Monday.
      translation: { dateFormat: 'dd/mm/yy', firstDayOfWeek: 1 },
    }),
    // One toast and one confirm dialog live in the shell; these services drive them.
    MessageService,
    ConfirmationService,
    // Lookups are needed on almost every screen, so load them once before the first page renders.
    provideAppInitializer(() => inject(ReferenceDataService).load()),
  ],
};
