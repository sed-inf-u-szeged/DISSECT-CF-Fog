import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PredictionChartComponent } from './prediction-chart.component';

describe('PredictionChartComponent', () => {
  let component: PredictionChartComponent;
  let fixture: ComponentFixture<PredictionChartComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [PredictionChartComponent]
    });
    fixture = TestBed.createComponent(PredictionChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
