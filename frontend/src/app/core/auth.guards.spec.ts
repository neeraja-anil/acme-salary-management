import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';

import { REFERENCE_DATA, pageTestProviders } from '../../testing/test-providers';
import { signedInGuard, superAdminGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { User } from './models';

const HR: User = { id: 2, username: 'Lena.HR', displayName: 'Lena Fischer', role: 'HR_MANAGER', enabled: true, createdAt: '2026-11-12T10:00:00Z' };
const ADMIN: User = { ...HR, id: 1, username: 'admin', role: 'SUPER_ADMIN' };

/** Who can open which screens (US-18, US-22). */
describe('route guards', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: pageTestProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function run(guard: typeof signedInGuard, url = '/insights') {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot)) as Promise<boolean | UrlTree> | boolean | UrlTree;
  }

  /** Answers the "who is signed in?" request the way the server would. */
  async function signIn(user: User | null): Promise<void> {
    const restored = TestBed.inject(AuthService).restore();
    const me = http.expectOne('/api/auth/me');
    if (user) {
      me.flush(user);
    } else {
      me.flush({ status: 401, code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });
    }
    await restored;
  }

  it('sends someone who is not signed in to the sign-in page, remembering where they were going', async () => {
    const result = run(signedInGuard);
    http.expectOne('/api/auth/me').flush({ status: 401, code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });

    expect(String(await result)).toBe('/login?returnUrl=%2Finsights');
  });

  it('lets a signed-in user in once the lookups have loaded', async () => {
    await signIn(HR);
    const result = run(signedInGuard);
    await Promise.resolve();
    http.expectOne('/api/reference').flush(REFERENCE_DATA);

    expect(await result).toBe(true);
  });

  it('keeps HR managers out of user management', async () => {
    await signIn(HR);
    expect(String(run(superAdminGuard))).toBe('/dashboard');
  });

  it('lets super admins into user management', async () => {
    await signIn(ADMIN);
    expect(run(superAdminGuard)).toBe(true);
  });
});
