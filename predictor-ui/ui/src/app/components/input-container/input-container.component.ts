import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-input-container',
  templateUrl: './input-container.component.html',
  styleUrls: ['./input-container.component.scss']
})
export class InputContainerComponent {
  @Input() orientation: 'vertical' | 'horizontal' = 'vertical'

}
