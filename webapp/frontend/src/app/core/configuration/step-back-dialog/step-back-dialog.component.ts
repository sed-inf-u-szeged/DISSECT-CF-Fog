import { Component, OnInit, Inject } from '@angular/core';
import { MatLegacyDialogRef as MatDialogRef, MAT_LEGACY_DIALOG_DATA as MAT_DIALOG_DATA } from '@angular/material/legacy-dialog';

@Component({
  selector: 'app-step-back-dialog',
  templateUrl: './step-back-dialog.component.html',
  styleUrls: ['./step-back-dialog.component.css']
})
export class StepBackDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<StepBackDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { okAction: boolean }
  ) {}

  public closeDialog(): void {
    this.data.okAction = false;
    this.dialogRef.close(this.data);
  }

  public dicardChanges(): void {
    this.data.okAction = true;
    this.dialogRef.close(this.data);
  }
}
