import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { xsrfResponseHeaderInterceptor, XSRF_HEADER } from './xsrf.interceptor';
import { XsrfTokenStore } from './xsrf-token.store';

describe('xsrfResponseHeaderInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let store: XsrfTokenStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([xsrfResponseHeaderInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    store = TestBed.inject(XsrfTokenStore);
    store.clear();
  });

  afterEach(() => httpMock.verify());

  it('captures XSRF token from response headers', () => {
    http.get('/actuator/health', { observe: 'response' }).subscribe();

    const req = httpMock.expectOne('/actuator/health');
    req.flush(null, { headers: { [XSRF_HEADER]: 't1' } });

    expect(store.get()).toBe('t1');
  });

  it('attaches XSRF header on mutating requests when a token is present', () => {
    store.set('t2');

    http.post('/api/v1/auth/login', { u: 'a', p: 'b' }).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.headers.get(XSRF_HEADER)).toBe('t2');
    req.flush({});
  });

  it('does not attach XSRF header when no token is present', () => {
    http.post('/api/v1/auth/login', { u: 'a', p: 'b' }).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has(XSRF_HEADER)).toBeFalse();
    req.flush({});
  });

  it('does not attach XSRF header for GET requests', () => {
    store.set('t3');

    http.get('/api/v1/products').subscribe();

    const req = httpMock.expectOne('/api/v1/products');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has(XSRF_HEADER)).toBeFalse();
    req.flush([]);
  });
});

