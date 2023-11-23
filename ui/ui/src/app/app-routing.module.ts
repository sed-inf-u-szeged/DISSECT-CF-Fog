import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ScenarioComponent } from './pages/scenario/scenario.component';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'scenario',
    pathMatch: 'full'
  },
  {
    path: 'scenario',
    component: ScenarioComponent
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
