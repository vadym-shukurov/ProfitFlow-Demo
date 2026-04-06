/**
 * A single resource cost entry — a GL account or cost pool that feeds
 * into the first stage of the ABC allocation engine.
 */
export interface ResourceCostDto {
  id: string;
  label: string;
  amount: number;
  currencyCode: string;
}

/** A named activity cost pool (stage 1 → stage 2 intermediate node). */
export interface ActivityDto {
  id: string;
  name: string;
}

/** A named product or service (final cost object in the ABC model). */
export interface ProductDto {
  id: string;
  name: string;
}

/**
 * A single Resource → Activity allocation rule row.
 *
 * `driverWeight` is a relative weight; the engine normalises all weights
 * for a given resource to sum to 1.0 before allocating.
 */
export interface ResourceActivityRuleDto {
  resourceId: string;
  activityId: string;
  driverWeight: number;
}

/**
 * A single Activity → Product allocation rule row.
 *
 * `driverWeight` is a relative weight; the engine normalises all weights
 * for a given activity to sum to 1.0 before allocating.
 */
export interface ActivityProductRuleDto {
  activityId: string;
  productId: string;
  driverWeight: number;
}

/** Request body for the AI allocation suggestion endpoint. */
export interface AiSuggestRequest {
  text: string;
}

/** AI-generated suggestion returned by `POST /api/v1/ai/suggest`. */
export interface AiSuggestionDto {
  suggestedActivityName: string;
  suggestedAllocationDriver: string;
}

/**
 * A single directed cost-flow edge used to render the CFO Sankey diagram.
 *
 * Flows run in two stages: RESOURCE → ACTIVITY (stage 1) and
 * ACTIVITY → PRODUCT (stage 2).
 */
export interface AllocationFlowDto {
  fromKind: 'RESOURCE' | 'ACTIVITY' | 'PRODUCT';
  fromId: string;
  toKind: 'RESOURCE' | 'ACTIVITY' | 'PRODUCT';
  toId: string;
  amount: number;
  currencyCode: string;
}

/** Complete result of an ABC allocation run. */
export interface AllocationRunResultDto {
  /** Total allocated cost per activity ID. */
  activityCosts: Record<string, number>;
  /** Total allocated cost per product ID. */
  productCosts: Record<string, number>;
  /** All cost-flow edges for Sankey visualisation. */
  flows: AllocationFlowDto[];
  /** IDs of resources that had no matching allocation rule and were skipped. */
  unallocatedResourceIds: string[];
}

/** Standard error body returned by the ProfitFlow API on 4xx/5xx responses. */
export interface ApiErrorDto {
  message?: string;
}
