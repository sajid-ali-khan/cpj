/*
 * File: admin-login.component.ts
 * Purpose: Handles administrator login with username and password credentials.
 */

import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-admin-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './admin-login.component.html',
  styles: [`
    .login-container {
      max-width: 400px;
      margin: 100px auto;
      border: 1px solid var(--border-color);
      background-color: var(--bg-panel);
      padding: 30px;
      border-radius: 4px;
    }
    .form-group {
      margin-bottom: 20px;
    }
    .form-group label {
      display: block;
      margin-bottom: 8px;
      font-weight: 600;
    }
    .btn-submit {
      width: 100%;
      padding: 10px;
    }
  `]
})
export class AdminLoginComponent {
  username = '';
  password = '';
  errorMessage = '';
  loading = false;

  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private router: Router
  ) {}

  onSubmit(): void {
    if (!this.username.trim() || !this.password) {
      this.errorMessage = 'Username and password are required';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.apiService.loginAdmin(this.username.trim(), this.password).subscribe({
      next: (response) => {
        this.authService.setAdminSession(
          response.user.username,
          response.user.name,
          response.token
        );
        this.router.navigate(['/admin/overview']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.error || err.message || 'Failed to authenticate';
      }
    });
  }
}
