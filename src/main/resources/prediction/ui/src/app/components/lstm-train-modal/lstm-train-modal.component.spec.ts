import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LstmTrainModalComponent } from './lstm-train-modal.component';

describe('LstmTrainModalComponent', () => {
  let component: LstmTrainModalComponent;
  let fixture: ComponentFixture<LstmTrainModalComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [LstmTrainModalComponent]
    });
    fixture = TestBed.createComponent(LstmTrainModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
