import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ScenarioComponent } from './scenario.component';

describe('ScenarioComponent', () => {
  let component: ScenarioComponent;
  let fixture: ComponentFixture<ScenarioComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [ScenarioComponent]
    });
    fixture = TestBed.createComponent(ScenarioComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
