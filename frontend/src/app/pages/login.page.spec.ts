import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute, Router } from '@angular/router';

import { LoginPage } from './login.page';
import { XsrfTokenStore } from '../core/http/xsrf-token.store';

function flushCsrfPriming(httpMock: HttpTestingController): void {
  const r = httpMock.expectOne('/actuator/health');
  expect(r.request.method).toBe('GET');
  r.flush(null, { headers: { 'X-XSRF-TOKEN': 'xsrf-test' } });
}

describe('LoginPage', () => {
  describe('default route', () => {
    let fixture: ComponentFixture<LoginPage>;
    let httpMock: HttpTestingController;
    let router: Router;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [LoginPage, HttpClientTestingModule, RouterTestingModule],
        providers: [
          { provide: ActivatedRoute, useValue: { snapshot: { queryParams: {} } } },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(LoginPage);
      httpMock = TestBed.inject(HttpTestingController);
      router = TestBed.inject(Router);
      TestBed.inject(XsrfTokenStore).clear();
      spyOn(router, 'navigateByUrl');
    });

    afterEach(() => httpMock.verify());

    it('shows validation when username or password is empty', () => {
      const cmp = fixture.componentInstance;
      cmp.username.set('');
      cmp.password.set('');
      cmp.submit();
      expect(cmp.errorMessage()).toContain('Please enter');
    });

    it('navigates to /dashboard on successful login', () => {
      const cmp = fixture.componentInstance;
      cmp.username.set('admin');
      cmp.password.set('Admin1234!');
      cmp.submit();

      flushCsrfPriming(httpMock);
      httpMock.expectOne('/api/v1/auth/login').flush({
        accessToken: 'a',
        refreshToken: 'r',
        tokenType: 'Bearer',
        expiresIn: 3600,
        username: 'admin',
        roles: 'ROLE_ADMIN',
      });

      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    });

    it('sets generic error and clears password on 401', () => {
      const cmp = fixture.componentInstance;
      cmp.username.set('admin');
      cmp.password.set('bad');
      cmp.submit();

      flushCsrfPriming(httpMock);
      httpMock.expectOne('/api/v1/auth/login').flush(
        {},
        { status: 401, statusText: 'Unauthorized' },
      );

      expect(cmp.errorMessage()).toContain('Invalid');
      expect(cmp.password()).toBe('');
    });
  });

  describe('returnUrl — safe relative path', () => {
    let fixture: ComponentFixture<LoginPage>;
    let httpMock: HttpTestingController;
    let router: Router;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [LoginPage, HttpClientTestingModule, RouterTestingModule],
        providers: [
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParams: { returnUrl: '/allocation-rules' } } },
          },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(LoginPage);
      httpMock = TestBed.inject(HttpTestingController);
      router = TestBed.inject(Router);
      TestBed.inject(XsrfTokenStore).clear();
      spyOn(router, 'navigateByUrl');
    });

    afterEach(() => httpMock.verify());

    it('navigates to returnUrl when safe', () => {
      const cmp = fixture.componentInstance;
      cmp.username.set('admin');
      cmp.password.set('Admin1234!');
      cmp.submit();

      flushCsrfPriming(httpMock);
      httpMock.expectOne('/api/v1/auth/login').flush({
        accessToken: 'a',
        refreshToken: 'r',
        tokenType: 'Bearer',
        expiresIn: 3600,
        username: 'admin',
        roles: 'ROLE_ADMIN',
      });

      expect(router.navigateByUrl).toHaveBeenCalledWith('/allocation-rules');
    });
  });

  describe('returnUrl — unsafe protocol-relative', () => {
    let fixture: ComponentFixture<LoginPage>;
    let httpMock: HttpTestingController;
    let router: Router;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [LoginPage, HttpClientTestingModule, RouterTestingModule],
        providers: [
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParams: { returnUrl: '//evil.example/phish' } } },
          },
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(LoginPage);
      httpMock = TestBed.inject(HttpTestingController);
      router = TestBed.inject(Router);
      TestBed.inject(XsrfTokenStore).clear();
      spyOn(router, 'navigateByUrl');
    });

    afterEach(() => httpMock.verify());

    it('falls back to /dashboard', () => {
      const cmp = fixture.componentInstance;
      cmp.username.set('admin');
      cmp.password.set('Admin1234!');
      cmp.submit();

      flushCsrfPriming(httpMock);
      httpMock.expectOne('/api/v1/auth/login').flush({
        accessToken: 'a',
        refreshToken: 'r',
        tokenType: 'Bearer',
        expiresIn: 3600,
        username: 'admin',
        roles: 'ROLE_ADMIN',
      });

      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    });
  });
});
