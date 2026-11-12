import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CreateUserRequest, User } from './models';

/** User management, for super admins only (api-contracts.md section 3a). */
@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly http = inject(HttpClient);

  list(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }

  /** New users are always HR managers. */
  create(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>('/api/users', request);
  }

  setEnabled(id: number, enabled: boolean): Observable<User> {
    return this.http.patch<User>(`/api/users/${id}`, { enabled });
  }

  resetPassword(id: number, password: string): Observable<void> {
    return this.http.put<void>(`/api/users/${id}/password`, { password });
  }
}
