import { Component } from '@angular/core';
import { UserConfigurationService } from 'src/app/services/configuration/user-configuration/user-configuration.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { FormControl } from '@angular/forms';

@Component({
  selector: 'app-upload-configuration',
  templateUrl: './upload-configuration.component.html',
  styleUrls: ['./upload-configuration.component.css']
})
export class UploadConfigurationComponent {
  files: File[] = [null, null, null];
  fileContents: string[] = ['', '', ''];
  fileNames: string[] = ['', '', ''];
  shortDescription = new FormControl('');
  t;

  constructor(
    public userConfigurationService: UserConfigurationService,
    private snackBar: MatSnackBar,
    private router: Router
  ) {}

  //Set the button text to the name of the uploaded xml file
  onFileChange(event: any, index: number): void {
    const file = event.target.files[0];
    if (file) {
      this.files[index] = file;
      this.fileNames[index] = file.name;
    } else {
      this.files[index] = null;
      this.fileNames[index] = '';
    }
  }

  //check if all 3 files are uploaded
  areAllFilesUploaded(): boolean {
    return this.files.every(file => file !== null);
  }

  //Converts the content of the file into an array of strings
  //index[0] appliences, index[1] devices, index[2] instances
  async convertFilesToStrings(): Promise<void> {
    try {
      const readFileAsync = (file: File): Promise<string> => {
        return new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = event => {
            const fileContent = event.target?.result as string;
            resolve(fileContent);
          };
          reader.onerror = event => {
            reject(event.target?.error);
          };
          reader.readAsText(file);
        });
      };

      const fileContents: string[] = [];
      for (const file of this.files) {
        if (file) {
          try {
            const fileContent = await readFileAsync(file);
            fileContents.push(fileContent);
          } catch (error) {
            console.error('Error reading in file:', error);
            // TODO file read error handle
          }
        }
      }

      //Sends the file contents to backend
      this.userConfigurationService.sendAdminConfiguration(fileContents, this.shortDescription.value);
      this.snackBar.open('File uploaded successfully!', 'Close', {
        duration: 3000
      });
      this.router.navigateByUrl('/admin-configurations').then(() => {
        window.location.reload();
      });
    } catch (error) {
      console.error('Something went wrong:', error);
    }
  }
}
