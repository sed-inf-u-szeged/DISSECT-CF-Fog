import { Injectable } from '@angular/core';
import { MatDrawer } from '@angular/material/sidenav';
import {
  InfoPanelData,
  getResourceFilesInfoData,
  getInstanceInfoData,
  getApplicationInfoData,
  getStationInfoData,
  getConnectionInfoData,
  getConfigurationErrorData
} from 'src/app/models/info-panel-data';
import { BehaviorSubject } from 'rxjs';

/**
 * This service controls the info panels, it is needed becasue
 * more than one MatDrawer are used. The app dialog is using an other one.
 */
@Injectable({
  providedIn: 'root'
})
export class PanelService {
  /**
   * This Subject contains the actially relevant InfoPanelData which are depends on
   * which info-icon was activated.
   */
  public infoData$ = new BehaviorSubject<InfoPanelData>(undefined);

  private mainDrawer: MatDrawer;
  private selectedDrawer: MatDrawer;

  public setDrawerAsMainDrawer(drawer: MatDrawer) {
    this.mainDrawer = drawer;
    this.selectedDrawer = drawer;
  }

  public setSelectedDrawer(drawer: MatDrawer) {
    this.selectedDrawer = drawer;
  }

  public setSelectedDrawerBacktoMainDrawer() {
    if (this.mainDrawer) {
      this.selectedDrawer = this.mainDrawer;
    }
  }

  public getDrawer(): MatDrawer {
    return this.selectedDrawer;
  }

  public open() {
    this.selectedDrawer.open();
  }

  public toogle() {
    this.selectedDrawer.toggle();
  }

  public close() {
    this.selectedDrawer.close();
  }

  public getResourceData(): void {
    this.infoData$.next(getResourceFilesInfoData());
  }

  public getInstanceData(): void {
    this.infoData$.next(getInstanceInfoData());
  }

  public getStationData(): void {
    this.infoData$.next(getStationInfoData());
  }

  public getApplicationData(): void {
    this.infoData$.next(getApplicationInfoData());
  }
  public getConnectionData(): void {
    this.infoData$.next(getConnectionInfoData());
  }

  public getConfigurationErrorData(): void {
    this.infoData$.next(getConfigurationErrorData());
  }
}
