import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { ReferenceDataService } from './reference-data';

/**
 * Every screen except sign-in needs a signed-in user (US-18). Lookups are loaded once the user is known,
 * because the API only answers signed-in users.
 */
export const signedInGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const reference = inject(ReferenceDataService);
  if (!(await auth.restore())) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  await reference.ensureLoaded();
  return true;
};

/** User management is for super admins; anyone else lands on the dashboard. */
export const superAdminGuard: CanActivateFn = () =>
  inject(AuthService).isSuperAdmin() ? true : inject(Router).createUrlTree(['/dashboard']);

/** Someone already signed in who opens /login goes straight to the app. */
export const signedOutGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.restore()) ? router.createUrlTree(['/dashboard']) : true;
};
