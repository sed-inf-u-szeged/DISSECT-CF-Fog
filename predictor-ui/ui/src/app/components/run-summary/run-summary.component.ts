import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RunInfo } from '../../shared/Prediction';

@Component({
  standalone: true,
  selector: 'app-run-summary',
  imports: [CommonModule],
  template: `
    <div class="run-summary">
      <details *ngIf="run as info">
        <summary>Run {{ info.id }} · Settings</summary>
        <div class="run-settings">
          <span>Profile: {{ info.profile }}</span>
          <span *ngFor="let item of info.thresholds">{{ item.label }}: {{ item.value }}</span>
          <p *ngIf="info.note">{{ info.note }}</p>
          <p *ngIf="info.settings_file">Source: {{ info.settings_file }}</p>
        </div>
      </details>
      <div class="legend"><span class="actual">━ Actual</span><span class="test">━ Backtest</span><span class="future">━ Future</span><span class="input">┄ Processed input</span></div>
      <details class="axis-note"><summary>About the time axis</summary><p>0 is each model’s forecast origin. Blank history is not stored. Matching replay numbers do not guarantee matching simulation times.</p></details>
    </div>`,
  styles: [`
    :host { display: block; flex-shrink: 0; }
    .run-summary { padding: 7px 16px; border-bottom: 1px solid #ffffff0b; font-size: 11px; }
    summary { cursor: pointer; color: #ffd174; }
    .run-settings { display: flex; flex-wrap: wrap; gap: 8px 20px; padding: 10px 0; }
    .run-settings p { width: 100%; overflow-wrap: anywhere; }
    .axis-note { margin-top: 5px; } .axis-note summary { color: #9298a4; font-size: 10px; }
    .legend { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 6px; }
    .actual { color: #ffd174; } .test { color: #8498ff; } .future { color: #ff6689; } .input { color: #55cfb9; }
    p { color: #aeb2bc; margin-top: 5px; font-size: 10px; }
  `]
})
export class RunSummaryComponent { @Input() run?: RunInfo; }
