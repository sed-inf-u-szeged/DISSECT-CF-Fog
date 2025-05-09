import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { UntypedFormBuilder } from '@angular/forms';
import { ResourceSelectionService } from 'src/app/services/configuration/resource-selection/resource-selection.service';

import { ConfigurableStationComponent } from './configurable-station.component';

const mockResourceSelectionService = {
  getUndividedClouds() {
    return 0;
  },
  getUndividedFogs() {
    return 0;
  }
};

describe('ConfigurableStationComponent', () => {
  let component: ConfigurableStationComponent;
  let fixture: ComponentFixture<ConfigurableStationComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ConfigurableStationComponent],
      imports: [HttpClientTestingModule],
      providers: [UntypedFormBuilder, { provide: ResourceSelectionService, useValue: mockResourceSelectionService }],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigurableStationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
