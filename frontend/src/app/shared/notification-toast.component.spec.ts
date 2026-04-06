import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NotificationToastComponent } from './notification-toast.component';
import { NotificationService } from '../core/services/notification.service';

describe('NotificationToastComponent', () => {
  let fixture: ComponentFixture<NotificationToastComponent>;
  let notify: NotificationService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationToastComponent],
      providers: [NotificationService],
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationToastComponent);
    notify = TestBed.inject(NotificationService);
    fixture.detectChanges();
  });

  it('renders a success toast with icon', () => {
    notify.success('Saved.');
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Saved.');
    expect(root.textContent).toContain('✓');
  });

  it('dismiss removes the toast', () => {
    notify.error('Something failed');
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector(
      'button[aria-label^="Dismiss"]',
    ) as HTMLButtonElement;
    expect(btn).toBeTruthy();
    btn.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Something failed');
  });
});
