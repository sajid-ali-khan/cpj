export type Verdict =
  | 'PENDING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR';

export interface SubmitResponse {
  submissionId: number;
}

export interface SubmissionResponse {
  id: number;
  problemId: number;
  languageId: number;
  code: string;
  verdict: Verdict;
  timeMs: number | null;
  memoryKb: number | null;
  submittedAt: string;
}

export interface SubmissionRequest {
  contestId: number;
  problemId: number;
  code: string;
  languageId: number;
}

export interface VerdictEvent {
  submissionId: number;
  problemId: number;
  verdict: Verdict;
  timeMs: number | null;
  memoryKb: number | null;
}

export interface LeaderboardEntry {
  rank: number;
  userId: number;
  name: string;
  rollNo: string;
  score: number;
  lastAcTime: string | null;
}

export interface LanguageOption {
  id: number;
  label: string;
}

export const LANGUAGES: LanguageOption[] = [
  { id: 54, label: 'C++' },
  { id: 62, label: 'Java' },
  { id: 71, label: 'Python 3' },
  { id: 50, label: 'C' },
];
