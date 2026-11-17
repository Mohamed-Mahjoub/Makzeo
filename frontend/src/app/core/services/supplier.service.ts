import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Country, PageResult, Supplier, SupplierRequest } from '../models/models';
import { api, toParams } from './api';

/** Appels vers supplier-service. */
@Injectable({ providedIn: 'root' })
export class SupplierService {
  private readonly http = inject(HttpClient);

  countries(): Observable<Country[]> {
    return this.http.get<Country[]>(api('/api/suppliers/countries'));
  }

  search(filters: { q?: string; active?: boolean | null; country?: string | null; page?: number; size?: number }): Observable<PageResult<Supplier>> {
    return this.http.get<PageResult<Supplier>>(api('/api/suppliers'), { params: toParams({ ...filters }) });
  }

  get(id: number): Observable<Supplier> {
    return this.http.get<Supplier>(api(`/api/suppliers/${id}`));
  }

  create(body: SupplierRequest): Observable<Supplier> {
    return this.http.post<Supplier>(api('/api/suppliers'), body);
  }

  update(id: number, body: SupplierRequest): Observable<Supplier> {
    return this.http.put<Supplier>(api(`/api/suppliers/${id}`), body);
  }

  setActive(id: number, active: boolean): Observable<Supplier> {
    return this.http.patch<Supplier>(api(`/api/suppliers/${id}/${active ? 'activate' : 'deactivate'}`), {});
  }
}
