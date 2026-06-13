/*
 * File: students.component.ts
 * Purpose: Invigilator student directory panel. Provides search, single student additions, and local CSV bulk importing.
 */

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-admin-students',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './students.component.html'
})
export class StudentsComponent implements OnInit {
  students: any[] = [];
  filteredStudents: any[] = [];
  searchQuery = '';
  showAddModal = false;
  showImportModal = false;
  
  // Single User Form
  newName = '';
  newRoll = '';
  newBranch = 'CSE';
  
  // Import Progress
  importing = false;
  importProgress = '';
  private importList: any[] = [];

  constructor(private apiService: ApiService) {}

  ngOnInit(): void {
    this.loadStudents();
  }

  loadStudents(): void {
    this.apiService.getAdminUsers().subscribe({
      next: (data) => {
        const raw = data.content || data || [];
        this.students = raw.map((u: any) => ({
          name: u.name,
          rollNumber: u.rollNo,
          branch: u.branch
        }));
        this.filter();
      }
    });
  }

  filter(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filteredStudents = this.students.filter(s => 
      s.name.toLowerCase().includes(q) || s.rollNumber.toLowerCase().includes(q)
    );
  }

  addStudent(): void {
    if (!this.newName || !this.newRoll) return;
    this.apiService.createStudent({
      name: this.newName,
      rollNo: this.newRoll.toUpperCase(),
      branch: this.newBranch.toUpperCase(),
      role: 'STUDENT'
    }).subscribe({
      next: () => {
        this.loadStudents();
        this.showAddModal = false;
        this.newName = '';
        this.newRoll = '';
      },
      error: (err) => alert(err.error?.error || 'Failed to create student')
    });
  }

  onFileSelected(event: any): void {
    const file: File = event.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result as string;
      this.parseCSV(text);
    };
    reader.readAsText(file);
  }

  parseCSV(text: string): void {
    const lines = text.split('\n');
    this.importList = [];
    
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;
      const parts = line.split(',');
      if (parts.length >= 2) {
        this.importList.push({
          name: parts[0].trim(),
          rollNo: parts[1].trim().toUpperCase(),
          branch: (parts[2] || 'CSE').trim().toUpperCase(),
          role: 'STUDENT'
        });
      }
    }
    this.importProgress = `Loaded ${this.importList.length} students from file. Ready to import.`;
  }

  runImport(): void {
    if (this.importList.length === 0) return;
    this.importing = true;
    this.importNext(0);
  }

  private importNext(index: number): void {
    if (index >= this.importList.length) {
      this.importProgress = `Import complete. ${this.importList.length} students loaded.`;
      this.importing = false;
      this.importList = [];
      this.loadStudents();
      return;
    }

    this.importProgress = `Importing (${index + 1}/${this.importList.length}): ${this.importList[index].name}`;
    
    this.apiService.createStudent(this.importList[index]).subscribe({
      next: () => this.importNext(index + 1),
      error: () => this.importNext(index + 1) // Continue importing others on error
    });
  }
}
