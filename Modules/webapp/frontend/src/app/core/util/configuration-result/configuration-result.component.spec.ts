import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';

import { ConfigurationResultComponent } from './configuration-result.component';

describe('ConfigurationResultComponent', () => {
  let component: ConfigurationResultComponent;
  let fixture: ComponentFixture<ConfigurationResultComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ConfigurationResultComponent],
      providers: [{ provide: UserConfigurationService, useValue: {} }],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigurationResultComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
