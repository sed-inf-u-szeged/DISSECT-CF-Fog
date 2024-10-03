import { Injectable } from '@angular/core';
import { MatLegacyDialog as MatDialog, MatLegacyDialogRef as MatDialogRef } from '@angular/material/legacy-dialog';
import { StepBackDialogComponent } from 'src/app/core/configuration/step-back-dialog/step-back-dialog.component';
import { WindowSizeService } from '../../window-size/window-size.service';

@Injectable({
  providedIn: 'root'
})
export class StepBackDialogService {
  constructor(private dialog: MatDialog, public windowService: WindowSizeService) {}

  public openDialog(): MatDialogRef<StepBackDialogComponent, any> {
    return this.dialog.open(StepBackDialogComponent, {
      panelClass: 'applications-dialog-panel',
      disableClose: true,
      width: this.windowService.calculateWidthForStepBackDialog(),
      data: { discard: false }
    });
  }
}
