/** Modèles TypeScript alignés sur les réponses JSON des microservices. */

export type Role = 'ADMIN' | 'GESTIONNAIRE' | 'MAGASINIER' | 'CONSULTANT';

export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  violations: { field: string; message: string }[];
}

// ---------- auth-service
export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  roles: Role[];
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AuthResult {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface UserRequest {
  username?: string;
  email: string;
  fullName: string;
  password?: string;
  roles: Role[];
}

// ---------- product-service
export type UnitOfMeasure = 'PIECE' | 'KG' | 'LITRE' | 'METRE' | 'CARTON' | 'BOITE' | 'PAQUET';

export interface Category {
  id: number;
  name: string;
  description: string | null;
}

export interface Product {
  id: number;
  reference: string;
  name: string;
  description: string | null;
  categoryId: number;
  categoryName: string | null;
  barcode: string;
  unit: UnitOfMeasure;
  purchasePrice: number;
  salePrice: number;
  minStock: number;
  maxStock: number | null;
  supplierId: number | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  reference: string;
  name: string;
  description: string | null;
  categoryId: number | null;
  barcode: string | null;
  unit: UnitOfMeasure;
  purchasePrice: number | null;
  salePrice: number | null;
  minStock: number;
  maxStock: number | null;
  supplierId: number | null;
}

// ---------- supplier-service
export interface Supplier {
  id: number;
  code: string;
  name: string;
  contactName: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  city: string | null;
  /** Code pays ISO 3166-1 alpha-2 (MA, TN, FR...) */
  country: string | null;
  countryName: string | null;
  /** Identifiant fiscal : ICE (Maroc), matricule fiscal (Tunisie), n° TVA... */
  taxId: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export type SupplierRequest = Omit<Supplier, 'id' | 'active' | 'createdAt' | 'updatedAt' | 'countryName'>;

export interface Country {
  code: string;
  name: string;
}

// ---------- paramètres (auth-service)
export interface Settings {
  currency: string;
  currencyName: string;
  currencySymbol: string;
  currencyFractionDigits: number;
  updatedAt: string;
  updatedBy: string | null;
}

export interface CurrencyOption {
  code: string;
  name: string;
  symbol: string;
  fractionDigits: number;
  common: boolean;
}

// ---------- stock-service
export type MovementType = 'IN' | 'OUT' | 'ADJUSTMENT';
export type StockStatus = 'OK' | 'LOW' | 'OUT_OF_STOCK';
export type AlertType = 'RUPTURE' | 'SEUIL_MIN';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type InventoryStatus = 'IN_PROGRESS' | 'VALIDATED' | 'CANCELLED';

export interface Movement {
  id: number;
  productId: number;
  productReference: string;
  productName: string;
  type: MovementType;
  typeLabel: string;
  quantity: number;
  signedQuantity: number;
  quantityBefore: number;
  quantityAfter: number;
  supplierId: number | null;
  documentReference: string | null;
  reason: string | null;
  inventoryId: number | null;
  performedBy: string;
  occurredAt: string;
}

export interface MovementRequest {
  productId: number;
  type: 'IN' | 'OUT';
  quantity: number;
  supplierId: number | null;
  documentReference: string | null;
  reason: string | null;
}

export interface StockLevel {
  productId: number;
  productReference: string;
  productName: string;
  quantity: number;
  minStock: number;
  status: StockStatus;
  lastMovementAt: string | null;
}

export interface StockAlert {
  id: number;
  productId: number;
  productReference: string;
  productName: string;
  type: AlertType;
  typeLabel: string;
  status: AlertStatus;
  quantity: number;
  threshold: number;
  message: string;
  createdAt: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
}

export interface InventoryLine {
  productId: number;
  productReference: string;
  productName: string;
  expectedQuantity: number;
  countedQuantity: number;
  difference: number;
  countedBy: string | null;
  countedAt: string | null;
}

export interface Inventory {
  id: number;
  name: string;
  notes: string | null;
  status: InventoryStatus;
  createdBy: string;
  createdAt: string;
  closedBy: string | null;
  closedAt: string | null;
  lineCount: number;
  discrepancyCount: number;
  lines: InventoryLine[];
}

export interface StockDashboard {
  trackedProducts: number;
  totalQuantity: number;
  outOfStockProducts: number;
  lowStockProducts: number;
  activeRuptureAlerts: number;
  activeThresholdAlerts: number;
  entriesToday: number;
  exitsToday: number;
  adjustmentsToday: number;
}

// ---------- reporting-service (analytics)
export type ReplenishmentPriority = 'CRITIQUE' | 'HAUTE' | 'MOYENNE' | 'NORMALE';

export interface Analytics {
  generatedAt: string;
  periodDays: number;
  currency: string;
  currencyFractionDigits: number;
  from: string;
  to: string;
  kpis: {
    productCount: number;
    totalQuantity: number;
    stockValue: number;
    outOfStock: number;
    lowStock: number;
    activeAlerts: number;
    entriesQuantity: number;
    exitsQuantity: number;
    movementCount: number;
    rotationRate: number;
    coverageDays: number | null;
    lostSalesPerDay: number;
    productsToReorder: number;
    reorderValue: number;
  };
  dailyFlows: { date: string; entries: number; exits: number; adjustments: number }[];
  statusDistribution: { status: StockStatus; label: string; count: number }[];
  valueByCategory: { category: string; productCount: number; quantity: number; value: number }[];
  topValue: {
    productId: number; reference: string; name: string; category: string; quantity: number;
    unitPrice: number; value: number;
  }[];
  topConsumed: { productId: number; reference: string; name: string; exits: number; avgDailyExits: number }[];
  replenishment: Replenishment[];
}

export interface Replenishment {
  productId: number;
  reference: string;
  name: string;
  category: string;
  quantity: number;
  minStock: number;
  maxStock: number | null;
  avgDailyExits: number;
  coverageDays: number | null;
  estimatedRuptureDate: string | null;
  suggestedQuantity: number;
  suggestedValue: number;
  priority: ReplenishmentPriority;
}

// ---------- libellés d'affichage
export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  GESTIONNAIRE: 'Gestionnaire',
  MAGASINIER: 'Magasinier',
  CONSULTANT: 'Consultant'
};

export const UNIT_LABELS: Record<UnitOfMeasure, string> = {
  PIECE: 'Pièce',
  KG: 'Kg',
  LITRE: 'Litre',
  METRE: 'Mètre',
  CARTON: 'Carton',
  BOITE: 'Boîte',
  PAQUET: 'Paquet'
};

export const STOCK_STATUS_LABELS: Record<StockStatus, string> = {
  OK: 'Normal',
  LOW: 'Sous le seuil',
  OUT_OF_STOCK: 'Rupture'
};

export const ALERT_STATUS_LABELS: Record<AlertStatus, string> = {
  OPEN: 'Ouverte',
  ACKNOWLEDGED: 'Prise en compte',
  RESOLVED: 'Résolue'
};

export const INVENTORY_STATUS_LABELS: Record<InventoryStatus, string> = {
  IN_PROGRESS: 'En cours',
  VALIDATED: 'Validé',
  CANCELLED: 'Annulé'
};
