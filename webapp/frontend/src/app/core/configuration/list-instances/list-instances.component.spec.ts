import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListInstancesComponent } from './list-instances.component';

describe('ListInstancesComponent', () => {
  let component: ListInstancesComponent;
  let fixture: ComponentFixture<ListInstancesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ListInstancesComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ListInstancesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
