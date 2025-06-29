import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { SignInResponse } from 'src/app/models/server-api/server-api';

const TOKEN_KEY = 'auth-token';
const USER_KEY = 'auth-user';

@Injectable({
  providedIn: 'root'
})
export class TokenStorageService {
  private tokenSubject = new BehaviorSubject<string>(sessionStorage.getItem(TOKEN_KEY));
  public userToken$ = this.tokenSubject.asObservable();

  public signOut(): void {
    sessionStorage.clear();
    this.tokenSubject.next(undefined);
  }

  public saveToken(token: string): void {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.setItem(TOKEN_KEY, token);

    this.tokenSubject.next(token);
  }

  public saveUser(user: SignInResponse): void {
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  public getUser(): SignInResponse {
    return JSON.parse(sessionStorage.getItem(USER_KEY));
  }

  public getToken(): string {
    return sessionStorage.getItem(TOKEN_KEY);
  }
}
