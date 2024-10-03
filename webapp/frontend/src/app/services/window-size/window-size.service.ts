import { Injectable } from '@angular/core';

/**
 * It helps to calculate dialog's width depending on the window's width.
 */
@Injectable({
  providedIn: 'root'
})
export class WindowSizeService {
  public calculateWidthForApplicationDialog(): string {
    const width = window.innerWidth;
    if (width < 700) {
      return '95%';
    } else if (width > 700 && width < 950) {
      return '80%';
    } else {
      return '55%';
    }
  }

  public calculateWidthForStepBackDialog(): string {
    const width = window.innerWidth;
    if (width < 700) {
      return '80%';
    } else {
      return '40%';
    }
  }
}
