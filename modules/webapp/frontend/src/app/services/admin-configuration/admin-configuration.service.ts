import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { adminConfiguration } from 'src/app/models/admin-configuration';
import { SERVER_URL } from 'src/app/models/server-api/server-api';

@Injectable({
  providedIn: 'root'
})
export class AdminConfigurationService {

  constructor(private http: HttpClient) {}

  //gets admin configurations from the database
  getAdminConfigurations(): Observable<adminConfiguration[]> {
    const url = `${SERVER_URL}configuration/getAdminConfigurations`;

    return this.http.get<adminConfiguration[]>(url, httpOptions);
  }
}

const httpOptions = {
  headers: new HttpHeaders({ 'Content-Type': 'application/json' })
};
