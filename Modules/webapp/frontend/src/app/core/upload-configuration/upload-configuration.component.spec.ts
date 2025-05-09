import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UploadConfigurationComponent } from './upload-configuration.component';

describe('UploadConfigurationComponent', () => {
  let component: UploadConfigurationComponent;
  let fixture: ComponentFixture<UploadConfigurationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ UploadConfigurationComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UploadConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
