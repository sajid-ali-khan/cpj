/*
 * File: student-login.component.ts
 * Purpose: Handles student login using their roll number and sets up session states.
 */

import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-student-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './student-login.component.html',
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
export class StudentLoginComponent {
  rollNumber = '';
  otp = '';
  otpSent = false;
  successMessage = '';
  errorMessage = '';
  loading = false;

  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private router: Router
  ) {}

  onRequestOtp(): void {
    if (!this.rollNumber.trim()) {
      this.errorMessage = 'Roll Number is required';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.apiService.sendOtp(this.rollNumber.trim()).subscribe({
      next: (response) => {
        this.loading = false;
        this.otpSent = true;
        this.successMessage = response.message || 'OTP sent successfully to your registered email.';
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.error || err.message || 'Failed to send OTP';
      }
    });
  }

  onVerifyOtp(): void {
    if (!this.otp.trim()) {
      this.errorMessage = 'OTP is required';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.apiService.verifyOtp(this.rollNumber.trim(), this.otp.trim()).subscribe({
      next: (response) => {
        this.authService.setStudentSession(
          response.user.rollNumber,
          response.user.name,
          response.token
        );
        this.router.navigate(['/student/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.error || err.message || 'Verification failed';
      }
    });
  }

  resetFlow(): void {
    this.otpSent = false;
    this.otp = '';
    this.errorMessage = '';
    this.successMessage = '';
  }
}
