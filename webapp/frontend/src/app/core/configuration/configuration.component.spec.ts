import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AngularMaterialModule } from 'src/app/angular-material/angular-material.module';
import { ResourceSelectionService } from 'src/app/services/configuration/resource-selection/resource-selection.service';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';

import { ConfigurationComponent } from './configuration.component';

const mockResourceSelectionService = {
  refreshResources() {}
};

describe('ConfigurationComponent', () => {
  let component: ConfigurationComponent;
  let fixture: ComponentFixture<ConfigurationComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ConfigurationComponent],
      imports: [BrowserAnimationsModule, AngularMaterialModule],
      providers: [
        { provide: UserConfigurationService, useValue: {} },
        { provide: ResourceSelectionService, useValue: mockResourceSelectionService }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
