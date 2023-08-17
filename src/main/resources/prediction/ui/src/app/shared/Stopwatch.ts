export class Stopwatch {
    private time: number;
    private interval: any;
    private tickCallback: Function | undefined;
    private paused: boolean;

    constructor() {
        this.time = 0;
        this.paused = false;
    }

    start(tickCallback?: Function) {
        this.time = 0;
        this.tickCallback = tickCallback;
        this.interval = setInterval(() => {
            if (!this.paused) {
                if (this.tickCallback ) {
                    this.tickCallback(this.time)
                }
                this.time++;
            }
        }, 1000);
    }

    pause() {
        this.paused = true;
    }

    resume() {
        this.paused = false;
    }

    reset() {
        clearInterval(this.interval);
        this.time = 0;
        this.interval = null;
    }

  stop() {
    clearInterval(this.interval);
    this.interval = null;
  }

    getFormatedTime() {
        const seconds = this.time % 60 < 10 ? `0${this.time % 60}` : this.time % 60;
        const mintues = Math.floor(this.time / 60) % 60 < 10 ? `0${Math.floor(this.time / 60) % 60}` : Math.floor(this.time / 60) % 60;
        const hours = Math.floor(Math.floor(this.time / 60) / 60) % 60 < 10 ? `0${Math.floor(Math.floor(this.time / 60) / 60) % 60}` : Math.floor(Math.floor(this.time / 60) / 60) % 60;
        return `${hours}:${mintues}:${seconds}`;
    }
}
