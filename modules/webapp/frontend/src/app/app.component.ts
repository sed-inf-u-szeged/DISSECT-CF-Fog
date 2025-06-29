import { Component, HostListener, OnInit, ViewChild } from '@angular/core';
import { MatSidenav } from '@angular/material/sidenav';
import { TokenStorageService } from './services/token-storage/token-storage.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  public title = 'DISSECT-CF-Fog-WebApp';
  public isLinear = false;
  public shouldOpenSidenav = true;
  public isBigScreen = true;
  public isLoggedIn = true;

  @ViewChild('sidenav') public sidenav: MatSidenav;

  constructor(public tokenStorageService: TokenStorageService) {}

  public ngOnInit(): void {
    this.isBigScreen = window.innerWidth > 1000;
  }

  /**
   * Listener for window size changing which sets the boolean variable
   */
  @HostListener('window:resize', ['$event'])
  public onResize(event): void {
    this.isBigScreen = event.target.innerWidth > 1000;
  }

  public toggleExpanded(): void {
    if (this.sidenav) {
      this.sidenav.toggle();
    }
  }
}
