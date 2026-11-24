import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PageResult } from '../models/models';
import {
  Comparison, ExtractedQuote, ImportResult, MarketSupplier, MarketSupplierRequest, MarketSupplierStatus,
  PriceHistory, PricePoint, PricePrediction, ProcurementOverview, Quote, QuoteRequest, QuotedProduct, Recommendation,
  RecommendationStatus, RecommendationType, Rfq, RfqStatus, SavingsSummary, SourcingLead, WatchRun
} from '../models/procurement.models';
import { api, toParams } from './api';

/** Appels vers procurement-service (module IA achats). */
@Injectable({ providedIn: 'root' })
export class ProcurementService {
  private readonly http = inject(HttpClient);

  // ---- synthèse et veille
  overview(): Observable<ProcurementOverview> {
    return this.http.get<ProcurementOverview>(api('/api/procurement/overview'));
  }

  savings(): Observable<SavingsSummary> {
    return this.http.get<SavingsSummary>(api('/api/procurement/savings'));
  }

  runWatch(): Observable<WatchRun> {
    return this.http.post<WatchRun>(api('/api/procurement/watch/run'), {});
  }

  watchRuns(page = 0, size = 10): Observable<PageResult<WatchRun>> {
    return this.http.get<PageResult<WatchRun>>(api('/api/procurement/watch/runs'), { params: toParams({ page, size }) });
  }

  leads(productId?: number | null): Observable<SourcingLead[]> {
    return this.http.get<SourcingLead[]>(api('/api/procurement/leads'), { params: toParams({ productId }) });
  }

  /** Articles disposant d'offres (navigation de la page de comparaison). */
  quotedProducts(): Observable<QuotedProduct[]> {
    return this.http.get<QuotedProduct[]>(api('/api/procurement/quotes/products'));
  }

  // ---- fournisseurs du marché
  suppliers(status: MarketSupplierStatus | null, page = 0, size = 20): Observable<PageResult<MarketSupplier>> {
    return this.http.get<PageResult<MarketSupplier>>(api('/api/procurement/suppliers'),
      { params: toParams({ status, page, size }) });
  }

  createSupplier(body: MarketSupplierRequest): Observable<MarketSupplier> {
    return this.http.post<MarketSupplier>(api('/api/procurement/suppliers'), body);
  }

  updateSupplier(id: number, body: MarketSupplierRequest): Observable<MarketSupplier> {
    return this.http.put<MarketSupplier>(api(`/api/procurement/suppliers/${id}`), body);
  }

  validateSupplier(id: number): Observable<MarketSupplier> {
    return this.http.patch<MarketSupplier>(api(`/api/procurement/suppliers/${id}/validate`), {});
  }

  rejectSupplier(id: number): Observable<MarketSupplier> {
    return this.http.patch<MarketSupplier>(api(`/api/procurement/suppliers/${id}/reject`), {});
  }

  // ---- demandes de prix
  rfqs(filters: { status?: RfqStatus | null; productId?: number | null; page?: number; size?: number }): Observable<PageResult<Rfq>> {
    return this.http.get<PageResult<Rfq>>(api('/api/procurement/rfqs'), { params: toParams({ ...filters }) });
  }

  draftRfq(productId: number, marketSupplierId: number, quantity: number): Observable<Rfq> {
    return this.http.post<Rfq>(api('/api/procurement/rfqs'), { productId, marketSupplierId, quantity });
  }

  updateRfq(id: number, body: { quantity: number; subject: string; body: string; responseDueDate: string | null }): Observable<Rfq> {
    return this.http.put<Rfq>(api(`/api/procurement/rfqs/${id}`), body);
  }

  approveRfq(id: number): Observable<Rfq> {
    return this.http.post<Rfq>(api(`/api/procurement/rfqs/${id}/approve`), {});
  }

  approveRfqs(ids: number[]): Observable<Rfq[]> {
    return this.http.post<Rfq[]>(api('/api/procurement/rfqs/approve'), { ids });
  }

  cancelRfq(id: number): Observable<Rfq> {
    return this.http.post<Rfq>(api(`/api/procurement/rfqs/${id}/cancel`), {});
  }

  // ---- devis et comparaison
  quotes(productId: number): Observable<Quote[]> {
    return this.http.get<Quote[]>(api('/api/procurement/quotes'), { params: toParams({ productId }) });
  }

  registerQuote(body: QuoteRequest): Observable<Quote> {
    return this.http.post<Quote>(api('/api/procurement/quotes'), body);
  }

  extractQuote(productId: number, text: string): Observable<ExtractedQuote> {
    return this.http.post<ExtractedQuote>(api('/api/procurement/quotes/extract'), { productId, text });
  }

  comparison(productId: number, quantity?: number | null): Observable<Comparison> {
    return this.http.get<Comparison>(api('/api/procurement/quotes/comparison'), { params: toParams({ productId, quantity }) });
  }

  // ---- prix et prédictions
  predictions(): Observable<PricePrediction[]> {
    return this.http.get<PricePrediction[]>(api('/api/procurement/prices/predictions'));
  }

  priceHistory(productId: number): Observable<PriceHistory> {
    return this.http.get<PriceHistory>(api(`/api/procurement/prices/${productId}`));
  }

  recordPurchase(body: { productId: number; unitPrice: number; date: string; supplierName: string | null; quantity: number | null }): Observable<PricePoint> {
    return this.http.post<PricePoint>(api('/api/procurement/prices/purchases'), body);
  }

  importPurchases(csv: string): Observable<ImportResult> {
    return this.http.post<ImportResult>(api('/api/procurement/prices/purchases/import'), { csv });
  }

  // ---- recommandations
  recommendations(filters: { status?: RecommendationStatus | null; type?: RecommendationType | null; page?: number; size?: number }): Observable<PageResult<Recommendation>> {
    return this.http.get<PageResult<Recommendation>>(api('/api/procurement/recommendations'), { params: toParams({ ...filters }) });
  }

  refreshRecommendations(): Observable<{ created: number; notified: number }> {
    return this.http.post<{ created: number; notified: number }>(api('/api/procurement/recommendations/refresh'), {});
  }

  acceptRecommendation(id: number): Observable<Recommendation> {
    return this.http.patch<Recommendation>(api(`/api/procurement/recommendations/${id}/accept`), {});
  }

  dismissRecommendation(id: number): Observable<Recommendation> {
    return this.http.patch<Recommendation>(api(`/api/procurement/recommendations/${id}/dismiss`), {});
  }
}
