import {Component, Input} from '@angular/core';

@Component({
  selector: 'app-button-fab',
  templateUrl: './button-fab.component.html',
  styleUrls: ['./button-fab.component.scss']
})
export class ButtonFabComponent {
  @Input() disabled: boolean = false;
  @Input() color: 'primary' | 'error' = 'primary';

  getClass() {
    return {
      primary: this.disabled ? false : this.color === 'primary',
      error: this.disabled ? false : this.color === 'error',
    };
  }
}
