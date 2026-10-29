import { Component, NgZone, NO_ERRORS_SCHEMA, OnInit } from '@angular/core';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ModalBaseComponent } from '../../components/modal-base/modal-base.component';
import { ModalService } from './modal.service';

@Component({ template: '<span class="initialized">{{ value }}</span>' })
class ReplayProbeComponent implements OnInit {
  value = '';
  ngOnInit(): void { this.value = 'Replay initialized'; }
}

describe('ModalService rendering and cleanup', () => {
  let service: ModalService;
  let zone: NgZone;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ declarations: [ModalBaseComponent, ReplayProbeComponent], schemas: [NO_ERRORS_SCHEMA] }).compileComponents();
    service = TestBed.inject(ModalService);
    zone = TestBed.inject(NgZone);
  });
  afterEach(fakeAsync(() => { service.closeCurrentmodal(); tick(200); }));

  it('renders the title and content immediately when opened outside Angular', () => {
    zone.runOutsideAngular(() => service.create(ReplayProbeComponent, { title: 'Simulation view' }));
    expect(document.querySelector('app-modal-base .title')?.textContent).toBe('Simulation view');
    expect(document.querySelector('.initialized')?.textContent).toBe('Replay initialized');
  });

  it('does not let an old close timer destroy a newly opened modal', fakeAsync(() => {
    service.create(ReplayProbeComponent, { title: 'First' });
    service.closeCurrentmodal();
    service.create(ReplayProbeComponent, { title: 'Second' });
    tick(200);
    expect(document.querySelectorAll('app-modal-base').length).toBe(1);
    expect(document.querySelector('app-modal-base .title')?.textContent).toBe('Second');
  }));

  it('removes content, shell and scroll lock on close', fakeAsync(() => {
    service.create(ReplayProbeComponent, { title: 'Replay' });
    service.closeCurrentmodal();
    tick(200);
    expect(document.querySelector('app-modal-base')).toBeNull();
    expect(document.body.classList.contains('no-scroll')).toBeFalse();
  }));
});
