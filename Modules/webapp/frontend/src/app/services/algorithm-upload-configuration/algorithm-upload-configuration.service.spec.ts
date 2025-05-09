import { TestBed } from '@angular/core/testing';

import { AlgorithmUploadConfigurationService } from './algorithm-upload-configuration.service';

describe('AlgorithmUploadConfigurationService', () => {
  let service: AlgorithmUploadConfigurationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AlgorithmUploadConfigurationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
