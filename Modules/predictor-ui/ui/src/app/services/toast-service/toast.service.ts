import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private toasts: any[] = [];

  constructor() { }

  create(message: string, duration: number) {
    let toast = document.createElement('div');
    toast.id = 'toast-' + this.toasts.length + Date.now();
    toast.innerText = message;
    toast.className = 'toast';

    this.toasts.push({ id: toast.id, component: toast });
    document.body.appendChild(toast);
    setTimeout(() => {
      toast.className = 'toast up';
      setTimeout(() => {
        this.removeToast(toast.id);
      }, 100);
    }, duration);
  }

  private removeToast(id: string) {
    document.body.removeChild(document.getElementById(id) as any);
    this.toasts = this.toasts.filter(toast => {
      return toast.id !== id;
    });
  }
}
