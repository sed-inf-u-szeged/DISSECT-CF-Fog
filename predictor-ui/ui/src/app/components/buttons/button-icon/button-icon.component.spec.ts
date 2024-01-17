import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ButtonIconComponent } from './button-icon.component';

describe('ButtonIconComponent', () => {
  let component: ButtonIconComponent;
  let fixture: ComponentFixture<ButtonIconComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ButtonIconComponent]
    });
    fixture = TestBed.createComponent(ButtonIconComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
