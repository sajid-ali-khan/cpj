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
  step = 1;

  // Forms & Modals
  title = '';
  description = '';
  constraints = '';
  difficulty = 'EASY';
  inputStructure = '';
  outputStructure = '';
  inputStructureItems: string[] = [''];
  outputStructureItems: string[] = [''];
  tcInput = '';
  tcOutput = '';
  tcIsSample = false;
  activeInspector: any = null;

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

  addInputLine(): void {
    this.inputStructureItems.push('');
  }

  removeInputLine(idx: number): void {
    this.inputStructureItems.splice(idx, 1);
    if (this.inputStructureItems.length === 0) {
      this.inputStructureItems.push('');
    }
  }

  addOutputLine(): void {
    this.outputStructureItems.push('');
  }

  removeOutputLine(idx: number): void {
    this.outputStructureItems.splice(idx, 1);
    if (this.outputStructureItems.length === 0) {
      this.outputStructureItems.push('');
    }
  }

  saveProblem(): void {
    if (!this.title || !this.description) return;
    const inputStr = JSON.stringify(this.inputStructureItems.map(item => item.trim()).filter(Boolean));
    const outputStr = JSON.stringify(this.outputStructureItems.map(item => item.trim()).filter(Boolean));

    this.apiService.createProblem({
      title: this.title, description: this.description, constraints: this.constraints,
      difficulty: this.difficulty, inputStructure: inputStr, outputStructure: outputStr
    }).subscribe({
      next: (res) => {
        this.loadProblems();
        this.selectedProblemId = res.id;
        this.selectedProblemTitle = res.title;
        this.testCases = [];
        this.step = 2;
      },
      error: (err) => this.showAlert(err.error?.error || 'Failed to create problem')
    });
  }

  finishAddProblem(): void {
    if (this.testCases.length === 0) {
      this.showConfirm(
        'Warning: A problem must have at least one testcase for compilation and evaluation. Are you sure you want to exit without adding testcases?',
        () => {
          this.showAddProblem = false;
          this.resetProblemForm();
        }
      );
    } else {
      this.showAddProblem = false;
      this.resetProblemForm();
    }
  }

  cancelAddProblem(): void {
    this.showConfirm('Cancel creating problem? Any unsaved details or testcases will be lost.', () => {
      this.showAddProblem = false;
      this.resetProblemForm();
    });
  }

  loadTestCasesForCurrent(): void {
    if (this.selectedProblemId !== null) {
      this.apiService.getTestCases(this.selectedProblemId).subscribe({
        next: (data) => this.testCases = data,
        error: () => this.showAlert('Failed to retrieve test cases')
      });
    }
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
        this.loadTestCasesForCurrent();
        this.tcInput = ''; this.tcOutput = ''; this.tcIsSample = false;
      },
      error: (err) => this.showAlert(err.error?.error || 'Failed to add testcase')
    });
  }

  deleteTestCase(id: number): void {
    this.showConfirm('Delete this testcase?', () => {
      this.apiService.deleteTestCase(id).subscribe({
        next: () => {
          if (this.showTestCases) {
            this.viewTestCases(this.selectedProblemId!, this.selectedProblemTitle);
          } else {
            this.loadTestCasesForCurrent();
          }
        },
        error: (err) => this.showAlert(err.error?.error || 'Failed to delete testcase')
      });
    });
  }



  private resetProblemForm(): void {
    this.title = ''; this.description = ''; this.constraints = '';
    this.difficulty = 'EASY'; this.inputStructure = ''; this.outputStructure = '';
    this.inputStructureItems = ['']; this.outputStructureItems = [''];
    this.step = 1;
    this.selectedProblemId = null;
    this.selectedProblemTitle = '';
    this.testCases = [];
  }
}
