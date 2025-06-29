import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoadingCircleComponent } from './loading-circle.component';

describe('LoadingCircleComponent', () => {
  let component: LoadingCircleComponent;
  let fixture: ComponentFixture<LoadingCircleComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [LoadingCircleComponent]
    });
    fixture = TestBed.createComponent(LoadingCircleComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
