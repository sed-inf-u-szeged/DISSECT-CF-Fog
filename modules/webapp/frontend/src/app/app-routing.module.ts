import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { ConfigurationComponent } from './core/configuration/configuration.component';
import { HomeComponent } from './core/home/home/home.component';
import { UserConfigurationsComponent } from './core/user-configurations/user-configurations/user-configurations.component';
import { UserEntranceComponent } from './core/user-entrance/user-entrance.component';
import { AuthGuard } from './guards/auth.guard';
import { UploadConfigurationComponent } from './core/upload-configuration/upload-configuration.component';
import { AdminConfigurationsComponent } from './core/admin-configurations/admin-configurations.component';
import { AlgorithmUploadComponent } from './core/algorithm-upload/algorithm-upload.component';
import { CustomAlgorithmsComponent } from './core/custom-algorithms/custom-algorithms.component';
import { UserManagementComponent } from './core/user-management/user-management.component';

const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'login', component: UserEntranceComponent },
  { path: 'register', component: UserEntranceComponent },
  { path: 'home', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'configure', component: ConfigurationComponent, canActivate: [AuthGuard] },
  { path: 'user-configurations', component: UserConfigurationsComponent, canActivate: [AuthGuard] },
  { path: 'upload-configuration', component: UploadConfigurationComponent, canActivate: [AuthGuard] },
  { path: 'admin-configurations', component: AdminConfigurationsComponent, canActivate: [AuthGuard] },
  { path: 'algorithm-upload', component: AlgorithmUploadComponent, canActivate: [AuthGuard] },
  { path: 'custom-algorithm', component: CustomAlgorithmsComponent, canActivate: [AuthGuard] },
  { path: 'manage-users', component: UserManagementComponent, canActivate: [AuthGuard] },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {})],
  exports: [RouterModule]
})
export class AppRoutingModule {}
