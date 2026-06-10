import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
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

type AdminTab = 'users' | 'problems' | 'contests';

@Component({
  selector: 'app-admin',
  imports: [CommonModule, FormsModule],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css',
})
export class AdminComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  readonly session = inject(SessionService);

  tab = signal<AdminTab>('users');
  readonly error = signal('');
  readonly message = signal('');

  rollNoInput = '';
  loggedIn = signal(false);

  users = signal<AdminUser[]>([]);
  problems = signal<AdminProblem[]>([]);
  contests = signal<AdminContest[]>([]);
  testCases = signal<AdminTestCase[]>([]);
  selectedProblemId: number | null = null;

  userForm = { name: '', rollNo: '', branch: '', role: 'STUDENT' as UserRole };
  problemForm = { title: '', description: '', constraints: '', difficulty: 'EASY' as Difficulty };
  testCaseForm = { stdin: '', expectedOutput: '', isSample: false };
  contestForm = {
    title: '',
    startTime: '',
    durationMins: 120,
    selectedProblemIds: [] as number[],
    points: 100,
  };

  private contestTimers: Map<number, any> = new Map();

  ngOnInit(): void {
    this.rollNoInput = this.session.rollNo();
    if (this.rollNoInput) {
      this.login();
    }
  }

  login(): void {
    this.error.set('');
    if (!this.rollNoInput.trim()) {
      this.error.set('Admin roll number is required.');
      return;
    }
    this.session.connect(this.rollNoInput.trim(), 0);
    this.loggedIn.set(true);
    this.refreshAll();
  }

  logout(): void {
    this.session.disconnect();
    this.loggedIn.set(false);
    this.rollNoInput = '';
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
      next: () => {
        this.message.set('User created.');
        this.userForm = { name: '', rollNo: '', branch: '', role: 'STUDENT' };
        this.loadUsers();
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to create user.'),
    });
  }

  createProblem(): void {
    this.adminApi.createProblem(this.problemForm).subscribe({
      next: () => {
        this.message.set('Problem created.');
        this.problemForm = { title: '', description: '', constraints: '', difficulty: 'EASY' };
        this.loadProblems();
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to create problem.'),
    });
  }

  selectProblem(problemId: number): void {
    this.selectedProblemId = problemId;
    this.loadTestCases(problemId);
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

  toggleContestProblem(problemId: number, checked: boolean): void {
    const ids = this.contestForm.selectedProblemIds;
    if (checked) {
      if (!ids.includes(problemId)) {
        this.contestForm.selectedProblemIds = [...ids, problemId];
      }
    } else {
      this.contestForm.selectedProblemIds = ids.filter((id) => id !== problemId);
    }
  }

  isProblemSelected(problemId: number): boolean {
    return this.contestForm.selectedProblemIds.includes(problemId);
  }

  createContest(): void {
    const problems = this.contestForm.selectedProblemIds.map((problemId, index) => ({
      problemId,
      points: this.contestForm.points,
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
        next: () => {
          this.message.set('Contest created.');
          this.contestForm = {
            title: '',
            startTime: '',
            durationMins: 120,
            selectedProblemIds: [],
            points: 100,
          };
          this.loadContests();
        },
        error: (err) => this.error.set(err?.error?.error ?? 'Failed to create contest.'),
      });
  }

  startContest(id: number): void {
    this.adminApi.startContest(id).subscribe({
      next: () => {
        this.message.set('Contest started.');
        this.loadContests();
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to start contest.'),
    });
  }

  endContest(id: number): void {
    this.adminApi.endContest(id).subscribe({
      next: () => {
        this.message.set('Contest ended.');
        this.loadContests();
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to end contest.'),
    });
  }

  private refreshAll(): void {
    this.loadUsers();
    this.loadProblems();
    this.loadContests();
  }

  private loadUsers(): void {
    this.adminApi.listUsers().subscribe({
      next: (rows) => this.users.set(rows),
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to load users.'),
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
}
