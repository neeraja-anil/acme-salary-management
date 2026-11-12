import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, firstValueFrom, of, switchMap, tap } from 'rxjs';

import { User } from './models';

/**
 * Who is signed in (US-18, US-19). The session itself is an HttpOnly cookie the app never sees; this
 * service only keeps the user's details for the screen, and asks the server when it needs to know.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly current = signal<User | null>(null);
  readonly user = this.current.asReadonly();
  readonly isSuperAdmin = computed(() => this.current()?.role === 'SUPER_ADMIN');

  /** Whether the server has been asked yet; until then "nobody" might just mean "don't know yet". */
  private checked = false;

  /**
   * At start-up: get the CSRF cookie (needed before signing in), then ask who is signed in.
   * Never rejects; a server that can't be reached simply means nobody is signed in yet.
   */
  async start(): Promise<void> {
    await firstValueFrom(this.http.get('/api/auth/csrf').pipe(catchError(() => of(null))));
    await this.restore();
  }

  /** The signed-in user, asking the server once if needed; null when nobody is signed in. */
  async restore(): Promise<User | null> {
    if (!this.checked) {
      const user = await firstValueFrom(this.http.get<User>('/api/auth/me').pipe(catchError(() => of(null))));
      this.current.set(user);
      this.checked = true;
    }
    return this.current();
  }

  /**
   * Gets a fresh CSRF cookie first: signing out clears it on the server, and a sign-in without one
   * would be refused with 403.
   */
  login(username: string, password: string): Observable<User> {
    return this.http.get('/api/auth/csrf').pipe(
      switchMap(() => this.http.post<User>('/api/auth/login', { username, password })),
      tap((user) => {
        this.current.set(user);
        this.checked = true;
      }),
    );
  }

  /** Ends the server session, then shows the sign-in page whatever the server said. */
  logout(): void {
    this.http
      .post<void>('/api/auth/logout', null)
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this.current.set(null);
        this.router.navigate(['/login']);
      });
  }

  /** The server says the session is gone (expired, or the user was disabled). */
  sessionEnded(): void {
    this.current.set(null);
  }
}
