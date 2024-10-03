import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { QuantityCounterService } from './quantity-counter.service';

describe('QuantityCounterService', () => {
  let service: QuantityCounterService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      schemas: [NO_ERRORS_SCHEMA]
    });
    service = TestBed.inject(QuantityCounterService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
