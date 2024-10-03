import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { UntypedFormBuilder } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { AuthService } from 'src/app/services/auth/auth.service';
import { TokenStorageService } from 'src/app/services/token-storage/token-storage.service';

import { UserEntranceComponent } from './user-entrance.component';

describe('UserEntranceComponent', () => {
  let component: UserEntranceComponent;
  let fixture: ComponentFixture<UserEntranceComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [UserEntranceComponent],
      imports: [HttpClientTestingModule, RouterTestingModule],
      providers: [
        UntypedFormBuilder,
        { provide: AuthService, useValue: {} },
        { provide: TokenStorageService, useValue: {} },
        { provide: ActivatedRoute, useValue: { snapshot: { routeConfig: { path: '' } } } }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(UserEntranceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
