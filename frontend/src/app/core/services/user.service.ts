import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResult, User, UserRequest } from '../models/models';
import { api, toParams } from './api';

/** Administration des utilisateurs (auth-service, rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  search(filters: { q?: string; page?: number; size?: number }): Observable<PageResult<User>> {
    return this.http.get<PageResult<User>>(api('/api/users'), { params: toParams({ ...filters }) });
  }

  create(body: UserRequest): Observable<User> {
    return this.http.post<User>(api('/api/users'), body);
  }

  update(id: number, body: UserRequest): Observable<User> {
    return this.http.put<User>(api(`/api/users/${id}`), body);
  }

  setEnabled(id: number, enabled: boolean): Observable<User> {
    return this.http.patch<User>(api(`/api/users/${id}/${enabled ? 'enable' : 'disable'}`), {});
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.put<void>(api(`/api/users/${id}/password`), { newPassword });
  }
}
