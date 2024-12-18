import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AngularMaterialModule } from './angular-material/angular-material.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ConfigurationComponent } from './core/configuration/configuration.component';
import { FlexLayoutModule } from '@angular/flex-layout';
import { StepBackDialogComponent } from './core/configuration/step-back-dialog/step-back-dialog.component';
import { ConnectionComponent } from './core/configuration/connection/connection.component';
import { ListStationsComponent } from './core/configuration/list-stations/list-stations.component';
import { ConfigurableStationComponent } from './core/configuration/list-stations/configurable-station/configurable-station.component';
import { NodeQuantityFormComponent } from './core/configuration/node-quantity-form/node-quantity-form.component';
import { ConfigurableNodeComponent } from './core/configuration/list-configurable-nodes/configurable-node/configurable-node.component';
import { ListConfigurableNodesComponent } from './core/configuration/list-configurable-nodes/list-configurable-nodes.component';
import { ApplicationsDialogComponent } from './core/configuration/list-configurable-nodes/configurable-node/applications-dialog/applications-dialog.component';
import { ApplicationCardComponent } from './core/configuration/list-configurable-nodes/configurable-node/applications-dialog/application-card/application-card.component';
import { HttpClientModule } from '@angular/common/http';
import { RunScriptsDirective } from './directives/run-scripts.directive';
import { SafeHtmlPipe } from './pipes/safe-html.pipe';
import { authInterceptorProviders } from './interceptors/auth.interceptor';
import { UserConfigurationService } from './services/configuration/user-configuration/user-configuration.service';
import { ConfigurationEndComponent } from './core/configuration/configuration-end/configuration-end.component';
import { InfoPanelComponent } from './core/info-panel/info-panel.component';
import { SidenavItemsComponent } from './core/navigation/sidenav-items/sidenav-items.component';
import { ToolbarComponent } from './core/navigation/toolbar/toolbar.component';
import { HomeComponent } from './core/home/home/home.component';
import { UserEntranceComponent } from './core/user-entrance/user-entrance.component';
import { UserConfigurationsComponent } from './core/user-configurations/user-configurations/user-configurations.component';
import { ConfigurationOverviewComponent } from './core/user-configurations/configuration-overview/configuration-overview.component';
import { ConfigurationResultComponent } from './core/util/configuration-result/configuration-result.component';
import { ResourceSelectionService } from './services/configuration/resource-selection/resource-selection.service';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { ListInstancesComponent } from './core/configuration/list-instances/list-instances.component';
import { ConfigurableInstanceComponent } from './core/configuration/list-instances/configurable-instance/configurable-instance.component';
import { SimulationComparisonComponent } from './core/util/simulation-comparison/simulation-comparison.component';
import { UploadConfigurationComponent } from './core/upload-configuration/upload-configuration.component';
import { AdminConfigurationsComponent } from './core/admin-configurations/admin-configurations.component';
import { AlgorithmUploadComponent } from './core/algorithm-upload/algorithm-upload.component';
import { CustomAlgorithmsComponent } from './core/custom-algorithms/custom-algorithms.component';

import { LeafletModule } from '@asymmetrik/ngx-leaflet';
import { CodemirrorModule } from '@ctrl/ngx-codemirror';
import { MatSnackBarModule } from '@angular/material/snack-bar';

@NgModule({
  declarations: [
    AppComponent,
    NodeQuantityFormComponent,
    ConfigurationComponent,
    ListConfigurableNodesComponent,
    ConfigurableNodeComponent,
    ApplicationsDialogComponent,
    ApplicationCardComponent,
    StepBackDialogComponent,
    ConnectionComponent,
    ListStationsComponent,
    ConfigurableStationComponent,
    InfoPanelComponent,
    SidenavItemsComponent,
    ToolbarComponent,
    HomeComponent,
    ConfigurationEndComponent,
    RunScriptsDirective,
    SafeHtmlPipe,
    UserEntranceComponent,
    UserConfigurationsComponent,
    ConfigurationOverviewComponent,
    ConfigurationResultComponent,
    ListInstancesComponent,
    ConfigurableInstanceComponent,
    SimulationComparisonComponent,
    UploadConfigurationComponent,
    AdminConfigurationsComponent,
    AlgorithmUploadComponent,
    CustomAlgorithmsComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    AngularMaterialModule,
    FormsModule,
    ReactiveFormsModule,
    FlexLayoutModule,
    HttpClientModule,
    LeafletModule,
    MatSnackBarModule,
    CodemirrorModule
  ],
  providers: [
    UserConfigurationService,
    ResourceSelectionService,
    authInterceptorProviders,
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { floatLabel: 'always' } }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
