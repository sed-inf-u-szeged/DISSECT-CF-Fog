import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SERVER_URL, SignInResponse, SignUpResponse } from 'src/app/models/server-api/server-api';
import { User } from 'src/app/models/user';

const AUTH_API = SERVER_URL + 'auth/';

const httpOptions = {
  headers: new HttpHeaders({ 'Content-Type': 'application/json' })
};

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  constructor(private http: HttpClient) {}

  public login(credentials: User): Observable<SignInResponse> {
    return this.http.post<SignInResponse>(
      AUTH_API + 'signin',
      {
        email: credentials.email,
        password: credentials.password
      },
      httpOptions
    );
  }

  public register(user: User): Observable<SignUpResponse> {
    return this.http.post<SignUpResponse>(
      AUTH_API + 'signup',
      {
        email: user.email,
        password: user.password
      },
      httpOptions
    );
  }
}
