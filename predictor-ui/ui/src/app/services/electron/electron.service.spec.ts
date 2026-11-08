import { NgZone } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ElectronService } from './electron.service';

describe('Electron IPC change detection', () => {
  let savedApi: any;
  let onData: (data: Map<string, any[]>) => void;
  let onError: (error: string) => void;
  let remove: jasmine.Spy;
  beforeEach(() => {
    savedApi = (window as any)['electron-api'];
    remove = jasmine.createSpy('remove');
    (window as any)['electron-api'] = {
      onDataUpdated: (callback: any) => onData = callback,
      onDataError: (callback: any) => onError = callback,
      removeAllListeners: remove
    };
  });
  afterEach(() => { (window as any)['electron-api'] = savedApi; });
  it('delivers both data and errors inside Angular even when the bridge callback is outside', () => {
    const service = TestBed.inject(ElectronService);
    const zone = TestBed.inject(NgZone);
    const zones: boolean[] = [];
    service.dataUpdates$.subscribe(() => zones.push(NgZone.isInAngularZone()));
    service.errorUpdates$.subscribe(() => zones.push(NgZone.isInAngularZone()));
    zone.runOutsideAngular(() => { onData(new Map()); onError('test error'); });
    expect(zones).toEqual([true, true]);
    service.cleanupListeners();
    expect(remove).toHaveBeenCalledTimes(1);
  });
});
