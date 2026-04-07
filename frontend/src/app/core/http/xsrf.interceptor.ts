import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { XsrfTokenStore } from './xsrf-token.store';

/** Must match {@link com.profitflow.security.CsrfTokenResponseHeaderFilter#CSRF_TOKEN_HEADER}. */
export const XSRF_HEADER = 'X-XSRF-TOKEN';

const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/**
 * Captures CSRF tokens from API responses and attaches {@value XSRF_HEADER} on mutating
 * requests (double-submit; cookie is HttpOnly).
 */
export const xsrfResponseHeaderInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(XsrfTokenStore);
  let outbound = req;
  if (MUTATING.has(req.method) && store.get()) {
    outbound = req.clone({ setHeaders: { [XSRF_HEADER]: store.get()! } });
  }
  return next(outbound).pipe(
    tap(event => {
      if (event instanceof HttpResponse) {
        const h = event.headers.get(XSRF_HEADER);
        if (h) {
          store.set(h);
        }
      }
    }),
  );
};
