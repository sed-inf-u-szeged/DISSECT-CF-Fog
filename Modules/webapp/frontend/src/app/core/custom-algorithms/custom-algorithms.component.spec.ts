import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CustomAlgorithmsComponent } from './custom-algorithms.component';

describe('CustomAlgorithmsComponent', () => {
  let component: CustomAlgorithmsComponent;
  let fixture: ComponentFixture<CustomAlgorithmsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ CustomAlgorithmsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CustomAlgorithmsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
