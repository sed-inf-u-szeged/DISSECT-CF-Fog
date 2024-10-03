import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { UserConfigurationService } from './user-configuration.service';

describe('UserConfigurationService', () => {
  let service: UserConfigurationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: UserConfigurationService, useValue: {} }],
      schemas: [NO_ERRORS_SCHEMA]
    });
    service = TestBed.inject(UserConfigurationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
