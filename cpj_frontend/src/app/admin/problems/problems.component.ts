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

  // Pagination & Search
  searchQuery = '';
  pageSize = 10;
  currentPage = 1;

  // New Details Modal State
  selectedProblemDetail: any = null;

  // Tab control
  activeDetailTab = 'description';

  // Zip upload state
  selectedZipFile: File | null = null;
  uploadingZip = false;

  // Calibration state
  calibrationLanguage = 'cpp';
  calibrationCode = '';
  calibrating = false;
  calibrationResult: any = null;


  // Edit Test Case Modal State
  showEditTestCase = false;
  editingTestCaseId: number | null = null;
  editTcInput = '';
  editTcOutput = '';
  editTcIsSample = false;

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

    this.apiService.createProblem({
      title: this.title, description: this.description, constraints: this.constraints,
      difficulty: this.difficulty, inputStructure: this.inputStructure, outputStructure: this.outputStructure
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

  get filteredProblems(): any[] {
    if (!this.searchQuery) return this.problems;
    const q = this.searchQuery.toLowerCase().trim();
    return this.problems.filter(p => p.title.toLowerCase().includes(q));
  }

  get paginatedProblems(): any[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredProblems.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.ceil(this.filteredProblems.length / this.pageSize) || 1;
  }

  prevPage(): void {
    if (this.currentPage > 1) this.currentPage--;
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) this.currentPage++;
  }

  viewProblemDetail(id: number): void {
    this.selectedProblemId = id;
    this.activeDetailTab = 'description';
    this.calibrationResult = null;
    this.calibrationCode = '';
    this.selectedZipFile = null;
    this.apiService.getAdminProblem(id).subscribe({
      next: (data) => {
        this.selectedProblemDetail = data;
        this.selectedProblemTitle = data.title;
        this.loadTestCasesForCurrent();
      },
      error: (err) => this.showAlert(err.error?.error || 'Failed to retrieve problem details')
    });
  }

  closeProblemDetail(): void {
    this.selectedProblemDetail = null;
    this.selectedProblemId = null;
    this.selectedProblemTitle = '';
    this.testCases = [];
    this.activeDetailTab = 'description';
    this.calibrationResult = null;
    this.calibrationCode = '';
    this.selectedZipFile = null;
  }

  onZipFileSelected(event: any): void {
    const file = event.target?.files?.[0];
    if (file) {
      this.selectedZipFile = file;
    }
  }

  uploadZipFile(): void {
    if (this.selectedProblemId === null || !this.selectedZipFile) return;
    this.uploadingZip = true;
    this.apiService.uploadTestCaseZip(this.selectedProblemId, this.selectedZipFile).subscribe({
      next: (data) => {
        this.uploadingZip = false;
        this.selectedZipFile = null;
        this.loadTestCasesForCurrent();
        this.showAlert('Successfully uploaded ' + data.length + ' test cases!');
      },
      error: (err) => {
        this.uploadingZip = false;
        this.showAlert(err.error?.error || 'Failed to upload test cases ZIP');
      }
    });
  }

  runCalibration(): void {
    if (this.selectedProblemId === null || !this.calibrationCode) return;
    this.calibrating = true;
    this.calibrationResult = null;
    const payload = {
      language: this.calibrationLanguage,
      code: this.calibrationCode
    };
    this.apiService.calibrateLimits(this.selectedProblemId, payload).subscribe({
      next: (res) => {
        this.calibrating = false;
        this.calibrationResult = res;

        // Refresh selected problem detail to update limits shown in the description tab
        this.apiService.getAdminProblem(this.selectedProblemId!).subscribe(data => {
          this.selectedProblemDetail = data;
        });

        this.showAlert('Calibration complete! New resource limits have been saved.');
      },
      error: (err) => {
        this.calibrating = false;
        this.showAlert(err.error?.error || 'Calibration failed');
      }
    });
  }


  deleteProblem(id: number): void {
    this.showConfirm('Are you sure you want to delete this problem? By default this performs a soft-delete.', () => {
      this.apiService.deleteProblem(id, false).subscribe({
        next: () => {
          this.loadProblems();
          this.showAlert('Problem soft-deleted successfully.');
        },
        error: (err) => this.showAlert(err.error?.error || 'Failed to delete problem')
      });
    });
  }

  openEditTestCase(tc: any): void {
    this.editingTestCaseId = tc.id;
    this.editTcInput = tc.stdin;
    this.editTcOutput = tc.expectedOutput;
    this.editTcIsSample = tc.isSample;
    this.showEditTestCase = true;
  }

  saveEditedTestCase(): void {
    if (this.editingTestCaseId === null) return;
    const body = {
      stdin: this.editTcInput,
      expectedOutput: this.editTcOutput,
      isSample: this.editTcIsSample
    };
    this.apiService.updateTestCase(this.editingTestCaseId, body).subscribe({
      next: () => {
        this.showEditTestCase = false;
        this.editingTestCaseId = null;
        this.editTcInput = '';
        this.editTcOutput = '';
        this.editTcIsSample = false;
        this.loadTestCasesForCurrent();
        if (this.showTestCases) {
          this.viewTestCases(this.selectedProblemId!, this.selectedProblemTitle);
        }
        this.showAlert('Test case updated successfully.');
      },
      error: (err) => this.showAlert(err.error?.error || 'Failed to update test case')
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
