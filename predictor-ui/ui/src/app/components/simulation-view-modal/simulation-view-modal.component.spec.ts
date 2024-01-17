import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SimulationViewModalComponent } from './simulation-view-modal.component';

describe('SimulationViewModalComponent', () => {
  let component: SimulationViewModalComponent;
  let fixture: ComponentFixture<SimulationViewModalComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SimulationViewModalComponent]
    });
    fixture = TestBed.createComponent(SimulationViewModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
