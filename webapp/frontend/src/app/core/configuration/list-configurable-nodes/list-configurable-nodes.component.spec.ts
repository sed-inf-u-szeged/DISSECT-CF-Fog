import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ListConfigurableNodesComponent } from './list-configurable-nodes.component';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ResourceSelectionService } from 'src/app/services/configuration/resource-selection/resource-selection.service';

describe('ListConfigurableNodesComponent', () => {
  let component: ListConfigurableNodesComponent;
  let fixture: ComponentFixture<ListConfigurableNodesComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ListConfigurableNodesComponent],
      imports: [HttpClientTestingModule],
      providers: [{ provide: ResourceSelectionService, useValue: {} }],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ListConfigurableNodesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
