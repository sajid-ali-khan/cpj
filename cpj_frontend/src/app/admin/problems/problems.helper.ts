/*
 * File: problems.helper.ts
 * Purpose: Helper utilities for problem management (truncation, sizing, CSV parsing).
 */

export interface CSVTestCase {
  stdin: string;
  expectedOutput: string;
  isSample: boolean;
}

export function truncateText(text: string, limit = 80): string {
  if (!text) return '—';
  const cleaned = text.trim();
  return cleaned.length > limit ? cleaned.substring(0, limit) + '...' : cleaned;
}

export function getSizeLabel(text: string): string {
  if (!text) return '0 B (0 lines)';
  const bytes = new Blob([text]).size;
  const lines = text.split('\n').filter(l => l.trim()).length;
  const sizeStr = bytes > 1024 ? (bytes / 1024).toFixed(1) + ' KB' : bytes + ' B';
  return `${sizeStr} (${lines} line${lines === 1 ? '' : 's'})`;
}

export function parseTestCaseCSV(text: string): CSVTestCase[] {
  const lines = text.split('\n');
  const results: CSVTestCase[] = [];
  const startIdx = lines[0].toLowerCase().includes('input') || lines[0].toLowerCase().includes('stdin') ? 1 : 0;

  for (let i = startIdx; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;
    const parts = parseCSVLine(line);
    if (parts.length >= 2) {
      results.push({
        stdin: parts[0].replace(/\\n/g, '\n').trim(),
        expectedOutput: parts[1].replace(/\\n/g, '\n').trim(),
        isSample: parts[2]?.trim().toUpperCase() === 'TRUE' || parts[2]?.trim() === '1'
      });
    }
  }
  return results;
}

function parseCSVLine(line: string): string[] {
  const result: string[] = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (char === '"') {
      inQuotes = !inQuotes;
    } else if (char === ',' && !inQuotes) {
      result.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  result.push(current);
  return result.map(p => p.trim().replace(/^"|"$/g, ''));
}
