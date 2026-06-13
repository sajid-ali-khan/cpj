/*
 * File: auth.service.ts
 * Purpose: Manages user session state, local storage tokens, roles, and authentication checks.
 */

import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private tokenKey = 'cpj_token';
  private rollKey = 'cpj_roll';
  private nameKey = 'cpj_name';
  private roleKey = 'cpj_role';

  userRole = signal<string | null>(localStorage.getItem(this.roleKey));
  userName = signal<string | null>(localStorage.getItem(this.nameKey));
  rollNumber = signal<string | null>(localStorage.getItem(this.rollKey));

  setStudentSession(rollNumber: string, name: string, token: string): void {
    localStorage.setItem(this.rollKey, rollNumber);
    localStorage.setItem(this.nameKey, name);
    localStorage.setItem(this.tokenKey, token);
    localStorage.setItem(this.roleKey, 'student');
    this.rollNumber.set(rollNumber);
    this.userName.set(name);
    this.userRole.set('student');
  }

  setAdminSession(username: string, name: string, token: string): void {
    localStorage.setItem(this.rollKey, username);
    localStorage.setItem(this.nameKey, name);
    localStorage.setItem(this.tokenKey, token);
    localStorage.setItem(this.roleKey, 'admin');
    this.rollNumber.set(username);
    this.userName.set(name);
    this.userRole.set('admin');
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  logout(): void {
    localStorage.clear();
    this.userRole.set(null);
    this.userName.set(null);
    this.rollNumber.set(null);
  }
}
