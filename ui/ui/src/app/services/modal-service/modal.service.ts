import { ApplicationRef, ComponentFactoryResolver, EmbeddedViewRef, EventEmitter, Injectable, Injector } from '@angular/core';
import { ModalBaseComponent } from 'src/app/components/modal-base/modal-base.component';

@Injectable({
  providedIn: 'root'
})
export class ModalService {
  closeEmitter: EventEmitter<any> = new EventEmitter();
  componentRef: any;
  modalRef: any;
  data: any;
  opts: any;
  canClose: boolean = true;

  constructor(
    private componentFactoryResolver: ComponentFactoryResolver,
    private appRef: ApplicationRef,
    private injector: Injector
  ) { }

  create(component: any, data?: any, opts?: any) {
    this.data = data;
    this.opts = opts;
    document.body.className = 'no-scroll';

    this.componentRef = this.componentFactoryResolver.resolveComponentFactory(component).create(this.injector);
    this.appRef.attachView(this.componentRef.hostView);
    const domElem = (this.componentRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
    
    this.modalRef = this.componentFactoryResolver.resolveComponentFactory(ModalBaseComponent).create(this.injector);
    this.appRef.attachView(this.modalRef.hostView);
    const modalElem = (this.modalRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
    
    let modalContainer = modalElem.getElementsByClassName('modal')[0];
    modalContainer.appendChild(domElem);
    modalContainer.className = 'modal';
    document.body.appendChild(modalElem);

    setTimeout(() => {
      modalContainer.className = 'modal open';
    }, 1);

    // 5. Wait some time and remove it from the component tree and from the DOM
    /*setTimeout(() => {
        this.appRef.detachView(componentRef.hostView);
        componentRef.destroy();
    }, 3000);*/
  }

  closeCurrentmodal(message?: any) {
    const modalElem = (this.modalRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
    let modalContainer = modalElem.getElementsByClassName('modal')[0];
    modalContainer.className = 'modal close';

    setTimeout(() => {
      this.appRef.detachView(this.modalRef.hostView);
      this.modalRef.destroy();
      this.appRef.detachView(this.componentRef.hostView);
      this.componentRef.destroy();
      document.body.className = '';
      this.closeEmitter.emit(message);
    }, 500);
  }

  afterClosed() {
    return this.closeEmitter;
  }

  disableClose() {
    this.canClose = false;
  }

  enableClose() {
    this.canClose = true;
  }
}
