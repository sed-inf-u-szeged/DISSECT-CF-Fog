import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class ElectronService {
  api: string = 'electron-api'

  constructor() { }

  send(channel: string, data?: any) {
    if (!this.hasApi()) {
      throw Error('[send] Electron API is not supported in Browser!');
    }
    (window as any)[this.api].send(channel, data);
  }

  receive(channel: string): Observable<{ result: any }> {
    return new Observable((subscriber) => {
      if (!this.hasApi()) {
        subscriber.error('[receive] Electron API is not supported in Browser!');
      } else {
        (window as any)[this.api].receive(channel, (data: any) => {
          subscriber.next(data);
        });
      }
    });
  }

  hasApi() {
    return (window as any)[this.api];
  }
}
