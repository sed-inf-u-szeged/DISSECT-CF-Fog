import { AfterViewInit, Component, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';

@Component({
  selector: 'app-progress-bar',
  templateUrl: './progress-bar.component.html',
  styleUrls: ['./progress-bar.component.scss']
})
export class ProgressBarComponent implements OnChanges, AfterViewInit {
  @Input() max: number;
  @Input() current: number;
  @ViewChild('progress') progress!: { nativeElement: any };

  ngOnChanges(changes: any): void {
    if (changes && changes.current && this.progress) {
      this.progress.nativeElement.style.width = `${this.getPercentage()}%`;
    }
  }

  ngAfterViewInit(): void {
    this.progress.nativeElement.style.width = `${this.getPercentage()}%`;
  }

  getPercentage() {
    return Math.round((this.current / this.max) * 100);
  }
}
