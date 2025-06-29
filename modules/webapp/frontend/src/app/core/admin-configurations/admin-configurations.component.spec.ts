import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminConfigurationsComponent } from './admin-configurations.component';

describe('AdminConfigurationsComponent', () => {
  let component: AdminConfigurationsComponent;
  let fixture: ComponentFixture<AdminConfigurationsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AdminConfigurationsComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminConfigurationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
