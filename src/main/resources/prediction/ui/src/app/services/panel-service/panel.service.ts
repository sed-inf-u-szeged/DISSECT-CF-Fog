import { ApplicationRef, ComponentFactoryResolver, EmbeddedViewRef, EventEmitter, Injectable, Injector } from '@angular/core';
import { RightPanelComponent } from 'src/app/components/right-panel/right-panel.component';

@Injectable({
  providedIn: 'root'
})
export class PanelService {
  closeEmitter: EventEmitter<any> = new EventEmitter();
  componentRef: any;
  panelRef: any;
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
    
    this.panelRef = this.componentFactoryResolver.resolveComponentFactory(RightPanelComponent).create(this.injector);
    this.appRef.attachView(this.panelRef.hostView);
    const panelElem = (this.panelRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
    
    let panelContainer = panelElem.getElementsByClassName('panel')[0];
    panelContainer.appendChild(domElem);
    panelContainer.className = 'panel';
    document.body.appendChild(panelElem);

    setTimeout(() => {
      panelContainer.className = 'panel open';
    }, 1);

    // 5. Wait some time and remove it from the component tree and from the DOM
    /*setTimeout(() => {
        this.appRef.detachView(componentRef.hostView);
        componentRef.destroy();
    }, 3000);*/
  }

  closeCurrentPanel(message?: any) {
    const panelElem = (this.panelRef.hostView as EmbeddedViewRef<any>).rootNodes[0] as HTMLElement;
    let panelContainer = panelElem.getElementsByClassName('panel')[0];
    panelContainer.className = 'panel close';

    setTimeout(() => {
      this.appRef.detachView(this.panelRef.hostView);
      this.panelRef.destroy();
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
