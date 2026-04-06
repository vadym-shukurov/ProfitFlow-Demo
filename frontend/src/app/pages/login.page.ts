import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../core/services/auth.service';

/**
 * Login page — the public entry point to ProfitFlow.
 *
 * Submits credentials to `POST /api/v1/auth/login` and, on success, navigates
 * to the originally requested URL or the dashboard.
 *
 * The password field is never logged, stored in any state signal, or sent over
 * anything other than HTTPS (enforced by HSTS in production).
 */
@Component({
  selector: 'pf-login-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly username = signal('');
  readonly password = signal('');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  submit(): void {
    if (!this.username() || !this.password()) {
      this.errorMessage.set('Please enter your username and password.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.auth.login(this.username(), this.password()).subscribe({
      next: () => {
        this.loading.set(false);
        const returnUrl = this.sanitizeReturnUrl(
          this.route.snapshot.queryParams['returnUrl']
        );
        this.router.navigateByUrl(returnUrl);
      },
      error: () => {
        this.loading.set(false);
        // Generic message — don't reveal whether the user exists or account is locked
        this.errorMessage.set('Invalid username or password.');
        // Clear the password on failure to prevent re-submission of wrong credentials
        this.password.set('');
      },
    });
  }

  /**
   * Validates that `returnUrl` is a safe relative path within this application.
   *
   * Rejects:
   * - Absolute URLs (`https://evil.example`)
   * - Protocol-relative URLs (`//evil.example`)
   * - `javascript:` URIs
   * - Blank or non-string values
   *
   * Falls back to `/dashboard` for any rejected input, preventing open-redirect
   * phishing attacks where an attacker crafts a login link with a malicious target.
   */
  private sanitizeReturnUrl(raw: unknown): string {
    if (typeof raw !== 'string' || !raw) {
      return '/dashboard';
    }
    const trimmed = raw.trim();
    // Must start with '/' and must NOT start with '//' (protocol-relative)
    if (!trimmed.startsWith('/') || trimmed.startsWith('//')) {
      return '/dashboard';
    }
    // Reject javascript: and data: URIs that might survive the slash check
    const lower = trimmed.toLowerCase();
    if (lower.includes('javascript:') || lower.includes('data:')) {
      return '/dashboard';
    }
    return trimmed;
  }
}
