import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import {
  AdminContest,
  AdminProblem,
  AdminTestCase,
  AdminUser,
  Difficulty,
  UserRole,
} from '../../models/admin.models';
import { AdminApiService } from '../../services/admin-api.service';
import { SessionService } from '../../services/session.service';
import { ApiService } from '../../services/api.service';
import { SseService } from '../../services/sse.service';

type AdminTab = 'users' | 'problems' | 'contests';

@Component({
  selector: 'app-admin',
  imports: [CommonModule, FormsModule],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css',
})
export class AdminComponent implements OnInit, OnDestroy {
  private readonly adminApi = inject(AdminApiService);
  readonly session = inject(SessionService);
  private readonly api = inject(ApiService);
  private readonly sse = inject(SseService);
  private subs = new Subscription();

  tab = signal<AdminTab>('contests');
  readonly error = signal('');
  readonly message = signal('');

  rollNoInput = '';
  loggedIn = signal(false);

  users = signal<AdminUser[]>([]);
  problems = signal<AdminProblem[]>([]);
  contests = signal<AdminContest[]>([]);
  testCases = signal<AdminTestCase[]>([]);
  selectedProblemId: number | null = null;
  isCreatingProblem = signal(false);

  readonly selectedContest = signal<AdminContest | null>(null);
  readonly leaderboard = signal<any[]>([]);
  readonly showModal = signal(false);
  readonly loadingLeaderboard = signal(false);
  isCreatingContest = signal(true);

  userSearchQuery = '';
  userCurrentPage = signal(0);
  userTotalPages = signal(0);
  userTotalElements = signal(0);

  problemFilterQuery = '';

  userForm = { name: '', rollNo: '', branch: '', role: 'STUDENT' as UserRole };
  problemForm = { title: '', description: '', constraints: '', difficulty: 'EASY' as Difficulty };
  testCaseForm = { stdin: '', expectedOutput: '', isSample: false };
  contestForm = {
    title: '',
    startTime: (() => {
      const date = new Date();
      date.setMinutes(date.getMinutes() + 5);
      const tzoffset = date.getTimezoneOffset() * 60000;
      return new Date(date.getTime() - tzoffset).toISOString().slice(0, 16);
    })(),
    durationMins: 120,
    problems: [] as { problemId: number; title: string; difficulty: string; points: number }[],
  };

  getFilteredProblems(): AdminProblem[] {
    const query = this.problemFilterQuery.toLowerCase().trim();
    if (!query) {
      return [];
    }
    return this.problems().filter((p) => {
      return p.title.toLowerCase().includes(query) || (p.difficulty ?? '').toLowerCase().includes(query);
    });
  }

  private contestTimers: Map<number, any> = new Map();

  ngOnInit(): void {
    this.rollNoInput = this.session.rollNo();
    if (this.rollNoInput) {
      this.login();
    }
  }

  ngOnDestroy(): void {
    this.sse.disconnect();
    this.subs.unsubscribe();
  }

  login(): void {
    this.error.set('');
    if (!this.rollNoInput.trim()) {
      this.error.set('Admin roll number is required.');
      return;
    }
    const rollNo = this.rollNoInput.trim();
    this.session.connect(rollNo, 0);
    this.loggedIn.set(true);
    this.refreshAll();
    this.sse.connect(rollNo);
    this.subscribeToSse();
  }

  logout(): void {
    this.session.disconnect();
    this.loggedIn.set(false);
    this.rollNoInput = '';
    this.sse.disconnect();
    this.subs.unsubscribe();
    this.subs = new Subscription();
    // Clear all timers
    this.contestTimers.forEach((timer) => clearInterval(timer));
    this.contestTimers.clear();
  }

  setTab(tab: AdminTab): void {
    this.tab.set(tab);
    this.message.set('');
    this.error.set('');
  }

  isContestTimeExpired(contest: AdminContest): boolean {
    const startTime = new Date(contest.startTime).getTime();
    const endTime = startTime + contest.durationMins * 60 * 1000;
    return Date.now() > endTime;
  }

  canEndContest(contest: AdminContest): boolean {
    return contest.status === 'ONGOING' && !this.isContestTimeExpired(contest);
  }

  createUser(): void {
    this.adminApi.createUser(this.userForm).subscribe({
      next: (createdUser) => {
        this.message.set(`User ${createdUser.name} created successfully.`);
        this.userSearchQuery = createdUser.rollNo;
        this.userForm = { name: '', rollNo: '', branch: '', role: 'STUDENT' };
        this.searchUsers(0);
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to create user.'),
    });
  }

  createProblem(): void {
    this.adminApi.createProblem(this.problemForm).subscribe({
      next: (createdProblem) => {
        this.message.set('Problem created successfully. You can now add test cases.');
        this.problemForm = { title: '', description: '', constraints: '', difficulty: 'EASY' };
        this.isCreatingProblem.set(false);
        this.adminApi.listProblems().subscribe({
          next: (rows) => {
            this.problems.set(rows);
            this.selectProblem(createdProblem.id);
          },
          error: (err) => this.error.set(err?.error?.error ?? 'Failed to load problems.'),
        });
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to create problem.'),
    });
  }

  selectProblem(problemId: number): void {
    this.selectedProblemId = problemId;
    this.isCreatingProblem.set(false);
    this.loadTestCases(problemId);
  }

  startCreatingProblem(): void {
    this.selectedProblemId = null;
    this.isCreatingProblem.set(true);
    this.message.set('');
    this.error.set('');
  }

  createTestCase(): void {
    if (!this.selectedProblemId) {
      return;
    }
    this.adminApi.createTestCase(this.selectedProblemId, this.testCaseForm).subscribe({
      next: () => {
        this.message.set('Test case added.');
        this.testCaseForm = { stdin: '', expectedOutput: '', isSample: false };
        this.loadTestCases(this.selectedProblemId!);
        this.loadProblems();
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to add test case.'),
    });
  }

  deleteTestCase(id: number): void {
    this.adminApi.deleteTestCase(id).subscribe({
      next: () => {
        if (this.selectedProblemId) {
          this.loadTestCases(this.selectedProblemId);
          this.loadProblems();
        }
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to delete test case.'),
    });
  }

  addProblemToContest(problem: AdminProblem): void {
    if (this.isProblemSelected(problem.id)) {
      return;
    }
    this.contestForm.problems.push({
      problemId: problem.id,
      title: problem.title,
      difficulty: problem.difficulty || 'EASY',
      points: 100,
    });
  }

  removeProblemFromContest(problemId: number): void {
    this.contestForm.problems = this.contestForm.problems.filter(
      (p) => p.problemId !== problemId
    );
  }

  moveProblemUp(index: number): void {
    if (index === 0) return;
    const list = this.contestForm.problems;
    const temp = list[index];
    list[index] = list[index - 1];
    list[index - 1] = temp;
    this.contestForm.problems = [...list];
  }

  moveProblemDown(index: number): void {
    const list = this.contestForm.problems;
    if (index === list.length - 1) return;
    const temp = list[index];
    list[index] = list[index + 1];
    list[index + 1] = temp;
    this.contestForm.problems = [...list];
  }

  isProblemSelected(problemId: number): boolean {
    return this.contestForm.problems.some((p) => p.problemId === problemId);
  }

  createContest(): void {
    const problems = this.contestForm.problems.map((p, index) => ({
      problemId: p.problemId,
      points: p.points || 100,
      displayOrder: index + 1,
    }));

    this.adminApi
      .createContest({
        title: this.contestForm.title,
        startTime: this.contestForm.startTime,
        durationMins: this.contestForm.durationMins,
        problems,
      })
      .subscribe({
        next: (createdContest) => {
          this.message.set('Contest created successfully.');
          this.contestForm = {
            title: '',
            startTime: (() => {
              const date = new Date();
              date.setMinutes(date.getMinutes() + 5);
              const tzoffset = date.getTimezoneOffset() * 60000;
              return new Date(date.getTime() - tzoffset).toISOString().slice(0, 16);
            })(),
            durationMins: 120,
            problems: [],
          };
          this.isCreatingContest.set(false);
          this.adminApi.listContests().subscribe({
            next: (rows) => {
              this.contests.set(rows);
              const match = rows.find(x => x.id === createdContest.id);
              if (match) {
                this.selectContest(match);
              }
            },
            error: (err) => this.error.set(err?.error?.error ?? 'Failed to load contests.'),
          });
        },
        error: (err) => this.error.set(err?.error?.error ?? 'Failed to create contest.'),
      });
  }

  startContest(id: number): void {
    this.adminApi.startContest(id).subscribe({
      next: (updated) => {
        this.message.set('Contest started.');
        this.loadContests();
        // Update selection with new status so view updates live
        const current = this.selectedContest();
        if (current && current.id === id) {
          this.selectedContest.set({ ...current, status: updated.status });
        }
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to start contest.'),
    });
  }

  endContest(id: number): void {
    this.adminApi.endContest(id).subscribe({
      next: (updated) => {
        this.message.set('Contest ended.');
        this.loadContests();
        // Update selection with new status so view updates live
        const current = this.selectedContest();
        if (current && current.id === id) {
          this.selectedContest.set({ ...current, status: updated.status });
        }
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to end contest.'),
    });
  }

  selectContest(contest: AdminContest): void {
    this.selectedContest.set(contest);
    this.isCreatingContest.set(false);
    this.loadingLeaderboard.set(true);
    this.leaderboard.set([]);
    this.api.getLeaderboard(contest.id).subscribe({
      next: (entries) => {
        this.leaderboard.set(entries);
        this.loadingLeaderboard.set(false);
      },
      error: (err) => {
        console.error('Failed to load leaderboard:', err);
        this.loadingLeaderboard.set(false);
      },
    });
  }

  startCreatingContest(): void {
    this.selectedContest.set(null);
    this.isCreatingContest.set(true);
    this.message.set('');
    this.error.set('');
    this.problemFilterQuery = '';
    this.contestForm.problems = [];
  }

  private refreshAll(): void {
    this.loadProblems();
    this.loadContests();
  }

  private subscribeToSse(): void {
    this.subs.unsubscribe();
    this.subs = new Subscription();

    this.subs.add(
      this.sse.leaderboard$.subscribe(() => {
        const currentContest = this.selectedContest();
        if (currentContest) {
          this.refreshLeaderboard(currentContest.id);
        }
      })
    );

    this.subs.add(
      this.sse.contestEvent$.subscribe((event) => {
        this.loadContests();
        const current = this.selectedContest();
        if (current && current.id === event.contestId) {
          this.selectedContest.set({ ...current, status: event.status });
        }
      })
    );
  }

  private refreshLeaderboard(contestId: number): void {
    this.api.getLeaderboard(contestId).subscribe({
      next: (entries) => {
        const current = this.selectedContest();
        if (current && current.id === contestId) {
          this.leaderboard.set(entries);
        }
      },
      error: (err) => console.error('Failed to auto-update leaderboard:', err),
    });
  }

  searchUsers(page: number = 0): void {
    this.userCurrentPage.set(page);
    this.adminApi.listUsers(this.userSearchQuery, page, 10).subscribe({
      next: (res) => {
        this.users.set(res.content);
        this.userTotalPages.set(res.totalPages);
        this.userTotalElements.set(res.totalElements);
        this.error.set('');
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to search users.'),
    });
  }

  private loadProblems(): void {
    this.adminApi.listProblems().subscribe({
      next: (rows) => this.problems.set(rows),
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to load problems.'),
    });
  }

  private loadContests(): void {
    this.adminApi.listContests().subscribe({
      next: (rows) => this.contests.set(rows),
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to load contests.'),
    });
  }

  private loadTestCases(problemId: number): void {
    this.adminApi.listTestCases(problemId).subscribe({
      next: (rows) => this.testCases.set(rows),
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to load test cases.'),
    });
  }

  getSelectedProblem(): AdminProblem | undefined {
    if (!this.selectedProblemId) return undefined;
    return this.problems().find((p) => p.id === this.selectedProblemId);
  }

  viewLeaderboard(contest: AdminContest): void {
    this.selectedContest.set(contest);
    this.showModal.set(true);
    this.loadingLeaderboard.set(true);
    this.leaderboard.set([]);

    this.api.getLeaderboard(contest.id).subscribe({
      next: (entries) => {
        this.leaderboard.set(entries);
        this.loadingLeaderboard.set(false);
      },
      error: (err) => {
        console.error('Failed to load leaderboard:', err);
        this.loadingLeaderboard.set(false);
      },
    });
  }

  closeModal(): void {
    this.showModal.set(false);
    this.selectedContest.set(null);
    this.leaderboard.set([]);
  }

  formatDateTime(iso: string): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  }

  formatDuration(mins: number): string {
    if (mins < 60) return `${mins} mins`;
    const hrs = Math.floor(mins / 60);
    const remaining = mins % 60;
    return remaining > 0 ? `${hrs}h ${remaining}m` : `${hrs} hrs`;
  }
}
