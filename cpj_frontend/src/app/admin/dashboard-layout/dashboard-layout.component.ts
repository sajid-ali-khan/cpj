/*
 * File: dashboard-layout.component.ts
 * Purpose: Provides the basic shell layout for admin dashboards with navigation links.
 */

import { Component, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './dashboard-layout.component.html',
  styles: [`
    .admin-container {
      display: flex;
      height: 100vh;
      overflow: hidden;
    }
    .sidebar {
      width: 220px;
      background-color: var(--bg-panel);
      border-right: 1px solid var(--border-color);
      padding: 20px;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }
    .main-content {
      flex-grow: 1;
      padding: 30px;
      box-sizing: border-box;
      background-color: var(--bg-dark);
      overflow-y: auto;
    }
    .nav-list {
      list-style: none;
      padding: 0;
      margin: 20px 0 0 0;
    }
    .nav-list li {
      margin-bottom: 12px;
    }
    .nav-list a {
      display: block;
      padding: 8px 12px;
      color: var(--text-main);
      font-weight: 600;
      border-radius: 4px;
    }
    .nav-list a:hover {
      background-color: #21262d;
      color: var(--text-bright);
      text-decoration: none;
    }
    .nav-list a.active {
      background-color: #21262d;
      color: var(--text-bright);
      border-left: 3px solid var(--color-blue, #58a6ff);
      border-radius: 0 4px 4px 0;
    }
  `]
})
export class DashboardLayoutComponent implements OnInit {
  adminName = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.adminName = this.authService.userName() || 'Administrator';
    if (this.authService.userRole() !== 'admin') {
      this.router.navigate(['/admin-login']);
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
