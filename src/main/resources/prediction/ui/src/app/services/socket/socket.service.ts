import {EventEmitter, Injectable} from '@angular/core';
import {SocketMessage} from "../../shared/SocketMessage";
import {ElectronService} from "../electron/electron.service";
import {map, Observable} from "rxjs";

@Injectable({
  providedIn: 'root'
})
export class SocketService {
  connected: boolean = false;

  constructor(private electron: ElectronService) {

  }

  connect() {
    if (this.connected) {
      return;
    }

    this.connected = true;
    this.electron.send("socket-connect");
  }

  close() {
    this.connected = false;
    this.electron.send("socket-close");
  }

  send(message: SocketMessage) {
    this.electron.send("socket-send", message);
  }

  receive(): Observable<SocketMessage> {
    return this.electron.receive("socket-response").pipe(
      map(res => res.result)
    );
  }
}
