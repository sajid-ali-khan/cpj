/*
 * File: contest-arena-state.service.ts
 * Purpose: Manage state and business logic for the Student Coding Arena.
 */

import { Injectable } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { getVerdictLabel } from './contest-arena.helper';

@Injectable()
export class ContestArenaStateService {
  contestId = 0;
  problems: any[] = [];
  activeQ = 0;
  selectedLang = 'java';
  consoleTab: number | 'custom' = 0;
  customInput = '';
  consoleOutput = '';
  loading = false;
  submitting = false;
  submittingContest = false;
  submitError = '';
  tcOutputs: Record<string, string> = {};
  tcVerdicts: Record<string, string> = {};
  submissions: any[] = [];
  leaderboard: any[] = [];
  runResult: any = null;

  constructor(
    private apiService: ApiService,
    private authService: AuthService
  ) {}

  loadProblems(contestId: number, success: () => void, error: () => void): void {
    this.apiService.getContestProblems(contestId).subscribe({
      next: (d) => {
        this.problems = d;
        this.consoleTab = d[this.activeQ]?.testCases?.length > 0 ? 0 : 'custom';
        this.loadSubmissions(contestId);
        this.loadLeaderboard(contestId);
        success();
      },
      error: () => error()
    });
  }

  loadSubmissions(contestId: number): void {
    this.apiService.getSubmissions(contestId).subscribe({
      next: (d) => this.submissions = d
    });
  }

  loadLeaderboard(contestId: number): void {
    this.apiService.getLeaderboard(contestId).subscribe({ next: (d) => this.leaderboard = d });
  }

  get problemSubmissions(): any[] {
    return this.submissions.filter((s: any) => s.problemId === this.problems[this.activeQ]?.problemId);
  }

  updateConsoleOutput(): void {
    if (!this.runResult) return;
    if (this.runResult.status === 'Compilation Error') {
      this.consoleOutput = `Compilation Error:\n\n${this.runResult.output || ''}`;
      return;
    }
    if (this.consoleTab === 'custom') {
      this.consoleOutput = this.runResult.testCaseResults?.[this.runResult.testCaseResults.length - 1]?.stderr || '';
    } else {
      const tc = this.runResult.testCaseResults?.[this.consoleTab];
      if (!tc) {
        this.consoleOutput = 'No verdict.';
        return;
      }
      const v = this.tcVerdicts[this.consoleTab];
      this.consoleOutput = `Verdict: ${v}` + (v === 'Accepted' ? '' : (tc.stderr ? `\n\nError:\n${tc.stderr}` : `\n\nInput:\n${tc.stdin}\nExpected:\n${tc.expectedOutput}\nActual:\n${tc.actualOutput}`));
    }
  }

  runCode(contestId: number, code: string): void {
    if (this.loading || !this.problems[this.activeQ]) return;
    this.loading = true;
    this.consoleOutput = 'Executing code on server...';
    this.apiService.compileCode({
      contestId,
      questionId: this.problems[this.activeQ].problemId,
      language: this.selectedLang.toUpperCase(),
      code,
      customInput: this.customInput ? this.customInput.trim() : ''
    }).subscribe({
      next: (res) => {
        this.loading = false;
        this.runResult = res;
        if (res.status === 'Compilation Error') {
          this.consoleOutput = `Compilation Error:\n\n${res.output || ''}`;
          return;
        }
        res.testCaseResults?.forEach((tc: any, idx: number) => {
          const isCust = idx === this.problems[this.activeQ].testCases?.length;
          const k = isCust ? 'custom' : String(idx);
          this.tcOutputs[k] = tc.actualOutput || '';
          this.tcVerdicts[k] = isCust ? '' : getVerdictLabel(tc.verdict);
        });
        this.updateConsoleOutput();
      },
      error: (e) => {
        this.loading = false;
        this.consoleOutput = e.error?.error || 'Run failed';
      }
    });
  }

  submitCode(contestId: number, code: string): void {
    if (this.submitting || !this.problems[this.activeQ]) return;
    this.submitting = true;
    this.consoleOutput = 'Evaluating...';
    this.apiService.submitCode({
      contestId,
      questionId: this.problems[this.activeQ].problemId,
      rollNumber: this.authService.rollNumber() || '',
      language: this.selectedLang.toUpperCase(),
      code
    }).subscribe({
      next: (r) => {
        this.submitting = false;
        this.consoleOutput = `Verdict: ${r.verdict}\nPassed: ${r.passed}/${r.total}`;
        this.loadSubmissions(contestId);
      },
      error: (e) => {
        this.submitting = false;
        this.consoleOutput = e.error?.error || 'Failed';
      }
    });
  }

  confirmSubmit(contestId: number, success: () => void): void {
    this.submitError = '';
    this.submittingContest = true;
    this.apiService.submitContest(contestId).subscribe({
      next: () => success(),
      error: (e) => {
        this.submittingContest = false;
        this.submitError = e.error?.error || 'Failed';
      }
    });
  }
}
