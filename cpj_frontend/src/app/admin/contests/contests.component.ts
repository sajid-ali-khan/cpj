/*
 * File: contests.component.ts
 * Purpose: Invigilator contest manager. Handles contest scheduling and maps questions with custom weights and display orders.
 */

import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-admin-contests',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './contests.component.html',
  styleUrl: './contests.component.css'
})
export class ContestsComponent implements OnInit, OnDestroy {
  contests: any[] = [];
  problems: any[] = [];
  showAddModal = false;

  // Contest Detail Modal State
  showDetailModal = false;
  selectedContestDetail: any = null;

  // Contest Detail Page State
  leaderboardData: any[] = [];
  eligibleStudents: any[] = [];
  studentListInput = '';
  submittingEligible = false;

  // Pagination
  pageSize = 10;
  currentPage = 1;

  // New Contest Form
  title = '';
  description = '';
  startTime = '';
  durationMins = 120;
  selectedProblemIds: number[] = [];
  problemPoints: Record<number, number> = {};
  problemOrder: Record<number, number> = {};

  private sseSource: EventSource | null = null;

  constructor(private apiService: ApiService, private authService: AuthService) {}

  ngOnInit(): void {
    this.loadData();
  }

  ngOnDestroy(): void {
    if (this.sseSource) {
      this.sseSource.close();
      this.sseSource = null;
    }
  }

  get paginatedContests(): any[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.contests.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.ceil(this.contests.length / this.pageSize) || 1;
  }

  prevPage(): void {
    if (this.currentPage > 1) this.currentPage--;
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) this.currentPage++;
  }

  loadData(): void {
    this.apiService.getAdminContests().subscribe({
      next: (data) => {
        this.contests = data;
        this.currentPage = 1;
      }
    });

    this.apiService.getAdminProblems().subscribe({
      next: (data) => this.problems = data
    });
  }

  toggleProblem(problemId: number): void {
    const idx = this.selectedProblemIds.indexOf(problemId);
    if (idx > -1) {
      this.selectedProblemIds.splice(idx, 1);
      delete this.problemPoints[problemId];
      delete this.problemOrder[problemId];
    } else {
      this.selectedProblemIds.push(problemId);
      this.problemPoints[problemId] = 100;
      this.problemOrder[problemId] = this.selectedProblemIds.length;
    }
  }

  getProblemTitle(id: number): string {
    const p = this.problems.find(item => item.id === id);
    return p ? p.title : '';
  }

  saveContest(): void {
    if (!this.title || !this.startTime || this.selectedProblemIds.length === 0) {
      alert('Please fill all required fields and select at least one problem.');
      return;
    }

    const contestProblems = this.selectedProblemIds.map((id) => ({
      problemId: id,
      points: Number(this.problemPoints[id] || 100),
      displayOrder: Number(this.problemOrder[id] || 1)
    }));

    const body = {
      title: this.title.trim(),
      description: this.description.trim(),
      startTime: new Date(this.startTime).toISOString(),
      durationMins: this.durationMins,
      problems: contestProblems
    };

    this.apiService.createContest(body).subscribe({
      next: () => {
        this.loadData();
        this.showAddModal = false;
        this.resetForm();
        alert('Contest created successfully.');
      },
      error: (err) => alert(err.error?.error || 'Failed to create contest')
    });
  }

  viewContestDetail(contestId: number): void {
    this.apiService.getAdminContest(contestId).subscribe({
      next: (data) => {
        this.selectedContestDetail = data;
        this.loadLeaderboard(contestId);
        this.loadEligibleStudents(contestId);
        this.connectSse(contestId);
      },
      error: (err) => alert(err.error?.error || 'Failed to retrieve contest details')
    });
  }

  connectSse(contestId: number): void {
    if (this.sseSource) {
      this.sseSource.close();
      this.sseSource = null;
    }

    const token = this.authService.getToken();
    if (!token) return;

    const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
    this.sseSource = new EventSource(`http://${host}:8080/api/events?rollNo=${encodeURIComponent(token)}`);

    this.sseSource.addEventListener('leaderboard', (event: any) => {
      try {
        const data = JSON.parse(event.data);
        if (data && data.length > 0 && data[0].contestId === contestId) {
          this.leaderboardData = data;
        }
      } catch (err) {
        console.error('Error parsing leaderboard SSE in admin:', err);
      }
    });

    this.sseSource.onerror = (err) => {
      console.error('Admin SSE connection error, closing...', err);
      this.sseSource?.close();
      this.sseSource = null;
      setTimeout(() => {
        if (this.selectedContestDetail && this.selectedContestDetail.contest.id === contestId) {
          this.connectSse(contestId);
        }
      }, 5000);
    };
  }

  loadLeaderboard(contestId: number): void {
    this.apiService.getLeaderboard(contestId).subscribe({
      next: (data) => this.leaderboardData = data,
      error: (err) => console.error('Failed to load leaderboard', err)
    });
  }

  loadEligibleStudents(contestId: number): void {
    this.apiService.getAdminContestRegistrations(contestId).subscribe({
      next: (data) => this.eligibleStudents = data,
      error: (err) => console.error('Failed to load eligible students', err)
    });
  }

  closeContestDetail(): void {
    this.selectedContestDetail = null;
    this.leaderboardData = [];
    this.eligibleStudents = [];
    this.studentListInput = '';
    this.loadData();
    if (this.sseSource) {
      this.sseSource.close();
      this.sseSource = null;
    }
  }

  addEligibleStudents(): void {
    if (!this.studentListInput.trim() || !this.selectedContestDetail) return;
    const rollNos = this.studentListInput
      .split(/[\n,]+/)
      .map(r => r.trim())
      .filter(Boolean);
    
    if (rollNos.length === 0) return;

    this.submittingEligible = true;
    const contestId = this.selectedContestDetail.contest.id;
    this.apiService.registerStudentsBulk(contestId, rollNos).subscribe({
      next: (res) => {
        this.submittingEligible = false;
        this.studentListInput = '';
        alert(res.message || 'Students made eligible successfully.');
        this.loadLeaderboard(contestId);
        this.loadEligibleStudents(contestId);
      },
      error: (err) => {
        this.submittingEligible = false;
        alert(err.error?.error || 'Failed to make students eligible.');
      }
    });
  }

  removeStudentRegistration(userId: number): void {
    if (!this.selectedContestDetail) return;
    if (confirm('Are you sure you want to remove this student eligibility?')) {
      const contestId = this.selectedContestDetail.contest.id;
      this.apiService.deleteStudentRegistration(contestId, userId).subscribe({
        next: () => {
          alert('Student registration removed successfully.');
          this.loadLeaderboard(contestId);
          this.loadEligibleStudents(contestId);
        },
        error: (err) => alert(err.error?.error || 'Failed to remove student registration')
      });
    }
  }

  resetStudentViolations(rollNo: string): void {
    this.apiService.resetStudentViolations(rollNo).subscribe({
      next: () => {
        alert('Violations reset successfully.');
        if (this.selectedContestDetail) {
          this.loadLeaderboard(this.selectedContestDetail.contest.id);
        }
      },
      error: (err) => alert(err.error?.error || 'Failed to reset violations')
    });
  }

  deleteContest(contestId: number): void {
    if (confirm('Are you sure you want to delete this contest?')) {
      this.apiService.deleteContest(contestId).subscribe({
        next: () => {
          this.loadData();
          alert('Contest deleted successfully.');
        },
        error: (err) => alert(err.error?.error || 'Failed to delete contest')
      });
    }
  }

  private resetForm(): void {
    this.title = '';
    this.description = '';
    this.startTime = '';
    this.durationMins = 120;
    this.selectedProblemIds = [];
    this.problemPoints = {};
    this.problemOrder = {};
  }
}
