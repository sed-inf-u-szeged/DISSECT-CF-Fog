import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-user-management',
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.css'],
  standalone: true,
  imports: [DatePipe, FormsModule, ReactiveFormsModule]
})
export class UserManagementComponent implements OnInit {
  users: any[] = [];
  filteredUsers: any[] = [];
  searchForm: FormGroup;

  constructor(private http: HttpClient, private fb: FormBuilder) {
    this.searchForm = this.fb.group({
      search: ['']
    });
  }

  ngOnInit(): void {
    this.fetchUsers();
    this.searchForm.get('search').valueChanges.subscribe(value => this.filterUsers(value));
  }

  fetchUsers(): void {
    this.http.get('/api/users').subscribe((response: any) => {
      this.users = response.users;
      this.filteredUsers = this.users;
    });
  }

  filterUsers(searchTerm: string): void {
    this.filteredUsers = this.users.filter(
      user =>
        user.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
        user.university.toLowerCase().includes(searchTerm.toLowerCase())
    );
  }

  updateUser(user: any): void {
    // Implement update logic here
    console.log('Updating user:', user);
  }
}
