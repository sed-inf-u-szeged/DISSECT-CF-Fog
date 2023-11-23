import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-button-icon',
  templateUrl: './button-icon.component.html',
  styleUrls: ['./button-icon.component.scss']
})
export class ButtonIconComponent {
  @Input() icon: string;
  @Input() color: 'primary' | 'error' | 'normal' | 'normal-light' = 'primary';
  @Input() disabled: boolean = false;

  getClass() {
    return {
      primary: this.disabled ? false : this.color === 'primary',
      error: this.disabled ? false : this.color === 'error',
      normal: this.disabled ? false : this.color === 'normal',
      'normal-light': this.disabled ? false : this.color === 'normal-light',
    };
  }
}
