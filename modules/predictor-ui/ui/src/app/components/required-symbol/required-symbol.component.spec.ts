import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RequiredSymbolComponent } from './required-symbol.component';

describe('RequiredSymbolComponent', () => {
  let component: RequiredSymbolComponent;
  let fixture: ComponentFixture<RequiredSymbolComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [RequiredSymbolComponent]
    });
    fixture = TestBed.createComponent(RequiredSymbolComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
