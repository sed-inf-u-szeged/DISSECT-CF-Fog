import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, shareReplay, switchMapTo } from 'rxjs/operators';
import {
  Instance,
  InstancesResponse,
  Resource,
  SERVER_URL,
  StrategysResponse as StrategiesResponse
} from 'src/app/models/server-api/server-api';

/**
 * API calls for selections.
 */
@Injectable()
export class ResourceSelectionService {
  private readonly PROPERTIES_API = SERVER_URL + 'properties';

  public strategiesForApplications$: Observable<string[]>;
  public strategiesForDevices$: Observable<string[]>;
  public resources$: Observable<Resource[]>;
  private refreshAPIcalls$ = new BehaviorSubject<void>(undefined);

  constructor(
    private http: HttpClient
    ) {
    this.refreshResources();
    this.strategiesForApplications$ = this.refreshAPIcalls$.pipe(
      switchMapTo(this.getStrategiesForApplications()),
      shareReplay(1)
    );
    this.strategiesForDevices$ = this.refreshAPIcalls$.pipe(
      switchMapTo(this.getStrategiesForDevices()),
      shareReplay(1)
    );
    this.resources$ = this.refreshAPIcalls$.pipe(switchMapTo(this.getResurceFiles()), shareReplay(1));
  }

  public refreshResources() {
    this.refreshAPIcalls$.next();
  }

  public getResurceFiles(): Observable<Resource[]> {
    return this.http.get<Resource[]>(this.PROPERTIES_API + '/resources');
  }

  /**
   * This method transform the given property key. If the key contains '-' character,
   * it will be removed and after this, the next caracter will be upper case.
   * @param key - a property name
   */
  private parseInstanceKey(key: string): string {
    if (!key.includes('-')) {
      return key;
    }
    const words = key.split('-');
    for (let i = 1; i < words.length; i++) {
      words[i] = words[i].charAt(0).toUpperCase() + words[i].slice(1);
    }
    return words.join('');
  }

  public getStrategiesForApplications(): Observable<string[]> {
    return this.http
      .get<StrategiesResponse>(this.PROPERTIES_API + '/strategies/application')
      .pipe(map(strategies => strategies.strategy));
  }

  public getStrategiesForDevices(): Observable<string[]> {
    return this.http
      .get<StrategiesResponse>(this.PROPERTIES_API + '/strategies/device')
      .pipe(map(strategies => strategies.strategy));
  }
}
