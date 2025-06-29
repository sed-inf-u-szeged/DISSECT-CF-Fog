import {Injectable} from '@angular/core';
import {ServerSideConfigurationObject} from '../../../models/configuration';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Observable} from 'rxjs';
import {shareReplay} from 'rxjs/operators';
import {TokenStorageService} from '../../token-storage/token-storage.service';
import {
  ConfigurationFile,
  ConfigurationResult,
  SERVER_URL,
  UserConfigurationDetails
} from 'src/app/models/server-api/server-api';
import {saveAs} from 'file-saver';
import {parseConfigurationObjectToXml} from 'src/app/core/util/configuration-util';

const httpOptions = {
  headers: new HttpHeaders({ 'Content-Type': 'application/json' })
};

const HTML_FILES = [
  'timeline',
  'devicesenergy',
  'nodesenergy'
];

/**
 * API calls for running configuration or get configurations.
 */
@Injectable()
export class UserConfigurationService {
  public configObservable: Observable<ConfigurationResult>;

  constructor(private http: HttpClient, public tokenService: TokenStorageService) {}

  /**
   * Send the configuration to the server and sets the returned observable as the value of the configObservable
   * @param objects An array of ServerSideConfigurationObjects where each config defines a simulation.
   */
  public sendConfiguration(objects: ServerSideConfigurationObject[]): void {
    const xmlBaseConfigs = [];
    for (const object of objects) {
      xmlBaseConfigs.push(parseConfigurationObjectToXml(object, this.tokenService.getUser().email));
    }
    console.log(xmlBaseConfigs);
    this.configObservable = this.http
      .post<ConfigurationResult>(SERVER_URL + 'configuration', JSON.stringify(xmlBaseConfigs), httpOptions)
      .pipe(shareReplay(1));
  }

  /**
   * Sends configuration to the server
   * @param configs array of strings with the content of the uploaded files
   * @param shortDescription A short description about the config itself by the admin
   */
  public sendAdminConfiguration(configs: string[], shortDescription: string): void {
    const config = {
      userId: this.tokenService.getUser().id,
      configs: configs,
      shortDescription: shortDescription
    };
    this.http.post<any>(
    SERVER_URL + 'configuration/adminConfiguration', config, httpOptions).toPromise()
  }

  /**
   * Returns the list of the configurations of the current user
   * @returns An observable which returns an UserConfigurationDetails array
   */
  public getConfigList(): Observable<UserConfigurationDetails[]> {
    const data = {
      id: this.tokenService.getUser().id
    };
    return this.http.post<UserConfigurationDetails[]>(SERVER_URL + 'user/configuration/list', data, httpOptions);
  }

  /**
   * Returns the requested configuration with the given id.
   * @param configId The id of the configuration
   * @returns an observable which will complete with the requested configuration
   */
  public getConfig(configId: any) {
    return this.http.post<ConfigurationResult>(SERVER_URL + 'user/configuration', {_id: configId});
  }

  /**
   * Downloads the specified file to the device of the user
   * @param id The id of the file in the MongoDB
   * @param file The type of the file
   */
  public downloadFileMongo(id, file): void {
    const data = { _id: id };
    this.http
      .post(SERVER_URL + 'user/configurations/download/' + file, data, {
        ...httpOptions.headers,
        responseType: 'blob'
      })
      .toPromise()
      .then(blob => {
        saveAs(blob, HTML_FILES.includes(file) ? `${file}.html` : `${file}.xml`);
      })
      .catch(err => console.error('download error = ', err));
  }
}
