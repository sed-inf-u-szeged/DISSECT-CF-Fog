import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ButtonFabComponent } from './button-fab.component';

describe('ButtonFabComponent', () => {
  let component: ButtonFabComponent;
  let fixture: ComponentFixture<ButtonFabComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ButtonFabComponent]
    });
    fixture = TestBed.createComponent(ButtonFabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
