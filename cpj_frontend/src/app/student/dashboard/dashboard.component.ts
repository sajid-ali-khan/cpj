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
  rollNo = '';
  name = '';

  // Leaderboard Modal State
  showLeaderboard = false;
  leaderboardContestName = '';
  leaderboardData: any[] = [];

  // Contest Details Modal State
  showContestDetails = false;
  selectedContestDetails: any = null;

  // Pagination
  currentPage = 1;
  pageSize = 5;
  totalPages = 1;
  totalElements = 0;

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
    this.apiService.getContests(this.currentPage - 1, this.pageSize).subscribe({
      next: (data) => {
        this.contests = data.content || [];
        this.totalPages = data.totalPages || 1;
        this.totalElements = data.totalElements || 0;
      }
    });
  }

  prevPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
      this.loadData();
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) {
      this.currentPage++;
      this.loadData();
    }
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

  viewContestDetails(contestId: number): void {
    this.apiService.getContest(contestId).subscribe({
      next: (data) => {
        this.selectedContestDetails = data;
        this.showContestDetails = true;
      },
      error: (err) => alert(err.error?.error || 'Failed to retrieve contest details')
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
