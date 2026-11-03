import { Injectable, NgZone } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Prediction } from 'src/app/shared/Prediction';

@Injectable({
  providedIn: 'root'
})
export class ElectronService {
  api: string = 'electron-api'

  private dataSubject = new Subject<Map<string, Prediction[]>>();
  public dataUpdates$: Observable<Map<string, Prediction[]>> = this.dataSubject.asObservable();

  private errorSubject = new Subject<string>();
  public errorUpdates$: Observable<string> = this.errorSubject.asObservable();

  constructor(private zone: NgZone) {
    if (!this.hasApi()) {
      this.errorSubject.next('Electron API is not supported in Browser!')
      throw Error('[readData] Electron API is not supported in Browser!');
    }
    (window as any)[this.api].onDataUpdated((data: Map<string, Prediction[]>) => {
        // Electron contextBridge callbacks can arrive outside Angular's zone.
        this.zone.run(() => this.dataSubject.next(data));
    });
    (window as any)[this.api].onDataError((data: string) => {
        this.zone.run(() => this.errorSubject.next(data));
    });
  }

  cleanupListeners(): void {
    if ((window as any)[this.api]?.removeAllListeners) {
      (window as any)[this.api].removeAllListeners();
    }
  }

  hasApi() {
    return (window as any)[this.api];
  }
}
