import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource } from '@angular/material/table';
import { adminConfiguration } from 'src/app/models/admin-configuration';
import { AdminConfigurationService } from 'src/app/services/admin-configuration/admin-configuration.service';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';

interface cellValue {
  id: string;
  fileType: string;
}

@Component({
  selector: 'app-admin-configurations',
  templateUrl: './admin-configurations.component.html',
  styleUrls: ['./admin-configurations.component.css']
})
export class AdminConfigurationsComponent implements AfterViewInit, OnInit {
  displayedColumns: string[] = ['id', 'shortDescription', 'appliancesId', 'devicesId', 'instancesId'];
  adminConfigurations: adminConfiguration[] = [];
  dataSource = new MatTableDataSource<adminConfiguration>(this.adminConfigurations);

  constructor(
    private adminConfigurationService: AdminConfigurationService,
    private userConfigurationService: UserConfigurationService
  ) {}

  ngOnInit(): void {
    this.adminConfigurationService.getAdminConfigurations().subscribe(
      data => {
        this.adminConfigurations = data;
        this.dataSource.data = this.adminConfigurations;
      },
      error => {
        console.log(error);
      }
    );
  }

  @ViewChild(MatPaginator) paginator: MatPaginator;
  ngAfterViewInit(): void {
    if (this.dataSource) {
      this.dataSource.paginator = this.paginator;
    }
  }

  /**
   * Sends the config id and type to the backend
   * @param value an object which containts the clicked file id and it's description
   */
  downloadFile(value: cellValue) {
    this.userConfigurationService.downloadFileMongo(value.id, value.fileType);
  }
}
