/** Modèles du module IA achats (procurement-service). */

export type MarketSupplierStatus = 'CANDIDATE' | 'VALIDATED' | 'REJECTED';
export type SupplierSource = 'CATALOG' | 'WEB_WATCH' | 'MANUAL';
export type RfqStatus = 'DRAFT' | 'APPROVED' | 'SENT' | 'ANSWERED' | 'FAILED' | 'CANCELLED';
export type Equivalence = 'IDENTICAL' | 'EQUIVALENT';
export type Availability = 'IN_STOCK' | 'ON_ORDER' | 'UNAVAILABLE';
export type QuoteSource = 'RFQ_REPLY' | 'MANUAL' | 'WEB' | 'AI_EXTRACTED';
export type PriceSource = 'PURCHASE' | 'QUOTE' | 'MARKET' | 'CATALOG';
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';
export type RecommendationType = 'ANTICIPATED_PURCHASE' | 'SWITCH_SUPPLIER';
export type RecommendationStatus = 'OPEN' | 'ACCEPTED' | 'DISMISSED' | 'EXPIRED';
export type WatchRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export const SUPPLIER_STATUS_LABELS: Record<MarketSupplierStatus, string> = {
  CANDIDATE: 'À valider', VALIDATED: 'Validé', REJECTED: 'Écarté'
};
export const SUPPLIER_SOURCE_LABELS: Record<SupplierSource, string> = {
  CATALOG: 'Catalogue', WEB_WATCH: 'Veille', MANUAL: 'Saisi'
};
export const RFQ_STATUS_LABELS: Record<RfqStatus, string> = {
  DRAFT: 'Brouillon', APPROVED: 'Validée', SENT: 'Envoyée', ANSWERED: 'Répondue', FAILED: 'Échec d\'envoi',
  CANCELLED: 'Annulée'
};
export const EQUIVALENCE_LABELS: Record<Equivalence, string> = { IDENTICAL: 'Identique', EQUIVALENT: 'Équivalent' };
export const AVAILABILITY_LABELS: Record<Availability, string> = {
  IN_STOCK: 'En stock', ON_ORDER: 'Sur commande', UNAVAILABLE: 'Indisponible'
};
export const QUOTE_SOURCE_LABELS: Record<QuoteSource, string> = {
  RFQ_REPLY: 'Réponse RFQ', MANUAL: 'Saisie', WEB: 'Prix publié', AI_EXTRACTED: 'Lu par l\'IA'
};
export const PRICE_SOURCE_LABELS: Record<PriceSource, string> = {
  PURCHASE: 'Achat', QUOTE: 'Devis', MARKET: 'Marché', CATALOG: 'Fiche produit'
};
export const CONFIDENCE_LABELS: Record<ConfidenceLevel, string> = { HIGH: 'Élevée', MEDIUM: 'Moyenne', LOW: 'Faible' };
export const RECO_TYPE_LABELS: Record<RecommendationType, string> = {
  ANTICIPATED_PURCHASE: 'Achat anticipé', SWITCH_SUPPLIER: 'Changer de fournisseur'
};
export const RECO_STATUS_LABELS: Record<RecommendationStatus, string> = {
  OPEN: 'À décider', ACCEPTED: 'Acceptée', DISMISSED: 'Écartée', EXPIRED: 'Remplacée'
};

export interface MarketSupplier {
  id: number;
  name: string;
  website: string | null;
  email: string | null;
  phone: string | null;
  city: string | null;
  source: SupplierSource;
  status: MarketSupplierStatus;
  catalogSupplierId: number | null;
  notes: string | null;
  discoveredAt: string;
  canReceiveRfq: boolean;
}

export interface MarketSupplierRequest {
  name: string;
  website: string | null;
  email: string | null;
  phone: string | null;
  city: string | null;
  notes: string | null;
}

export interface SourcingLead {
  id: number;
  productId: number;
  marketSupplierId: number;
  supplierName: string;
  offeredName: string;
  offeredReference: string | null;
  url: string | null;
  publishedPrice: number | null;
  currency: string | null;
  equivalence: Equivalence;
  similarity: number;
  rationale: string | null;
  detectedAt: string;
}

export interface Rfq {
  id: number;
  reference: string;
  productId: number;
  productReference: string;
  productName: string;
  marketSupplierId: number;
  supplierName: string;
  supplierEmail: string | null;
  quantity: number;
  subject: string;
  body: string;
  status: RfqStatus;
  responseDueDate: string | null;
  createdBy: string;
  createdAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
  sentAt: string | null;
  failureReason: string | null;
}

export interface Quote {
  id: number;
  rfqId: number | null;
  productId: number;
  marketSupplierId: number;
  supplierName: string;
  offeredName: string;
  equivalence: Equivalence;
  unitPrice: number;
  currency: string;
  minQuantity: number;
  availability: Availability;
  leadTimeDays: number | null;
  paymentTerms: string | null;
  validUntil: string | null;
  qualityScore: number;
  source: QuoteSource;
  receivedAt: string;
}

export interface QuoteRequest {
  rfqId: number | null;
  productId: number;
  marketSupplierId: number | null;
  offeredName: string;
  equivalence: Equivalence;
  unitPrice: number | null;
  priceIncludesVat: boolean;
  minQuantity: number | null;
  availability: Availability;
  leadTimeDays: number | null;
  paymentTerms: string | null;
  validUntil: string | null;
  qualityScore: number;
  source: QuoteSource | null;
}

export interface ExtractedQuote {
  supplierName: string | null;
  offeredName: string | null;
  unitPrice: number;
  currency: string;
  priceIncludesVat: boolean;
  minQuantity: number | null;
  availability: Availability;
  leadTimeDays: number | null;
  paymentTerms: string | null;
  validUntil: string | null;
  notes: string | null;
}

export interface ComparedOffer {
  rank: number;
  eligible: boolean;
  exclusionReason: string | null;
  recommended: boolean;
  quoteId: number;
  marketSupplierId: number;
  supplierName: string;
  offeredName: string;
  equivalence: Equivalence;
  unitPrice: number;
  minQuantity: number;
  orderQuantity: number;
  totalCost: number;
  effectiveUnitCost: number;
  leadTimeDays: number | null;
  availability: Availability;
  qualityScore: number;
  validUntil: string | null;
  source: QuoteSource;
  score: number;
  unitSaving: number | null;
  savingPct: number | null;
  annualSaving: number | null;
}

export interface Comparison {
  productId: number;
  productReference: string;
  productName: string;
  quantity: number;
  referencePrice: number | null;
  currency: string;
  monthlyConsumption: number;
  offers: ComparedOffer[];
}

export interface QuotedProduct {
  productId: number;
  reference: string;
  name: string;
  offers: number;
  bestUnitPrice: number | null;
  referencePrice: number | null;
  lastOfferAt: string | null;
}

export interface PricePoint {
  id: number;
  productId: number;
  unitPrice: number;
  observedOn: string;
  source: PriceSource;
  supplierName: string | null;
  quantity: number | null;
}

export interface PricePrediction {
  id: number;
  productId: number;
  productReference: string;
  productName: string;
  generatedAt: string;
  currentPrice: number;
  predictedPrice: number;
  changePct: number;
  horizonDays: number;
  confidence: number;
  confidenceLevel: ConfidenceLevel;
  monthlyTrendPct: number;
  seasonalityPct: number;
  inflationPct: number;
  marketSignalPct: number | null;
  sampleSize: number;
  rationale: string;
}

export interface PriceHistory {
  productId: number;
  points: PricePoint[];
  prediction: PricePrediction | null;
}

export interface ImportResult {
  imported: number;
  rejected: string[];
}

export interface Recommendation {
  id: number;
  type: RecommendationType;
  status: RecommendationStatus;
  productId: number;
  productReference: string;
  productName: string;
  supplierName: string | null;
  quoteId: number | null;
  quantity: number;
  referenceUnitPrice: number;
  recommendedUnitPrice: number;
  expectedFuturePrice: number | null;
  costNow: number;
  costLater: number;
  holdingCost: number;
  netSavings: number;
  buyBefore: string | null;
  confidence: number | null;
  rationale: string;
  createdAt: string;
  decidedBy: string | null;
  decidedAt: string | null;
}

export interface WatchRun {
  id: number;
  period: string;
  trigger: 'SCHEDULED' | 'MANUAL';
  status: WatchRunStatus;
  startedBy: string;
  startedAt: string;
  finishedAt: string | null;
  productsScanned: number;
  suppliersDiscovered: number;
  leadsFound: number;
  rfqsDrafted: number;
  recommendations: number;
  message: string | null;
}

export interface ProcurementOverview {
  aiEnabled: boolean;
  /** Moteur de la veille web : « API Claude », « Serper + Tavily », « Serper » ; null si non configurée. */
  aiProvider: string | null;
  mailEnabled: boolean;
  currency: string;
  marketName: string;
  lastRun: WatchRun | null;
  candidateSuppliers: number;
  validatedSuppliers: number;
  draftRfqs: number;
  sentRfqs: number;
  openOpportunities: number;
  potentialSavings: number;
  realizedSavings: number;
  risingPrices: number;
  fallingPrices: number;
}

export interface SavingsLine {
  key: string;
  label: string;
  realized: number;
  potential: number;
  count: number;
}

export interface SavingsSummary {
  totalRealized: number;
  totalPotential: number;
  anticipatedPurchaseGains: number;
  supplierSwitchGains: number;
  byProduct: SavingsLine[];
  bySupplier: SavingsLine[];
  byMonth: SavingsLine[];
}
