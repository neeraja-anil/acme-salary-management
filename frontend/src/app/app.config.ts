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
import { AuthService } from './core/auth.service';
import { loadingInterceptor, sessionInterceptor, unexpectedErrorInterceptor } from './core/http.interceptors';
import { AcmePreset } from './theme';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Route parameters (e.g. :id) are passed to page components as inputs.
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([loadingInterceptor, sessionInterceptor, unexpectedErrorInterceptor])),
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
    // Before the first page: get the CSRF cookie and find out whether someone is already signed in.
    // (Lookups load after sign-in, in the route guard, because the API only answers signed-in users.)
    provideAppInitializer(() => inject(AuthService).start()),
  ],
};
