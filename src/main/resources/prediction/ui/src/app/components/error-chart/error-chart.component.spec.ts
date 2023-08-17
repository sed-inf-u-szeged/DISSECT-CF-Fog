import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ErrorChartComponent } from './error-chart.component';

describe('ErrorChartComponent', () => {
  let component: ErrorChartComponent;
  let fixture: ComponentFixture<ErrorChartComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ErrorChartComponent]
    });
    fixture = TestBed.createComponent(ErrorChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
