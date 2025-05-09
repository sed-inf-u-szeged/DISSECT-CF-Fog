import { Component, EventEmitter, Output, Input } from '@angular/core';
import { TokenStorageService } from 'src/app/services/token-storage/token-storage.service';

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.css']
})
export class ToolbarComponent {
  @Input() showItems: boolean;
  @Input() title: string;
  @Output() sidenavToggle = new EventEmitter<void>();

  constructor(public tokenStorageService: TokenStorageService) {}
}
