import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AlgorithmUploadComponent } from './algorithm-upload.component';

describe('AlgorithmUploadComponent', () => {
  let component: AlgorithmUploadComponent;
  let fixture: ComponentFixture<AlgorithmUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AlgorithmUploadComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AlgorithmUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
