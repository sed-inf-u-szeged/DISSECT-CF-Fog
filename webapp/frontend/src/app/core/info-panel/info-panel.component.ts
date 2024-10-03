import { Component, Input } from '@angular/core';
import { InfoPanelData } from 'src/app/models/info-panel-data';
import { PanelService } from 'src/app/services/panel/panel.service';

@Component({
  selector: 'app-info-panel',
  templateUrl: './info-panel.component.html',
  styleUrls: ['./info-panel.component.css']
})
export class InfoPanelComponent {
  @Input() public infoData: InfoPanelData;

  constructor(public panelService: PanelService) {}
}
