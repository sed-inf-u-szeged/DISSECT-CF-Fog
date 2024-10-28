import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { UserInfo } from 'os';
import { Observable } from 'rxjs';
import { SERVER_URL, SignInResponse, SignUpResponse } from 'src/app/models/server-api/server-api';
import { User } from 'src/app/models/user';

const AUTH_API = SERVER_URL + 'user/';

const httpOptions = {
  headers: new HttpHeaders({ 'Content-Type': 'application/json' })
};

@Injectable({
  providedIn: 'root'
})
export class UserManagementService {

  constructor(private http: HttpClient) { }

 public getAllUser(): Observable<User[]> {

   return this.http.post<User[]>(SERVER_URL + '/allUsers', httpOptions);
 }

 //public getAllUser(): Observable<User[]> {
 //  return this.http.post<User[]>(
 //    AUTH_API + 'getAllUser',
 //    httpOptions
 //  );
 //}
}