import {
  Component,
  OnInit,
  Inject,
  QueryList,
  ViewChildren,
  ChangeDetectorRef,
  AfterViewChecked,
  ViewChild,
  AfterViewInit
} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Application, ApplicationsObject } from 'src/app/models/application';
import { ApplicationCardComponent } from './application-card/application-card.component';
import { MatDrawer } from '@angular/material/sidenav';
import { PanelService } from 'src/app/services/panel/panel.service';

@Component({
  selector: 'app-applications-dialog',
  templateUrl: './applications-dialog.component.html',
  styleUrls: ['./applications-dialog.component.css']
})
export class ApplicationsDialogComponent implements OnInit, AfterViewChecked, AfterViewInit {
  public appIndex = 0;

  @ViewChildren(ApplicationCardComponent) public applicationCards: QueryList<ApplicationCardComponent>;
  @ViewChild('drawer') public drawer: MatDrawer;

  constructor(
    public dialogRef: MatDialogRef<ApplicationsDialogComponent>,
    private cdRef: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) public data: { nodeId: string; numOfApps: number; applications: ApplicationsObject },
    public panelService: PanelService
  ) {}

  public ngOnInit(): void {
    const appsArrayLength = Object.values(this.data.applications).length;
    this.appIndex = this.calculateLastAppIndex();
    if (appsArrayLength < this.data.numOfApps) {
      for (let i = 0; i < this.data.numOfApps - appsArrayLength; i++) {
        this.createApp();
      }
    }
  }

  public ngAfterViewChecked(): void {
    this.cdRef.detectChanges();
  }

  public ngAfterViewInit(): void {
    if (this.drawer) {
      this.panelService.setSelectedDrawer(this.drawer);
    }
  }

  private calculateLastAppIndex(): number {
    if (Object.keys(this.data.applications).length === 0) {
      return 0;
    }
    const keys: number[] = [];
    for (const id of Object.keys(this.data.applications)) {
      keys.push(+id.split('app')[1]);
    }
    return Math.max(...keys);
  }

  public onNoClick(): void {
    this.dialogRef.close();
  }

  private filterOutUnConfiguredApps(): ApplicationsObject {
    const configuredApps: ApplicationsObject = {};
    for (const [id, app] of Object.entries(this.data.applications)) {
      if (app.isConfigured) {
        configuredApps[id] = app;
      }
    }
    return configuredApps;
  }

  public submitApplicationCards(): void {
    this.saveApplicationsState();
    this.closeWithData();
  }

  public checkDialogIsValid(): boolean {
    let isValidAll = true;
    if (this.applicationCards) {
      this.applicationCards.forEach(appCard => {
        if (!appCard.checkValidation()) {
          isValidAll = false;
        } else {
          appCard.application.isConfigured = true;
        }
      });
    }
    return isValidAll;
  }

  private closeWithData(): void {
    const dialogResult = {
      applications: this.filterOutUnConfiguredApps(),
      valid: this.checkDialogIsValid()
    };

    this.dialogRef.close(dialogResult);
  }

  private createApp(): void {
    this.appIndex += 1;
    const appId = this.data.nodeId + '_app' + this.appIndex;
    const app = new Application();
    app.id = appId;
    app.isConfigured = false;
    this.data.applications[app.id] = app;
  }

  private saveApplicationsState(): void {
    if (this.applicationCards) {
      this.data.applications = {};
      this.applicationCards.forEach(appCard => {
        const application = appCard.getValidApplication();
        this.data.applications[application.id] = application;
      });
    }
  }
}
