import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AngularMaterialModule } from 'src/app/angular-material/angular-material.module';
import { StepBackDialogService } from './step-back-dialog.service';

describe('StepBackDialogService', () => {
  let service: StepBackDialogService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AngularMaterialModule],
      schemas: [NO_ERRORS_SCHEMA]
    });
    service = TestBed.inject(StepBackDialogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
