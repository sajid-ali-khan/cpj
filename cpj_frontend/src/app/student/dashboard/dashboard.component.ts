/*
 * File: dashboard.component.ts
 * Purpose: Student dashboard listing contests with registration limits, entries, and real-time leaderboards.
 */

import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-student-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  contests: any[] = [];
  registrations: any[] = [];
  rollNo = '';
  name = '';

  // Leaderboard Modal State
  showLeaderboard = false;
  leaderboardContestName = '';
  leaderboardData: any[] = [];

  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.rollNo = this.authService.rollNumber() || '';
    this.name = this.authService.userName() || '';
    if (!this.rollNo) {
      this.router.navigate(['/student-login']);
      return;
    }
    this.loadData();
  }

  loadData(): void {
    this.apiService.getContests().subscribe({
      next: (data) => this.contests = data
    });

    this.apiService.getStudentRegistrations(this.rollNo).subscribe({
      next: (data) => this.registrations = data
    });
  }

  isRegistered(contestId: number): boolean {
    return this.registrations.some(r => Number(r.contestId) === Number(contestId));
  }

  getRegistrationStatus(contestId: number): string {
    const reg = this.registrations.find(r => Number(r.contestId) === Number(contestId));
    return reg ? reg.status : '';
  }

  register(contestId: number): void {
    this.apiService.registerForContest(contestId).subscribe({
      next: () => this.loadData(),
      error: (err) => alert(err.error?.error || 'Registration failed')
    });
  }

  viewLeaderboard(contestId: number, contestName: string): void {
    this.leaderboardContestName = contestName;
    this.apiService.getLeaderboard(contestId).subscribe({
      next: (data) => {
        this.leaderboardData = data;
        this.showLeaderboard = true;
      },
      error: (err) => alert(err.error?.error || 'Failed to retrieve leaderboard')
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
