import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { RestartConfigurationService } from './restart-configuration.service';

describe('RestartConfigurationService', () => {
  let service: RestartConfigurationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA]
    });
    service = TestBed.inject(RestartConfigurationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
