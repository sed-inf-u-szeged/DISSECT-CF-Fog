import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigurableInstanceComponent } from './configurable-instance.component';

describe('ConfigurableInstanceComponent', () => {
  let component: ConfigurableInstanceComponent;
  let fixture: ComponentFixture<ConfigurableInstanceComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ConfigurableInstanceComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigurableInstanceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
