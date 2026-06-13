/*
 * File: api.service.ts
 * Purpose: Handles all REST API requests to the Spring Boot cpj backend.
 */

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl: string;

  constructor(private http: HttpClient) {
    const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
    this.baseUrl = `http://${host}:8080/api`;
  }

  private getHeaders(isMultipart = false): HttpHeaders {
    const token = localStorage.getItem('cpj_token');
    let h = new HttpHeaders();
    if (!isMultipart) h = h.set('Content-Type', 'application/json');
    return token ? h.set('X-Roll-No', token) : h;
  }

  // --- Auth endpoints ---
  sendOtp(rollNumber: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/student/login/send-otp`, { rollNumber }, { headers: this.getHeaders() });
  }

  verifyOtp(rollNumber: string, otp: string, contestId?: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/student/login/verify-otp`, { rollNumber, otp, contestId }, { headers: this.getHeaders() });
  }

  loginAdmin(username: string, password: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/login`, { username, password }, { headers: this.getHeaders() });
  }

  // --- Student Contests ---
  getContests(): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests`, { headers: this.getHeaders() });
  }

  getCurrentContest(): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests/current`, { headers: this.getHeaders() });
  }

  getContestProblems(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/contests/${contestId}/problems`, { headers: this.getHeaders() });
  }

  registerForContest(contestId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/contests/${contestId}/register`, {}, { headers: this.getHeaders() });
  }

  submitContest(contestId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/contests/${contestId}/submit`, {}, { headers: this.getHeaders() });
  }

  getStudentRegistrations(rollNumber: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/students/${rollNumber}/registrations`, { headers: this.getHeaders() });
  }

  // --- Execution and Submissions ---
  compileCode(body: { contestId: number, questionId: number, language: string, code: string, customInput: string }): Observable<any> {
    return this.http.post(`${this.baseUrl}/compile`, body, { headers: this.getHeaders() });
  }

  submitCode(body: { contestId: number, questionId: number, rollNumber: string, language: string, code: string }): Observable<any> {
    return this.http.post(`${this.baseUrl}/submit`, body, { headers: this.getHeaders() });
  }

  getSubmissions(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/submissions?contestId=${contestId}`, { headers: this.getHeaders() });
  }

  getLeaderboard(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/leaderboard?contestId=${contestId}`, { headers: this.getHeaders() });
  }

  // --- Admin Contests ---
  getAdminContests(): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/contests`, { headers: this.getHeaders() });
  }

  createContest(body: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/contests`, body, { headers: this.getHeaders() });
  }

  updateContest(id: number, body: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/admin/contests/${id}`, body, { headers: this.getHeaders() });
  }

  endContest(id: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/contests/${id}/end`, {}, { headers: this.getHeaders() });
  }

  getAdminContestSubmissions(contestId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/contests/${contestId}/submissions`, { headers: this.getHeaders() });
  }

  // --- Admin Problems ---
  getAdminProblems(): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/problems`, { headers: this.getHeaders() });
  }

  createProblem(body: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/problems`, body, { headers: this.getHeaders() });
  }

  updateProblem(id: number, body: any): Observable<any> {
    return this.http.put(`${this.baseUrl}/admin/problems/${id}`, body, { headers: this.getHeaders() });
  }

  // --- Admin Test Cases ---
  createTestCase(problemId: number, body: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/problems/${problemId}/test-cases`, body, { headers: this.getHeaders() });
  }

  uploadTestCaseCSV(problemId: number, file: File): Observable<any> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post(`${this.baseUrl}/admin/problems/${problemId}/test-cases/csv`, fd, { headers: this.getHeaders(true) });
  }

  getTestCases(problemId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/admin/problems/${problemId}/test-cases`, { headers: this.getHeaders() });
  }

  deleteTestCase(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/admin/test-cases/${id}`, { headers: this.getHeaders() });
  }

  // --- Admin Users ---
  getAdminUsers(query?: string, page = 0, size = 10000): Observable<any> {
    let url = `${this.baseUrl}/admin/users?page=${page}&size=${size}`;
    if (query) url += `&query=${encodeURIComponent(query)}`;
    return this.http.get(url, { headers: this.getHeaders() });
  }

  createStudent(body: { name: string, rollNo: string, branch: string, role: string }): Observable<any> {
    return this.http.post(`${this.baseUrl}/admin/users`, body, { headers: this.getHeaders() });
  }
  updateStudent(id: number, body: { name: string, rollNo: string, branch: string, role: string }): Observable<any> {
    return this.http.put(`${this.baseUrl}/admin/users/${id}`, body, { headers: this.getHeaders() });
  }
  deleteStudent(id: number): Observable<any> {
    return this.http.delete(`${this.baseUrl}/admin/users/${id}`, { headers: this.getHeaders() });
  }
}
