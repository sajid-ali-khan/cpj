/*
 * File: overview.component.ts
 * Purpose: Admin dashboard overview showing aggregate platform statistics, active contests, and real-time contest leaderboards.
 */
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-admin-overview',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.css'
})
export class OverviewComponent implements OnInit {
  contestsCount = 0; studentsCount = 0; problemsCount = 0; contests: any[] = []; loading = false;
  pageSize = 5; currentPage = 1;
  showLeaderboard = false; leaderboardContestName = ''; leaderboardData: any[] = [];
  currentContestId = 0; leaderboardSearchQuery = '';

  constructor(private apiService: ApiService) {}

  ngOnInit(): void { this.loadData(); }

  get paginatedContests(): any[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.contests.slice(start, start + this.pageSize);
  }

  get totalPages(): number { return Math.ceil(this.contests.length / this.pageSize) || 1; }
  prevPage(): void { if (this.currentPage > 1) this.currentPage--; }
  nextPage(): void { if (this.currentPage < this.totalPages) this.currentPage++; }

  loadData(): void {
    this.loading = true;
    this.apiService.getAdminContests().subscribe({
      next: (data) => { this.contests = data; this.contestsCount = data.length; this.currentPage = 1; this.loading = false; },
      error: () => { this.loading = false; }
    });
    this.apiService.getAdminUsers().subscribe({
      next: (data) => { this.studentsCount = data.content ? data.content.length : (data.length || 0); }
    });
    this.apiService.getAdminProblems().subscribe({
      next: (data) => { this.problemsCount = data.length || 0; }
    });
  }

  endContest(contestId: number): void {
    if (confirm('Are you sure you want to end this contest?')) {
      this.apiService.endContest(contestId).subscribe({
        next: () => { alert('Contest ended successfully.'); this.loadData(); },
        error: (err) => alert(err.error?.error || 'Failed to end contest')
      });
    }
  }

  get filteredLeaderboard(): any[] {
    if (!this.leaderboardSearchQuery) return this.leaderboardData;
    const q = this.leaderboardSearchQuery.toLowerCase().trim();
    return this.leaderboardData.filter(row =>
      (row.name?.toLowerCase() || '').includes(q) ||
      (row.rollNo?.toLowerCase() || '').includes(q)
    );
  }

  viewLeaderboard(contestId: number, contestName: string): void {
    this.currentContestId = contestId;
    this.leaderboardContestName = contestName;
    this.leaderboardSearchQuery = '';
    this.loadLeaderboard(contestId);
  }

  loadLeaderboard(contestId: number): void {
    this.apiService.getLeaderboard(contestId).subscribe({
      next: (data) => { this.leaderboardData = data; this.showLeaderboard = true; },
      error: (err) => alert(err.error?.error || 'Failed to load leaderboard')
    });
  }

  resetStudentViolations(rollNo: string): void {
    this.apiService.resetStudentViolations(rollNo).subscribe({
      next: () => {
        alert('Violations reset successfully.');
        if (this.currentContestId) this.loadLeaderboard(this.currentContestId);
      },
      error: (err) => alert(err.error?.error || 'Failed to reset violations')
    });
  }
}
