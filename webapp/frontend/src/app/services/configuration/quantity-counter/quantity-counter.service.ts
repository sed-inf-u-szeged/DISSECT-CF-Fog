import { Injectable } from '@angular/core';

/**
 * This service keeps track of the current amount of clouds and fogs.
 * Stores how many nodes are selected for configuration and how many are still missing.
 */
@Injectable({
  providedIn: 'root'
})
export class QuantityCounterService {
  public numOfClouds: number;
  public numOfFogs: number;
  public dividedClouds: number;
  public dividedFogs: number;

  public setNodeQuantities(nOfClouds: number, nOfFogs: number, divClouds: number, divFogs: number) {
    this.numOfClouds = nOfClouds;
    this.numOfFogs = nOfFogs;
    this.dividedClouds = divClouds;
    this.dividedFogs = divFogs;
  }

  /**
   * Returns how many clouds are left that are not yet selected for configuration.
   */
  public getUndividedClouds(): number {
    return this.numOfClouds - this.dividedClouds;
  }

  /**
   * Returns how many fogs are left that are not yet selected for configuration.
   */
  public getUndividedFogs(): number {
    return this.numOfFogs - this.dividedFogs;
  }

  public increaseClouds(): boolean {
    if (this.dividedClouds + 1 <= this.numOfClouds) {
      this.dividedClouds++;
      return true;
    }
    return false;
  }

  public increaseFogs(): boolean {
    if (this.dividedFogs + 1 <= this.numOfFogs) {
      this.dividedFogs++;
      return true;
    }
    return false;
  }

  public decreseClouds(unitNumber: number): boolean {
    if (this.dividedClouds - 1 > 0 && unitNumber > 1) {
      this.dividedClouds--;
      return true;
    }
    return false;
  }

  public decreseFogs(unitNumber: number): boolean {
    if (this.dividedFogs - 1 > 0 && unitNumber > 1) {
      this.dividedFogs--;
      return true;
    }
    return false;
  }
}
