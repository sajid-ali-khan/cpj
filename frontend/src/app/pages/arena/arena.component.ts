import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ContestProblemSummary, ContestSummary } from '../../models/admin.models';
import {
  LANGUAGES,
  LeaderboardEntry,
  SubmissionResponse,
  Verdict,
} from '../../models/api.models';
import { ApiService } from '../../services/api.service';
import { SessionService } from '../../services/session.service';
import { SseConnectionState, SseService } from '../../services/sse.service';

@Component({
  selector: 'app-arena',
  imports: [CommonModule, FormsModule],
  templateUrl: './arena.component.html',
  styleUrl: './arena.component.css',
})
export class ArenaComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  readonly session = inject(SessionService);
  private readonly sse = inject(SseService);

  readonly languages = LANGUAGES;

  rollNoInput = '';
  contest = signal<ContestSummary | null>(null);
  problems = signal<ContestProblemSummary[]>([]);
  selectedProblemId: number | null = null;
  languageId = 71;
  code = '';

  readonly submissions = signal<SubmissionResponse[]>([]);
  readonly leaderboard = signal<LeaderboardEntry[]>([]);
  readonly submitting = signal(false);
  readonly error = signal('');

  activeTab = signal<'problem' | 'submissions' | 'leaderboard'>('problem');
  expandedSubmissionId = signal<number | null>(null);

  setTab(tab: 'problem' | 'submissions' | 'leaderboard'): void {
    this.activeTab.set(tab);
    if (tab === 'submissions') {
      this.refreshSubmissions();
    } else if (tab === 'leaderboard') {
      this.refreshLeaderboard();
    }
  }

  getProblemTitle(problemId: number): string {
    const p = this.problems().find((prob) => prob.problemId === problemId);
    return p ? p.title : `Problem ${problemId}`;
  }

  getLanguageLabel(languageId: number): string {
    const lang = this.languages.find((l) => l.id === languageId);
    return lang ? lang.label : `Lang ${languageId}`;
  }

  viewCode(row: SubmissionResponse): void {
    if (this.expandedSubmissionId() === row.id) {
      this.expandedSubmissionId.set(null);
    } else {
      this.expandedSubmissionId.set(row.id);
    }
  }

  loadIntoEditor(code: string, problemId: number, languageId: number): void {
    this.code = code;
    this.selectedProblemId = problemId;
    this.languageId = languageId;
    this.activeTab.set('problem');
  }
  readonly connectionState = this.sse.connectionState;
  readonly checkingContest = signal(true);
  readonly contestExists = signal<boolean | null>(null);

  private subs = new Subscription();

  ngOnInit(): void {
    this.rollNoInput = this.session.rollNo();
    if (this.session.isConnected()) {
      this.loadContestAndSession();
    } else {
      // Check if a contest exists before showing login form
      this.checkContestAvailability();
    }

    this.subs.add(
      this.sse.verdict$.subscribe(() => {
        this.refreshSubmissions();
        this.refreshLeaderboard();
      }),
    );
    this.subs.add(this.sse.leaderboard$.subscribe((entries) => this.leaderboard.set(entries)));
    this.subs.add(
      this.sse.contestEvent$.subscribe((event) => {
        if (event.status === 'ENDED') {
          this.error.set('Contest has ended.');
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.sse.disconnect();
  }

  connect(): void {
    this.error.set('');
    if (!this.rollNoInput.trim()) {
      this.error.set('Roll number is required.');
      return;
    }
    this.loadContestAndSession(this.rollNoInput.trim());
  }

  private checkContestAvailability(): void {
    this.checkingContest.set(true);
    this.api.getCurrentContest().subscribe({
      next: (contest) => {
        this.contestExists.set(true);
        this.checkingContest.set(false);
      },
      error: () => {
        this.contestExists.set(false);
        this.checkingContest.set(false);
        this.error.set('No ongoing contest. Ask your instructor to start one.');
      },
    });
  }

  disconnect(): void {
    this.sse.disconnect();
    this.session.disconnect();
    this.contest.set(null);
    this.problems.set([]);
    this.submissions.set([]);
    this.leaderboard.set([]);
    this.rollNoInput = '';
    this.selectedProblemId = null;
  }

  submit(): void {
    const contestId = this.contest()?.id;
    if (!contestId || !this.selectedProblemId || !this.code.trim()) {
      return;
    }

    this.submitting.set(true);
    this.error.set('');

    this.api
      .submit({
        contestId,
        problemId: this.selectedProblemId,
        code: this.code,
        languageId: this.languageId,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.refreshSubmissions();
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.error ?? 'Submission failed.');
        },
      });
  }

  selectedProblem(): ContestProblemSummary | undefined {
    return this.problems().find((p) => p.problemId === this.selectedProblemId);
  }

  connectionLabel(state: SseConnectionState): string {
    switch (state) {
      case 'connected':
        return 'Live';
      case 'connecting':
        return 'Connecting…';
      case 'error':
        return 'Reconnecting…';
      default:
        return 'Offline';
    }
  }

  verdictClass(verdict: Verdict): string {
    switch (verdict) {
      case 'ACCEPTED':
        return 'verdict-accepted';
      case 'PENDING':
        return 'verdict-pending';
      case 'WRONG_ANSWER':
        return 'verdict-wa';
      default:
        return 'verdict-error';
    }
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString();
  }

  private loadContestAndSession(rollNo?: string): void {
    const resolvedRollNo = rollNo ?? this.session.rollNo();
    if (!resolvedRollNo) {
      this.checkingContest.set(false);
      return;
    }

    this.api.getCurrentContest().subscribe({
      next: (contest) => {
        this.contest.set(contest);
        this.session.connect(resolvedRollNo, contest.id);
        this.sse.connect(resolvedRollNo);
        this.loadProblems(contest.id);
        this.refreshSubmissions();
        this.refreshLeaderboard();
        this.contestExists.set(true);
        this.checkingContest.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.error ?? 'No ongoing contest. Ask your instructor to start one.');
        this.sse.disconnect();
        this.contestExists.set(false);
        this.checkingContest.set(false);
      },
    });
  }

  private loadProblems(contestId: number): void {
    this.api.getContestProblems(contestId).subscribe({
      next: (problems) => {
        this.problems.set(problems);
        if (problems.length > 0 && !this.selectedProblemId) {
          this.selectedProblemId = problems[0].problemId;
        }
      },
      error: (err) => this.error.set(err?.error?.error ?? 'Failed to load problems.'),
    });
  }

  refreshSubmissions(): void {
    const contestId = this.contest()?.id;
    if (!contestId) {
      return;
    }
    this.api.getSubmissions(contestId).subscribe({
      next: (rows) => this.submissions.set(rows),
    });
  }

  refreshLeaderboard(): void {
    const contestId = this.contest()?.id;
    if (!contestId) {
      return;
    }
    this.api.getLeaderboard(contestId).subscribe({
      next: (rows) => this.leaderboard.set(rows),
    });
  }
}
