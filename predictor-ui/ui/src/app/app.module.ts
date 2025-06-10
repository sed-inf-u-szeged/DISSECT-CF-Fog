import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ButtonFabComponent } from './components/buttons/button-fab/button-fab.component';
import { ToolbarComponent } from './components/toolbar/toolbar.component';
import { ScenarioComponent } from './pages/scenario/scenario.component';
import { InputContainerComponent } from './components/input-container/input-container.component';
import { GroupComponent } from './components/group/group.component';
import { ButtonComponent } from './components/buttons/button/button.component';
import { CheckboxContainerComponent } from './components/checkbox-container/checkbox-container.component';
import { ButtonIconComponent } from './components/buttons/button-icon/button-icon.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RequiredSymbolComponent } from './components/required-symbol/required-symbol.component';
import { PredictionChartComponent } from './components/prediction-chart/prediction-chart.component';
import { ErrorChartComponent } from './components/error-chart/error-chart.component';
import { RightPanelComponent } from './components/right-panel/right-panel.component';
import { ProgressBarComponent } from './components/progress-bar/progress-bar.component';
import { LoadingCircleComponent } from './components/loading-circle/loading-circle.component';
import { ModalBaseComponent } from './components/modal-base/modal-base.component';
import { SimulationViewModalComponent } from './components/simulation-view-modal/simulation-view-modal.component';

@NgModule({
  declarations: [
    AppComponent,
    ButtonFabComponent,
    ToolbarComponent,
    ScenarioComponent,
    InputContainerComponent,
    GroupComponent,
    ButtonComponent,
    CheckboxContainerComponent,
    ButtonIconComponent,
    RequiredSymbolComponent,
    PredictionChartComponent,
    ErrorChartComponent,
    RightPanelComponent,
    ProgressBarComponent,
    LoadingCircleComponent,
    ModalBaseComponent,
    SimulationViewModalComponent,
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule,
    ReactiveFormsModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
