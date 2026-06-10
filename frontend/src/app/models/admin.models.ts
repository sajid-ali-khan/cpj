export type UserRole = 'STUDENT' | 'ADMIN';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type ContestStatus = 'UPCOMING' | 'ONGOING' | 'ENDED';

export interface AdminUser {
  id: number;
  name: string;
  rollNo: string;
  branch: string | null;
  role: UserRole;
}

export interface AdminProblem {
  id: number;
  title: string;
  description: string;
  constraints: string | null;
  difficulty: Difficulty | null;
  mediaLink: string | null;
  testCaseCount: number;
}

export interface AdminTestCase {
  id: number;
  problemId: number;
  stdin: string | null;
  expectedOutput: string;
  isSample: boolean;
}

export interface ContestProblemItem {
  problemId: number;
  title: string;
  difficulty: Difficulty | null;
  points: number;
  displayOrder: number;
}

export interface AdminContest {
  id: number;
  title: string;
  startTime: string;
  durationMins: number;
  status: ContestStatus;
  problems: ContestProblemItem[];
}

export interface ContestProblemSummary {
  problemId: number;
  title: string;
  description: string;
  constraints: string | null;
  difficulty: Difficulty | null;
  points: number;
  displayOrder: number;
}

export interface ContestSummary {
  id: number;
  title: string;
  startTime: string;
  durationMins: number;
  status: ContestStatus;
}
