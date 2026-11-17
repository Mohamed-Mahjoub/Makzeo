import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Category, PageResult, Product, ProductRequest } from '../models/models';
import { api, toParams } from './api';

export interface ProductFilters {
  q?: string;
  categoryId?: number | null;
  supplierId?: number | null;
  active?: boolean | null;
  page?: number;
  size?: number;
}

/** Appels vers product-service (catalogue). */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>(api('/api/categories'));
  }

  createCategory(body: { name: string; description: string | null }): Observable<Category> {
    return this.http.post<Category>(api('/api/categories'), body);
  }

  updateCategory(id: number, body: { name: string; description: string | null }): Observable<Category> {
    return this.http.put<Category>(api(`/api/categories/${id}`), body);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(api(`/api/categories/${id}`));
  }

  products(filters: ProductFilters): Observable<PageResult<Product>> {
    return this.http.get<PageResult<Product>>(api('/api/products'), { params: toParams({ ...filters }) });
  }

  product(id: number): Observable<Product> {
    return this.http.get<Product>(api(`/api/products/${id}`));
  }

  productByBarcode(barcode: string): Observable<Product> {
    return this.http.get<Product>(api(`/api/products/barcode/${encodeURIComponent(barcode.trim())}`));
  }

  productByReference(reference: string): Observable<Product> {
    return this.http.get<Product>(api(`/api/products/reference/${encodeURIComponent(reference.trim())}`));
  }

  createProduct(body: ProductRequest): Observable<Product> {
    return this.http.post<Product>(api('/api/products'), body);
  }

  updateProduct(id: number, body: ProductRequest): Observable<Product> {
    return this.http.put<Product>(api(`/api/products/${id}`), body);
  }

  setProductActive(id: number, active: boolean): Observable<Product> {
    return this.http.patch<Product>(api(`/api/products/${id}/${active ? 'activate' : 'deactivate'}`), {});
  }
}
