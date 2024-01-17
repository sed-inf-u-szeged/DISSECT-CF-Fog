import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-button',
  templateUrl: './button.component.html',
  styleUrls: ['./button.component.scss']
})
export class ButtonComponent {
  @Input() disabled: boolean = false;
  @Input() color: 'primary' | 'error' | 'normal' = 'primary';

  getClass() {
    return {
      primary: this.disabled ? false : this.color === 'primary',
      error: this.disabled ? false : this.color === 'error',
      normal: this.disabled ? false : this.color === 'normal',
    };
  }
}
