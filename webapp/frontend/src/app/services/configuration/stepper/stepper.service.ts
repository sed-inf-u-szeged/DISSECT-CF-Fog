import { Injectable } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';

@Injectable({
  providedIn: 'root'
})
export class StepperService {
  private matStepper: MatStepper;

  public setStepper(stepper: MatStepper): void {
    this.matStepper = stepper;
  }

  public get stepper(): MatStepper {
    return this.matStepper;
  }

  public stepBack(): void {
    if (this.matStepper) {
      this.matStepper.previous();
    }
  }

  public stepForward(): void {
    if (this.matStepper) {
      this.matStepper.next();
    }
  }
}
