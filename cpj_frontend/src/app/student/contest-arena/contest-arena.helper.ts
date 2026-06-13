/*
 * File: contest-arena.helper.ts
 * Purpose: Helper utilities, templates, and mappings for the coding arena component.
 */

export const DEFAULT_TEMPLATES: Record<string, string> = {
  cpp: '#include <iostream>\nusing namespace std;\nint main() {\n    return 0;\n}',
  python: 'def main():\n    pass\nif __name__ == "__main__":\n    main()',
  java: 'import java.util.*;\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n    }\n}'
};

export function getCodeTemplate(lang: string, problem: any): string {
  return problem?.templates?.[lang] || DEFAULT_TEMPLATES[lang] || '';
}

export function getSaveKey(contestId: number, problemId: number, lang: string): string {
  return `code_${contestId}_${problemId}_${lang}`;
}

export function getSavedCode(contestId: number, problemId: number, lang: string, problem: any): string {
  if (!problemId) return '';
  const key = getSaveKey(contestId, problemId, lang);
  return localStorage.getItem(key) || getCodeTemplate(lang, problem);
}

export function saveCode(contestId: number, problemId: number, lang: string, code: string): void {
  if (!problemId) return;
  const key = getSaveKey(contestId, problemId, lang);
  localStorage.setItem(key, code);
}

export function getMonacoLanguage(lang: string): string {
  return lang === 'cpp' ? 'cpp' : lang === 'python' ? 'python' : 'java';
}

export const VERDICT_MAPPINGS: Record<string, string> = {
  ACCEPTED: 'Accepted',
  WRONG_ANSWER: 'Wrong Answer',
  TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
  MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded',
  RUNTIME_ERROR: 'Runtime Error'
};

export function getVerdictLabel(verdict: string): string {
  return VERDICT_MAPPINGS[verdict] || 'Compilation Error';
}
