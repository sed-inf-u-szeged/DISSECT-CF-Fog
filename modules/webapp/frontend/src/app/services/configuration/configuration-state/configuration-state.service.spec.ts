import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ConfigurationStateService } from './configuration-state.service';

describe('ConfigurationStateService', () => {
  let service: ConfigurationStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA]
    });
    service = TestBed.inject(ConfigurationStateService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
