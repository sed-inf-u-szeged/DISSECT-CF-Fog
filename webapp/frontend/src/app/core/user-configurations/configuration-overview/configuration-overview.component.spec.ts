import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';

import { ConfigurationOverviewComponent } from './configuration-overview.component';

describe('ConfigurationOverviewComponent', () => {
  let component: ConfigurationOverviewComponent;
  let fixture: ComponentFixture<ConfigurationOverviewComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ConfigurationOverviewComponent],
      providers: [{ provide: UserConfigurationService, useValue: {} }],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigurationOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
