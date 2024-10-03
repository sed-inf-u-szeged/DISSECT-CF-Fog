import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import { ConfigurationResultComponent } from '../../util/configuration-result/configuration-result.component';

import { ConfigurationEndComponent } from './configuration-end.component';

describe('ConfigurationEndComponent', () => {
  let component: ConfigurationEndComponent;
  let fixture: ComponentFixture<ConfigurationEndComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ConfigurationEndComponent, ConfigurationResultComponent],
      providers: [{ provide: UserConfigurationService, useValue: {} }],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigurationEndComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
