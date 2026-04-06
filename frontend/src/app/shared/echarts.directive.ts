import {
  Directive,
  ElementRef,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  OnInit,
  inject,
} from '@angular/core';
import type { ECharts, EChartsOption } from 'echarts';

/**
 * Lightweight ECharts host directive.
 *
 * Attach to any `<div>` to turn it into a chart container:
 * ```html
 * <div [appEcharts]="myOption" class="h-96 w-full"></div>
 * ```
 *
 * ### Design choices
 * - **Lazy import**: ECharts is loaded on first `ngOnInit` (not at bundle
 *   parse time), keeping the initial JS bundle small.
 * - **Outside Angular zone**: `echarts.init` and `resize` run outside
 *   Angular's change-detection zone to avoid unnecessary CD cycles on every
 *   animation frame.
 * - **ResizeObserver cleanup**: The observer is explicitly disconnected in
 *   `ngOnDestroy` to prevent memory leaks when the host element is removed.
 * - **`notMerge: true`**: Each option update fully replaces the previous one
 *   instead of deep-merging, which is safer for computed signals that rebuild
 *   the entire option object on every emission.
 */
@Directive({ selector: '[appEcharts]', standalone: true })
export class EchartsDirective implements OnInit, OnChanges, OnDestroy {
  /** The ECharts option object to render. Pass `null` to render nothing. */
  @Input('appEcharts') option: EChartsOption | null = null;

  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly zone = inject(NgZone);

  private chart: ECharts | null = null;
  /** Kept so we can call `disconnect()` in `ngOnDestroy`. */
  private resizeObserver: ResizeObserver | null = null;

  ngOnInit(): void {
    this.zone.runOutsideAngular(() => {
      import('echarts').then((echarts) => {
        this.chart = echarts.init(this.el.nativeElement, undefined, { renderer: 'canvas' });
        this.applyOption();

        this.resizeObserver = new ResizeObserver(() => this.chart?.resize());
        this.resizeObserver.observe(this.el.nativeElement);
      });
    });
  }

  ngOnChanges(): void {
    this.applyOption();
  }

  ngOnDestroy(): void {
    // Disconnect the observer before disposing the chart to avoid a potential
    // resize callback firing on an already-disposed instance.
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;

    this.chart?.dispose();
    this.chart = null;
  }

  private applyOption(): void {
    if (!this.chart || !this.option) {
      return;
    }
    this.chart.setOption(this.option, { notMerge: true });
  }
}
