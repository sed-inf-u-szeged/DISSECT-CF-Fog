import { ApplicationRef, ComponentFactoryResolver, EventEmitter, Injectable, Injector, NgZone } from '@angular/core';
import { ModalBaseComponent } from 'src/app/components/modal-base/modal-base.component';

@Injectable({ providedIn: 'root' })
export class ModalService {
  closeEmitter: EventEmitter<any> = new EventEmitter();
  componentRef: any;
  modalRef: any;
  data: any;
  opts: any;
  canClose = true;
  private closeTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private componentFactoryResolver: ComponentFactoryResolver,
    private appRef: ApplicationRef,
    private injector: Injector,
    private zone: NgZone
  ) {}

  create(component: any, data?: any, opts?: any): void {
    this.zone.run(() => {
      this.dispose();
      this.data = data;
      this.opts = opts;
      this.canClose = true;
      document.body.classList.add('no-scroll');
      try {
        this.modalRef = this.componentFactoryResolver.resolveComponentFactory(ModalBaseComponent).create(this.injector);
        this.componentRef = this.componentFactoryResolver.resolveComponentFactory(component).create(this.injector);
        this.appRef.attachView(this.modalRef.hostView);
        this.appRef.attachView(this.componentRef.hostView);
        const modalElement = this.modalRef.location.nativeElement as HTMLElement;
        const contentElement = this.componentRef.location.nativeElement as HTMLElement;
        const container = modalElement.querySelector('.modal')!;
        container.appendChild(contentElement);
        document.body.appendChild(modalElement);
        // Initial render must not depend on a later IPC callback or browser event.
        this.modalRef.changeDetectorRef.detectChanges();
        this.componentRef.changeDetectorRef.detectChanges();
        container.classList.add('open');
      } catch (error) {
        this.dispose();
        throw error;
      }
    });
  }

  closeCurrentmodal(message?: any): void {
    if (!this.modalRef || !this.canClose || this.closeTimer !== undefined) { return; }
    this.zone.run(() => {
      const element = this.modalRef.location.nativeElement as HTMLElement;
      element.querySelector('.modal')!.className = 'modal close';
      // Stop playback immediately; destroy the content while the shell fades out.
      if (this.componentRef) {
        this.appRef.detachView(this.componentRef.hostView);
        this.componentRef.destroy();
        this.componentRef = undefined;
      }
      this.closeTimer = setTimeout(() => {
        this.dispose();
        this.closeEmitter.emit(message);
      }, 150);
    });
  }

  private dispose(): void {
    if (this.closeTimer !== undefined) {
      clearTimeout(this.closeTimer);
      this.closeTimer = undefined;
    }
    for (const ref of [this.componentRef, this.modalRef]) {
      if (ref) { this.appRef.detachView(ref.hostView); ref.destroy(); }
    }
    this.componentRef = undefined;
    this.modalRef = undefined;
    document.body.classList.remove('no-scroll');
  }

  afterClosed() { return this.closeEmitter; }
  disableClose(): void { this.canClose = false; }
  enableClose(): void { this.canClose = true; }
}
