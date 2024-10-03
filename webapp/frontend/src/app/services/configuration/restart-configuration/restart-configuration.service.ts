import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class RestartConfigurationService {
  public restartConfigurationSubject = new Subject();
  public restartConfiguration$ = this.restartConfigurationSubject.asObservable();

  public restart(): void {
    this.restartConfigurationSubject.next();
  }
}
