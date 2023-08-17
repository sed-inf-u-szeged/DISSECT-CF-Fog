import { Component } from '@angular/core';
import { ModalService } from 'src/app/services/modal-service/modal.service';

@Component({
  selector: 'app-modal-base',
  templateUrl: './modal-base.component.html',
  styleUrls: ['./modal-base.component.scss']
})
export class ModalBaseComponent {
  constructor(public modalService: ModalService) { }

  close() {
    if (!this.modalService.canClose) {
      return;
    }
    this.modalService.closeCurrentmodal();
  }

}
