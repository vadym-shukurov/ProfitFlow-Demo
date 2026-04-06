import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';

import { CostLedgerStore } from '../core/services/cost-ledger.store';

/**
 * Cost Ledger page — displays all resource cost entries and allows
 * Finance Managers to create new entries individually or bulk-import
 * them from a CSV file.
 *
 * CSV format: `label,amount,currency` (header row required).
 * Leading formula-injection characters are sanitised on the backend.
 */
@Component({
  selector: 'app-cost-ledger-page',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './cost-ledger.page.html',
})
export class CostLedgerPage implements OnInit {
  protected readonly ledger = inject(CostLedgerStore);

  protected readonly draftLabel = signal('');
  protected readonly draftAmount = signal('');
  protected readonly draftCurrency = signal('USD');
  protected readonly csvDraft = signal('');

  ngOnInit(): void {
    this.ledger.load();
  }

  protected submitNewCost(): void {
    const label = this.draftLabel().trim();
    const amount = Number(this.draftAmount());
    if (!label) {
      this.ledger.error.set('Label is required.');
      return;
    }
    if (!Number.isFinite(amount) || amount < 0) {
      this.ledger.error.set('Amount must be a non-negative number.');
      return;
    }
    this.ledger.error.set(null);
    this.ledger.create(label, amount, this.draftCurrency(), () => {
      this.draftLabel.set('');
      this.draftAmount.set('');
      this.draftCurrency.set('USD');
    });
  }

  protected importCsv(): void {
    this.ledger.error.set(null);
    this.ledger.importCsv(this.csvDraft(), () => this.csvDraft.set(''));
  }
}
