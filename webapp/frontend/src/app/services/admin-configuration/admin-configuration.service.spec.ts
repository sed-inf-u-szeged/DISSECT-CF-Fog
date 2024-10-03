import { TestBed } from '@angular/core/testing';

import { AdminConfigurationService } from './admin-configuration.service';

describe('AdminConfigurationService', () => {
  let service: AdminConfigurationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AdminConfigurationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
