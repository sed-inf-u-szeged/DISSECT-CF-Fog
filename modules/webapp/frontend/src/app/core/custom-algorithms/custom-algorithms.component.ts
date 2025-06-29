import { Component, OnInit, ViewChild, AfterViewInit } from '@angular/core';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatSort, Sort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { AdminConfigurationService } from 'src/app/services/admin-configuration/admin-configuration.service';
import { AlgorithmUploadConfigurationService } from 'src/app/services/algorithm-upload-configuration/algorithm-upload-configuration.service';

@Component({
  selector: 'app-custom-algorithms',
  templateUrl: './custom-algorithms.component.html',
  styleUrls: ['./custom-algorithms.component.css']
})
export class CustomAlgorithmsComponent implements OnInit, AfterViewInit {

  dataSource = new MatTableDataSource<any>([]);
  displayedColumns: string[] = [
   'nickname', 'createdDate','totalEnergyConsumptionOfNodesInWatt', 'totalEnergyConsumptionOfDevicesInWatt', 
    'costAWS', 'costIBM', 'costAzure', 'costTotal'
  ];
  orderOptions = [
    { value: 'totalEnergyConsumptionOfNodesInWatt', viewValue: 'Total Energy Consumption of Nodes (W)' },
    { value: 'totalEnergyConsumptionOfDevicesInWatt', viewValue: 'Total Energy Consumption of Devices (W)' },
    { value: 'costAWS', viewValue: 'Cost (AWS)' },
    { value: 'costIBM', viewValue: 'Cost (IBM)' },
    { value: 'costAzure', viewValue: 'Cost (Azure)' },
    { value: 'costTotal', viewValue: 'Total Cost in Euro' }
  ];
  selectedOrder: string = 'totalEnergyConsumptionOfNodesInWatt';
  options: any = [];
  filteredOptions: string[];

  @ViewChild(MatSort) sort: MatSort;

  constructor(
    private algorithmUploadConfigurationService: AlgorithmUploadConfigurationService,
    private adminConfigurationService: AdminConfigurationService
  ) {
    this.filteredOptions = this.options.slice()
  }

  ngOnInit(): void {
    this.adminConfigurationService.getAdminConfigurations().subscribe(
      data => {
        this.options = data.map(obj => obj._id);
        this.filterOptions('');
      },
      error => {
        console.log(error);
      }
    );
  }

  //angular material table setup
  ngAfterViewInit() {
    this.dataSource.sortingDataAccessor = (item, property) => {
      switch (property) {
        case 'costAWS':
          return item.simulatorJobResult.cost.AWS;
        case 'costIBM':
          return item.simulatorJobResult.cost.IBM;
        case 'costAzure':
          return item.simulatorJobResult.cost.azure;
        case 'costTotal':
          return item.simulatorJobResult.cost.totalCostInEuro;
        case 'totalEnergyConsumptionOfNodesInWatt':
          return item.simulatorJobResult.architecture.totalEnergyConsumptionOfNodesInWatt;
        case 'totalEnergyConsumptionOfDevicesInWatt':
          return item.simulatorJobResult.architecture.totalEnergyConsumptionOfDevicesInWatt;
        default:
          return item[property];
      }
    };
  }

  getData(adminConfigId) {
    this.algorithmUploadConfigurationService.getCustomConfigurations(adminConfigId).subscribe(
      data => {
        this.dataSource.data = data;
        this.applySort();
      },
      error => {
        console.log(error);
      }
    )
  }

  //sorting with selected date 
  applySort() {
    if (this.sort) {
      this.dataSource.sort = this.sort;
      this.dataSource.sort.active = this.selectedOrder;
      this.dataSource.sort.direction = 'asc';
      this.dataSource.sort.sortChange.emit();
    }
  }

  onOrderChange() {
    this.applySort();
  }

  filterOptions(value: string) {
    this.filteredOptions = this.options.filter(option => option.toLowerCase().includes(value.toLowerCase()));
  }

  onOptionSelected(event: MatAutocompleteSelectedEvent) {
    this.getData(event.option.value);
  }
}
