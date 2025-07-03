import { HttpClient } from '@angular/common/http';
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

export interface UnevirsitiesData {
  country: string;
  domains: string[];
  code: string;
  name: string;
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
  public currentDomain: string;
  allUniName: string[] = [];
  selected: string;
  /**
   * This tells that should show login or register content.
   */
  public isLogin = false;
  currentUniversity: UnevirsitiesData;
  constructor(
    private authService: AuthService,
    private formBuilder: UntypedFormBuilder,
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private tokenStorageService: TokenStorageService,
    private http: HttpClient
  ) { }

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
      password: new UntypedFormControl('', [Validators.required]),
      university: new UntypedFormControl('', [Validators.required])
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
    this.checkRegistrationData()
  }

  /**
   * Check if the e-mail contains @ characther and checks if the password is correct
   * @returns 
   */
  private checkRegistrationData(): boolean {
    const email = this.entranceForm.value.email;
    const pw = this.entranceForm.value.password;
    let isEmailGood = false;
    let isPwGood = false;

    let emailFirstPart;
    let temp = [];

    temp = email.split('@');
    emailFirstPart = temp[0];
    this.currentDomain = temp[1];


    if (email.includes('@')) {
      isEmailGood = true;
    }
    if (pw.length >= 8 && pw.includes('^(?=.*[A-Z]).+$') && pw.includes('^(?=.*[a-z]).+$') && pw.includes('.*[0-9].*')) {
      isPwGood = true;
    }
    if (isEmailGood && isPwGood) {
      return true;
    } else {
      return false;
    }
  }
  /**
   * If the e-mail address is valid for one of the universities this will give back.
   */
  getUniversitiesWithData() {
    this.allUniName= [];
    const email = this.entranceForm.value.email;
    let temp = [];
    temp = email.split('@');
    this.currentDomain = temp[1];

    this.http.get<UnevirsitiesData>('http://universities.hipolabs.com/search?domain=' + this.currentDomain, { observe: 'response' }).subscribe(data => {
      if (Object.keys(data.body).length !== 0) {
        if (Object.keys(data.body).length > 1) {
          for (let i = 0; i < (Object.keys(data.body).length); i++) {
            this.allUniName.push(data.body[i].name);
          }
          this.selected = this.allUniName[0];
        } else {
          this.allUniName.push(data.body[0].name);
          this.selected = this.allUniName[0];
        }
      } else {
        this.getUniversitiesWithoutData()
      }
    })
  }

  /**
   * If the e-mail not valid any of the universities this will get every university name
   */
  getUniversitiesWithoutData(){
    this.http.get<UnevirsitiesData>('http://universities.hipolabs.com/search?', { observe: 'response' }).subscribe(data => {
      for (let i = 0; i < (Object.keys(data.body).length); i++) {
        this.allUniName.push(data.body[i].name);
      }
      this.selected = this.allUniName[0];
    })
  }
}