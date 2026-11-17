import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AlertStatus, AlertType, Inventory, InventoryStatus, Movement, MovementRequest, MovementType, PageResult,
  StockAlert, StockDashboard, StockLevel, StockStatus
} from '../models/models';
import { api, toParams } from './api';

export interface MovementFilters {
  productId?: number | null;
  type?: MovementType | null;
  from?: string | null;
  to?: string | null;
  user?: string | null;
  page?: number;
  size?: number;
}

/** Appels vers stock-service (mouvements, niveaux, alertes, inventaires). */
@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly http = inject(HttpClient);

  // --- mouvements
  recordMovement(body: MovementRequest): Observable<Movement> {
    return this.http.post<Movement>(api('/api/stock/movements'), body);
  }

  movements(filters: MovementFilters): Observable<PageResult<Movement>> {
    return this.http.get<PageResult<Movement>>(api('/api/stock/movements'), { params: toParams({ ...filters }) });
  }

  // --- niveaux
  levels(filters: { q?: string; status?: StockStatus | null; page?: number; size?: number }): Observable<PageResult<StockLevel>> {
    return this.http.get<PageResult<StockLevel>>(api('/api/stock/levels'), { params: toParams({ ...filters }) });
  }

  levelsBatch(productIds: number[]): Observable<StockLevel[]> {
    return this.http.get<StockLevel[]>(api('/api/stock/levels/batch'), { params: toParams({ productIds: productIds.join(',') }) });
  }

  level(productId: number): Observable<StockLevel> {
    return this.http.get<StockLevel>(api(`/api/stock/levels/${productId}`));
  }

  dashboard(): Observable<StockDashboard> {
    return this.http.get<StockDashboard>(api('/api/stock/dashboard'));
  }

  // --- alertes
  alerts(filters: { status?: AlertStatus | null; type?: AlertType | null; activeOnly?: boolean | null; page?: number; size?: number }): Observable<PageResult<StockAlert>> {
    return this.http.get<PageResult<StockAlert>>(api('/api/stock/alerts'), { params: toParams({ ...filters }) });
  }

  acknowledgeAlert(id: number): Observable<StockAlert> {
    return this.http.patch<StockAlert>(api(`/api/stock/alerts/${id}/acknowledge`), {});
  }

  recomputeAlerts(): Observable<{ evaluatedProducts: number }> {
    return this.http.post<{ evaluatedProducts: number }>(api('/api/stock/alerts/recompute'), {});
  }

  // --- inventaires
  inventories(filters: { status?: InventoryStatus | null; page?: number; size?: number }): Observable<PageResult<Inventory>> {
    return this.http.get<PageResult<Inventory>>(api('/api/stock/inventories'), { params: toParams({ ...filters }) });
  }

  inventory(id: number): Observable<Inventory> {
    return this.http.get<Inventory>(api(`/api/stock/inventories/${id}`));
  }

  openInventory(body: { name: string; notes: string | null }): Observable<Inventory> {
    return this.http.post<Inventory>(api('/api/stock/inventories'), body);
  }

  recordCount(id: number, productId: number, countedQuantity: number): Observable<Inventory> {
    return this.http.put<Inventory>(api(`/api/stock/inventories/${id}/counts`), { productId, countedQuantity });
  }

  removeCount(id: number, productId: number): Observable<Inventory> {
    return this.http.delete<Inventory>(api(`/api/stock/inventories/${id}/counts/${productId}`));
  }

  validateInventory(id: number): Observable<Inventory> {
    return this.http.post<Inventory>(api(`/api/stock/inventories/${id}/validate`), {});
  }

  cancelInventory(id: number): Observable<Inventory> {
    return this.http.post<Inventory>(api(`/api/stock/inventories/${id}/cancel`), {});
  }
}
