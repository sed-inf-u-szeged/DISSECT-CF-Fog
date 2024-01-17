import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CheckboxContainerComponent } from './checkbox-container.component';

describe('CheckboxContainerComponent', () => {
  let component: CheckboxContainerComponent;
  let fixture: ComponentFixture<CheckboxContainerComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [CheckboxContainerComponent]
    });
    fixture = TestBed.createComponent(CheckboxContainerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
