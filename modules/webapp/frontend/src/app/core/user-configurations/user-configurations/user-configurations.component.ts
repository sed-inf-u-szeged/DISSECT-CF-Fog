import { AfterViewInit, Component, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { Observable } from 'rxjs';
import { UserConfigurationDetails } from 'src/app/models/server-api/server-api';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import { map } from 'rxjs/operators';

@Component({
  selector: 'app-user-configurations',
  templateUrl: './user-configurations.component.html',
  styleUrls: ['./user-configurations.component.css']
})
export class UserConfigurationsComponent implements AfterViewInit {
  public displayedColumns: string[] = ['time', 'clouds', 'compare', 'results'];
  public userConfigurationsDetails$: Observable<MatTableDataSource<UserConfigurationDetails>>;
  public isConfigResultSelected = false;
  public isSimulationComparisonSelected = false;
  public configId: any;
  @ViewChild(MatSort) public sort: MatSort;
  @ViewChild(MatPaginator) public paginator: MatPaginator;

  constructor(public userConfigurationService: UserConfigurationService) {}

  public ngAfterViewInit(): void {
    this.userConfigurationsDetails$ = this.userConfigurationService.getConfigList().pipe(
      map(details => new MatTableDataSource(details)),
      map(details => {
        if (details && this.sort && this.paginator) {
          details.sort = this.sort;
          details.paginator = this.paginator;
        }
        return details;
      })
    );
  }

  public overviewConfiguration(configId: string): void {
    this.isConfigResultSelected = true;
    this.configId = configId;
  }

  public compareSimulations(configId: any){
    console.log('configId' + configId);
    this.isSimulationComparisonSelected = true;
    this.configId = configId;
  }

  public showTable() {
    this.isConfigResultSelected = false;
    this.isSimulationComparisonSelected = false;
  }
}
