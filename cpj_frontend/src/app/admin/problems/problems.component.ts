/*
 * File: problems.component.ts
 * Purpose: Invigilator problem repository manager. Supports manual/file/CSV testcase management.
 */

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { truncateText, getSizeLabel } from './problems.helper';

@Component({
  selector: 'app-admin-problems',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './problems.component.html',
  styleUrl: './problems.component.css'
})
export class ProblemsComponent implements OnInit {
  problems: any[] = [];
  testCases: any[] = [];
  selectedProblemId: number | null = null;
  selectedProblemTitle = '';
  showAddProblem = false;
  showTestCases = false;

  // Forms & Modals
  title = '';
  description = '';
  constraints = '';
  difficulty = 'EASY';
  inputStructure = '';
  outputStructure = '';
  tcInput = '';
  tcOutput = '';
  tcIsSample = false;
  addMode: 'manual' | 'file' | 'csv' = 'manual';
  activeInspector: any = null;
  selectedCSVFile: File | null = null;

  // Custom Alerts & Confirms
  alertMsg: string | null = null;
  confirmConfig: { msg: string; onConfirm: () => void } | null = null;

  truncate = truncateText;
  getSize = getSizeLabel;

  constructor(private apiService: ApiService) {}

  ngOnInit(): void { this.loadProblems(); }

  loadProblems(): void {
    this.apiService.getAdminProblems().subscribe(data => this.problems = data);
  }

  showAlert(msg: string): void { this.alertMsg = msg; }
  showConfirm(msg: string, action: () => void): void { this.confirmConfig = { msg, onConfirm: action }; }

  saveProblem(): void {
    if (!this.title || !this.description) return;
    this.apiService.createProblem({
      title: this.title, description: this.description, constraints: this.constraints,
      difficulty: this.difficulty, inputStructure: this.inputStructure, outputStructure: this.outputStructure
    }).subscribe({
      next: () => { this.loadProblems(); this.showAddProblem = false; this.resetProblemForm(); },
      error: (err) => this.showAlert(err.error?.error || 'Failed to create problem')
    });
  }

  viewTestCases(problemId: number, title: string): void {
    this.selectedProblemId = problemId;
    this.selectedProblemTitle = title;
    this.apiService.getTestCases(problemId).subscribe({
      next: (data) => { this.testCases = data; this.showTestCases = true; },
      error: () => this.showAlert('Failed to retrieve test cases')
    });
  }

  addTestCase(): void {
    if (this.selectedProblemId === null) return;
    this.apiService.createTestCase(this.selectedProblemId, {
      stdin: this.tcInput, expectedOutput: this.tcOutput, isSample: this.tcIsSample
    }).subscribe({
      next: () => {
        this.viewTestCases(this.selectedProblemId!, this.selectedProblemTitle);
        this.tcInput = ''; this.tcOutput = ''; this.tcIsSample = false;
      },
      error: (err) => this.showAlert(err.error?.error || 'Failed to add testcase')
    });
  }

  deleteTestCase(id: number): void {
    this.showConfirm('Delete this testcase?', () => {
      this.apiService.deleteTestCase(id).subscribe({
        next: () => this.viewTestCases(this.selectedProblemId!, this.selectedProblemTitle),
        error: (err) => this.showAlert(err.error?.error || 'Failed to delete testcase')
      });
    });
  }

  readFile(file: File, cb: (res: string) => void): void {
    const r = new FileReader();
    r.onload = (e) => cb(e.target?.result as string || '');
    r.readAsText(file);
  }

  onFileChange(e: any, key: 'tcInput' | 'tcOutput'): void {
    const f = e.target.files?.[0];
    if (f) this.readFile(f, (txt) => this[key] = txt);
  }

  onCSVFileSelected(e: any): void {
    this.selectedCSVFile = e.target.files?.[0] || null;
  }

  uploadCSV(): void {
    if (this.selectedCSVFile && this.selectedProblemId !== null) {
      this.apiService.uploadTestCaseCSV(this.selectedProblemId, this.selectedCSVFile).subscribe({
        next: () => {
          this.showAlert('CSV file uploaded and processed successfully.');
          this.viewTestCases(this.selectedProblemId!, this.selectedProblemTitle);
          this.selectedCSVFile = null;
        },
        error: (err) => this.showAlert(err.error?.error || 'Failed to upload CSV')
      });
    }
  }

  private resetProblemForm(): void {
    this.title = ''; this.description = ''; this.constraints = '';
    this.difficulty = 'EASY'; this.inputStructure = ''; this.outputStructure = '';
  }
}
