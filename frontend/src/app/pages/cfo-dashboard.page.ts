import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject } from '@angular/core';
import type { EChartsOption } from 'echarts';

import type { SankeyLink, SankeyNode } from '../core/services/cfo-dashboard.store';

function buildSankeySeries(nodes: SankeyNode[], links: SankeyLink[]) {
  return {
    type: 'sankey',
    emphasis: { focus: 'adjacency' },
    data: nodes,
    links,
    lineStyle: { color: 'gradient', curveness: 0.5, opacity: 0.45 },
    itemStyle: { borderRadius: 4 },
    label: {
      position: 'right',
      fontSize: 12,
      color: '#334155',
      fontFamily: 'DM Sans, ui-sans-serif, system-ui, sans-serif',
    },
    nodeAlign: 'left',
    nodeWidth: 16,
    nodeGap: 18,
    top: '5%',
    bottom: '5%',
    left: '2%',
    right: '18%',
  };
}

import { EchartsDirective } from '../shared/echarts.directive';
import { CfoDashboardStore } from '../core/services/cfo-dashboard.store';

/**
 * CFO Dashboard page — the primary analytics view for executives.
 *
 * Displays a Sankey diagram of the full two-stage ABC cost flow
 * (Resources → Activities → Products), a product cost breakdown table,
 * and summary cards for key metrics. Allocation is triggered on demand
 * via the "Run Allocation" button, which calls `POST /api/v1/allocation/run`.
 */
@Component({
  selector: 'app-cfo-dashboard-page',
  standalone: true,
  imports: [DecimalPipe, EchartsDirective],
  templateUrl: './cfo-dashboard.page.html',
})
export class CfoDashboardPage implements OnInit {
  protected readonly store = inject(CfoDashboardStore);

  protected readonly sankeyOption = computed((): EChartsOption => {
    const { nodes, links } = this.store.sankeyData();
    return {
      tooltip: {
        trigger: 'item',
        formatter: (params: unknown) => {
          const p = params as {
            dataType: string;
            name: string;
            data: { source?: string; target?: string; value?: number };
            value: number;
          };
          if (p.dataType === 'edge') {
            return `<b>${p.data.source}</b> → <b>${p.data.target}</b><br/>
                    ${p.data.value?.toLocaleString(undefined, { minimumFractionDigits: 2 })}`;
          }
          return `<b>${p.name}</b>`;
        },
      },
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      series: [buildSankeySeries(nodes, links)] as any,
    };
  });

  ngOnInit(): void {
    this.store.loadCatalogs();
  }
}
