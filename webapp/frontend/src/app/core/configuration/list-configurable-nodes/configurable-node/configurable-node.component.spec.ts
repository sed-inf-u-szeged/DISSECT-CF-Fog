import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { UntypedFormBuilder, UntypedFormControl } from '@angular/forms';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AngularMaterialModule } from 'src/app/angular-material/angular-material.module';
import { ComputingNode } from 'src/app/models/computing-node';
import { QuantityCounterService } from 'src/app/services/configuration/quantity-counter/quantity-counter.service';
import { PanelService } from 'src/app/services/panel/panel.service';
import { WindowSizeService } from 'src/app/services/window-size/window-size.service';

import { ConfigurableNodeComponent } from './configurable-node.component';

const mockResourceSelectionService = {
  getUndividedClouds() {
    return 0;
  },
  getUndividedFogs() {
    return 0;
  }
};

describe('ConfigurableNodeComponent', () => {
  let component: ConfigurableNodeComponent;
  let fixture: ComponentFixture<ConfigurableNodeComponent>;
  let formBuilder: UntypedFormBuilder;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ConfigurableNodeComponent],
      imports: [BrowserAnimationsModule, AngularMaterialModule],
      providers: [
        UntypedFormBuilder,
        { provide: WindowSizeService, useValue: {} },
        { provide: PanelService, useValue: {} },
        { provide: QuantityCounterService, useValue: mockResourceSelectionService }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigurableNodeComponent);
    component = fixture.componentInstance;
    formBuilder = TestBed.inject(UntypedFormBuilder);
    component.nodeCardForm = formBuilder.group({
      numOfApplications: new UntypedFormControl({
        value: ['mock'],
        disabled: true
      })
    });
    component.node = {} as ComputingNode;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
