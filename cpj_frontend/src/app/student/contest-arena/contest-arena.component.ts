/*
 * File: contest-arena.component.ts
 * Purpose: Student coding workspace. Manages code editor (Monaco), execution tab output maps, and exam submissions.
 */
import { Component, OnInit, AfterViewInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common'; import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service'; import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-contest-arena', standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './contest-arena.component.html',
  styleUrl: './contest-arena.component.css'
})
export class ContestArenaComponent implements OnInit, AfterViewInit {
  contestId = 0; problems: any[] = []; activeQ = 0; selectedLang = 'java'; consoleTab: number | 'custom' = 0;
  customInput = ''; consoleOutput = ''; loading = false; submitting = false; tcOutputs: Record<string, string> = {};
  tcVerdicts: Record<string, string> = {}; showSubmitModal = false; submitError = ''; submittingContest = false;
  submissions: any[] = []; leftTab: 'description' | 'submissions' = 'description';
  isEditorExpanded = false;
  private editor: any; private runResult: any = null;

  constructor(private route: ActivatedRoute, private router: Router, private apiService: ApiService, private authService: AuthService) {}

  ngOnInit(): void {
    this.contestId = Number(this.route.snapshot.paramMap.get('id'));
    this.apiService.getContestProblems(this.contestId).subscribe({
      next: (d) => {
        this.problems = d; const qp = this.route.snapshot.queryParamMap;
        this.activeQ = Number(qp.get('problem')) || 0; this.selectedLang = qp.get('lang') || 'java';
        this.loadSavedCode(); this.consoleTab = d[this.activeQ]?.testCases?.length > 0 ? 0 : 'custom'; this.loadSubmissions();
      },
      error: () => this.router.navigate(['/student/dashboard'])
    });
  }

  ngAfterViewInit(): void {
    if ((window as any).monaco) return this.initMonaco();
    const s = document.createElement('script'); s.src = 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs/loader.js';
    s.onload = () => {
      const w = window as any; w.require.config({ paths: { vs: 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' } });
      w.require(['vs/editor/editor.main'], () => this.initMonaco());
    };
    document.body.appendChild(s);
  }

  initMonaco(): void {
    const key = `code_${this.contestId}_${this.problems[this.activeQ]?.problemId}_${this.selectedLang}`;
    this.editor = (window as any).monaco.editor.create(document.getElementById('editor-container'), {
      value: localStorage.getItem(key) || this.getCodeTemplate(),
      language: this.selectedLang === 'cpp' ? 'cpp' : this.selectedLang === 'python' ? 'python' : 'java',
      theme: 'vs-dark', automaticLayout: true, fontSize: 14, minimap: { enabled: false }
    });
    this.editor.onDidChangeModelContent(() => {
      if (this.editor && this.problems[this.activeQ]) localStorage.setItem(`code_${this.contestId}_${this.problems[this.activeQ].problemId}_${this.selectedLang}`, this.editor.getValue());
    });
  }

  getCodeTemplate(): string {
    const c = this.problems[this.activeQ]?.templates?.[this.selectedLang];
    return c || (this.selectedLang === 'cpp' ? '#include <iostream>\nusing namespace std;\nint main() {\n    return 0;\n}' : this.selectedLang === 'python' ? 'def main():\n    pass\nif __name__ == "__main__":\n    main()' : 'import java.util.*;\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n    }\n}');
  }

  loadSavedCode(): void {
    if (!this.problems[this.activeQ] || !this.editor) return;
    this.editor.setValue(localStorage.getItem(`code_${this.contestId}_${this.problems[this.activeQ].problemId}_${this.selectedLang}`) || this.getCodeTemplate());
    (window as any).monaco.editor.setModelLanguage(this.editor.getModel(), this.selectedLang === 'cpp' ? 'cpp' : this.selectedLang === 'python' ? 'python' : 'java');
  }

  onLangChange(lang: string): void { this.selectedLang = lang; this.loadSavedCode(); this.updateRoute(); }

  toggleEditorExpand(): void {
    this.isEditorExpanded = !this.isEditorExpanded;
    setTimeout(() => this.editor?.layout(), 50);
    setTimeout(() => this.editor?.layout(), 150);
    setTimeout(() => this.editor?.layout(), 300);
  }

  /** Collapse to normal view, then run code */
  runAndCollapse(): void {
    this.isEditorExpanded = false;
    setTimeout(() => { this.editor?.layout(); this.runCode(); }, 50);
    setTimeout(() => this.editor?.layout(), 150);
    setTimeout(() => this.editor?.layout(), 300);
  }

  /** Collapse to normal view, then submit code */
  submitAndCollapse(): void {
    this.isEditorExpanded = false;
    setTimeout(() => { this.editor?.layout(); this.submitCode(); }, 50);
    setTimeout(() => this.editor?.layout(), 150);
    setTimeout(() => this.editor?.layout(), 300);
  }
  selectTab(tab: number | 'custom'): void { this.consoleTab = tab; this.updateConsoleOutput(); }
  updateRoute(): void { this.router.navigate([], { relativeTo: this.route, queryParams: { problem: this.activeQ, lang: this.selectedLang }, queryParamsHandling: 'merge' }); }
  loadSubmissions(): void { this.apiService.getSubmissions(this.contestId).subscribe({ next: (d) => this.submissions = d }); }
  get problemSubmissions(): any[] { return this.submissions.filter((s: any) => s.problemId === this.problems[this.activeQ]?.problemId); }

  onProblemChange(idx: number): void {
    this.activeQ = idx; this.loadSavedCode(); this.consoleTab = this.problems[idx]?.testCases?.length > 0 ? 0 : 'custom';
    this.tcOutputs = {}; this.tcVerdicts = {}; this.runResult = null; this.updateRoute();
  }

  useCode(code: string, langId: number): void {
    const lang = { 62: 'java', 54: 'cpp', 71: 'python' }[langId] || 'java'; this.selectedLang = lang;
    if (this.editor) { this.editor.setValue(code); (window as any).monaco.editor.setModelLanguage(this.editor.getModel(), lang === 'cpp' ? 'cpp' : lang === 'python' ? 'python' : 'java'); }
    this.updateRoute();
  }

  updateConsoleOutput(): void {
    if (!this.runResult) return;
    if (this.runResult.status === 'Compilation Error') { this.consoleOutput = `Compilation Error:\n\n${this.runResult.output || ''}`; return; }
    if (this.consoleTab === 'custom') {
      this.consoleOutput = this.runResult.testCaseResults?.[this.runResult.testCaseResults.length - 1]?.stderr || '';
    } else {
      const tc = this.runResult.testCaseResults?.[this.consoleTab]; if (!tc) { this.consoleOutput = 'No verdict.'; return; }
      const v = this.tcVerdicts[this.consoleTab]; this.consoleOutput = `Verdict: ${v}` + (v === 'Accepted' ? '' : (tc.stderr ? `\n\nError:\n${tc.stderr}` : `\n\nInput:\n${tc.stdin}\nExpected:\n${tc.expectedOutput}\nActual:\n${tc.actualOutput}`));
    }
  }

  runCode(): void {
    if (this.loading || !this.problems[this.activeQ]) return;
    this.loading = true; this.consoleOutput = 'Executing code on server...';
    this.apiService.compileCode({ contestId: this.contestId, questionId: this.problems[this.activeQ].problemId, language: this.selectedLang.toUpperCase(), code: this.editor.getValue(), customInput: this.customInput ? this.customInput.trim() : '' }).subscribe({
      next: (res) => {
        this.loading = false; this.runResult = res;
        if (res.status === 'Compilation Error') { this.consoleOutput = `Compilation Error:\n\n${res.output || ''}`; return; }
        res.testCaseResults?.forEach((tc: any, idx: number) => {
          const isCust = idx === this.problems[this.activeQ].testCases?.length, k = isCust ? 'custom' : String(idx);
          this.tcOutputs[k] = tc.actualOutput || '';
          const m: Record<string, string> = { ACCEPTED: 'Accepted', WRONG_ANSWER: 'Wrong Answer', TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded', MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded', RUNTIME_ERROR: 'Runtime Error' };
          this.tcVerdicts[k] = isCust ? '' : (m[tc.verdict || ''] || 'Compilation Error');
        });
        this.updateConsoleOutput();
      },
      error: (e) => { this.loading = false; this.consoleOutput = e.error?.error || 'Run failed'; }
    });
  }

  submitCode(): void {
    if (this.submitting || !this.problems[this.activeQ]) return;
    this.submitting = true; this.consoleOutput = 'Evaluating...';
    this.apiService.submitCode({ contestId: this.contestId, questionId: this.problems[this.activeQ].problemId, rollNumber: this.authService.rollNumber() || '', language: this.selectedLang.toUpperCase(), code: this.editor.getValue() }).subscribe({
      next: (r) => { this.submitting = false; this.consoleOutput = `Verdict: ${r.verdict}\nPassed: ${r.passed}/${r.total}`; this.loadSubmissions(); },
      error: (e) => { this.submitting = false; this.consoleOutput = e.error?.error || 'Failed'; }
    });
  }

  confirmSubmit(): void {
    this.submitError = ''; this.submittingContest = true;
    this.apiService.submitContest(this.contestId).subscribe({ next: () => this.router.navigate(['/student/dashboard']), error: (e) => { this.submittingContest = false; this.submitError = e.error?.error || 'Failed'; } });
  }
}
