import { Component } from '@angular/core';
import { PanelService } from 'src/app/services/panel-service/panel.service';

@Component({
  selector: 'app-right-panel',
  templateUrl: './right-panel.component.html',
  styleUrls: ['./right-panel.component.scss']
})
export class RightPanelComponent {
  constructor(public panelService: PanelService) { }

  close() {
    if (!this.panelService.canClose) {
      return;
    }
    this.panelService.closeCurrentPanel();
  }

}
