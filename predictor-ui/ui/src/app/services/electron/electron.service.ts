import { Injectable, NgZone } from '@angular/core';
import { Observable, ReplaySubject, Subject } from 'rxjs';
import { MobilityRun } from 'src/app/shared/MobilityRun';
import { Prediction } from 'src/app/shared/Prediction';
import { MobilityJobState, MobilityLiveBatch, MobilityRunRequest, MobilityJobSnapshot } from 'src/app/shared/MobilityJob';

@Injectable({
  providedIn: 'root'
})
export class ElectronService {
  api: string = 'electron-api'

  private mobilitySubject = new ReplaySubject<MobilityRun>(1);
  public mobilityUpdates$ = this.mobilitySubject.asObservable();
  private jobSubject = new ReplaySubject<MobilityJobState>(1);
  public mobilityJobUpdates$ = this.jobSubject.asObservable();
  private liveSubject = new Subject<MobilityLiveBatch>();
  public mobilityLiveUpdates$ = this.liveSubject.asObservable();

  private dataSubject = new Subject<Map<string, Prediction[]>>();
  public dataUpdates$: Observable<Map<string, Prediction[]>> = this.dataSubject.asObservable();

  private errorSubject = new ReplaySubject<string>(1);
  public errorUpdates$: Observable<string> = this.errorSubject.asObservable();

  constructor(private zone: NgZone) {
    if (!this.hasApi()) {
      this.errorSubject.next('Electron API is not supported in Browser!')
      throw Error('[readData] Electron API is not supported in Browser!');
    }
    (window as any)[this.api].onMobilityUpdated?.((data: MobilityRun) => {
      this.zone.run(() => this.mobilitySubject.next(data));
    });
    (window as any)[this.api].onMobilityJobUpdated?.((state: MobilityJobState) => this.zone.run(() => this.jobSubject.next(state)));
    (window as any)[this.api].onMobilityLiveUpdated?.((batch: MobilityLiveBatch) => this.zone.run(() => this.liveSubject.next(batch)));
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

  async openMobilityRun(): Promise<boolean> {
    return (window as any)[this.api].openMobilityRun();
  }
  async getMobilityJob(): Promise<MobilityJobSnapshot> { return (window as any)[this.api].getMobilityJob(); }
  async startMobilityJob(request: MobilityRunRequest): Promise<MobilityJobState> { return (window as any)[this.api].startMobilityJob(request); }
  async cancelMobilityJob(): Promise<boolean> { return (window as any)[this.api].cancelMobilityJob(); }

  hasApi() {
    return (window as any)[this.api];
  }
}
