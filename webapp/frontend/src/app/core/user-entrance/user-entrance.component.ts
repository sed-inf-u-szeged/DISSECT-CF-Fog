import { Component, OnInit } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { take } from 'rxjs/operators';
import { AuthService } from 'src/app/services/auth/auth.service';
import { TokenStorageService } from 'src/app/services/token-storage/token-storage.service';

export interface CoverData {
  title: string;
  buttonLabel: string;
}

@Component({
  selector: 'app-user-entrance',
  templateUrl: './user-entrance.component.html',
  styleUrls: ['./user-entrance.component.css']
})
export class UserEntranceComponent implements OnInit {
  public entranceForm: UntypedFormGroup;
  public isRegisterSuccessful = false;
  public isRegisterFailed = false;
  public isLoginSucessful = false;
  public isLoginFailed = false;
  public data: CoverData;
  /**
   * This tells that should show login or register content.
   */
  public isLogin = false;

  constructor(
    private authService: AuthService,
    private formBuilder: UntypedFormBuilder,
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private tokenStorageService: TokenStorageService
  ) {}

  public ngOnInit(): void {
    this.isLogin = this.activatedRoute.snapshot.routeConfig.path.includes('login');
    this.data = this.isLogin
      ? { title: 'Login', buttonLabel: 'Login' }
      : { title: 'Registration', buttonLabel: 'Register' };
    this.initForm();
  }

  private initForm() {
    this.entranceForm = this.formBuilder.group({
      email: new UntypedFormControl('', [Validators.required]),
      password: new UntypedFormControl('', [Validators.required])
    });
  }

  public onSubmit(): void {
    if (this.isLogin) {
      this.authService
        .login(this.entranceForm.value)
        .pipe(take(1))
        .subscribe(
          data => {
            this.tokenStorageService.saveToken(data.accessToken);
            this.tokenStorageService.saveUser(data);

            this.isLoginFailed = false;
            this.isLoginSucessful = true;
            this.router.navigate(['/home']);
          },
          err => {
            this.isLoginFailed = true;
          }
        );
    } else {
      this.authService.register(this.entranceForm.value).subscribe(
        data => {
          this.isRegisterSuccessful = true;
          this.isRegisterFailed = false;
        },
        err => {
          this.isRegisterFailed = true;
        }
      );
    }
  }
}
