import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import { UserConfigurationsComponent } from './user-configurations.component';

describe('UserConfigurationsComponent', () => {
  let component: UserConfigurationsComponent;
  let fixture: ComponentFixture<UserConfigurationsComponent>;

  const moockUserConfigurationsComponent = {
    getUserConfigurationsDetails() {
      return new Subject();
    }
  };

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [UserConfigurationsComponent],
      providers: [{ provide: UserConfigurationService, useValue: moockUserConfigurationsComponent }],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(UserConfigurationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
