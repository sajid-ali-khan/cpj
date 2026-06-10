# CPJ API Endpoints - Flow & Examples

## Overview

All requests require the header: `X-Roll-No: <student_roll_number>` or `X-Roll-No: ADMIN001` for admin operations.

The system uses **roll number** as authentication (no password). Admin operations require `ADMIN` role.

---

## Table of Contents

1. [Student: Contests](#student-contests)
2. [Student: Submissions](#student-submissions)
3. [Student: Leaderboard](#student-leaderboard)
4. [Student: Real-Time Events (SSE)](#student-real-time-events-sse)
5. [Admin: User Management](#admin-user-management)
6. [Admin: Problem Management](#admin-problem-management)
7. [Admin: Test Case Management](#admin-test-case-management)
8. [Admin: Contest Management](#admin-contest-management)

---

# STUDENT APIs

## Student: Contests

### GET /api/contests/current
**Purpose**: Get the currently active (ONGOING) contest.

**Request**:
```http
GET /api/contests/current
Header: X-Roll-No: STUDENT001
```

**Response** (200 OK):
```json
{
  "id": 1,
  "title": "DSA Lab - Week 1",
  "startTime": "2026-06-10T14:00:00",
  "durationMins": 120,
  "status": "ONGOING"
}
```

**Response** (404 Not Found):
```json
{
  "error": "No ongoing contest"
}
```

**Flow**:
1. Extract roll# from `X-Roll-No` header
2. Create/get User from DB by roll#
3. Query `contests` table where `status = 'ONGOING'`
4. If found → return contest summary
5. If not found → return 404

**Key Points**:
- Only one contest can be ONGOING at a time (enforced by admin start logic)
- Students check this before viewing arena

---

### GET /api/contests/{id}/problems
**Purpose**: Get list of problems in a contest with their details.

**Request**:
```http
GET /api/contests/1/problems
Header: X-Roll-No: STUDENT001
```

**Response** (200 OK):
```json
[
  {
    "problemId": 10,
    "title": "Two Sum",
    "description": "Given an array of integers, find two numbers that add up to a target.",
    "constraints": "1 <= n <= 10^5, -10^9 <= nums[i] <= 10^9",
    "difficulty": "EASY",
    "points": 100,
    "displayOrder": 1
  },
  {
    "problemId": 11,
    "title": "Longest Substring",
    "description": "Find the length of the longest substring without repeating characters.",
    "constraints": "0 <= s.length <= 5 * 10^4",
    "difficulty": "MEDIUM",
    "points": 150,
    "displayOrder": 2
  }
]
```

**Flow**:
1. Verify contest exists
2. Query `contest_problems` JOIN `problems` WHERE `contest_id = {id}`
3. Sort by `display_order`
4. Return problem list with points

**Key Points**:
- Problems are ordered for the contest UI
- Points shown to student (so they know what each problem is worth)
- Same problem can be in multiple contests with different point values

---

# STUDENT APIs - Submissions

## POST /api/submissions
**Purpose**: Submit code for a problem in the current contest.

**Request**:
```http
POST /api/submissions
Header: X-Roll-No: STUDENT001
Content-Type: application/json

{
  "contestId": 1,
  "problemId": 10,
  "code": "def two_sum(nums, target):\n    seen = {}\n    for num in nums:\n        complement = target - num\n        if complement in seen:\n            return [seen[complement], nums.index(num)]\n        seen[num] = nums.index(num)\n    return []",
  "languageId": 71
}
```

**Response** (200 OK):
```json
{
  "submissionId": 1001
}
```

**Response** (400 Bad Request):
```json
{
  "error": "Contest is not ongoing"
}
```

**Flow**:
1. **Validation** (SubmissionService.validateSubmitRequest):
   - contestId required, not null
   - problemId required, not null
   - code not empty
   - languageId valid (71 = Python3, 50 = C, 54 = C++, etc.)

2. **Contest Check**:
   - Query `contests` WHERE `id = contestId`
   - If status ≠ `ONGOING` → return 400 "Contest is not ongoing"

3. **Problem Check**:
   - Query `contest_problems` WHERE `contest_id = contestId AND problem_id = problemId`
   - If not found → return 400 "Problem is not part of this contest"

4. **Save Submission** (Submission entity):
   ```
   user_id = current user from context
   contest_id = contestId
   problem_id = problemId
   code = code string (stored as-is)
   language_id = languageId
   verdict = 'PENDING' (initial)
   judge0_token = null
   submitted_at = NOW()
   ```

5. **Trigger Async Judging** (JudgeDispatchService.dispatch):
   - Spawn async thread
   - Load all non-sample test cases (or samples if no hidden ones)
   - Loop through each:
     - Send to Judge0 with `wait=true`
     - Get result immediately
     - If verdict ≠ ACCEPTED → break (fail fast)
   - Send final verdict to SubmissionResultService.finalize()

6. **Return** submissionId immediately (async continues in background)

**Async Result Processing** (SubmissionResultService.finalize):
1. Update submission verdict, time_ms, memory_kb
2. Check if first-time AC for this (user, problem, contest):
   - Query if `ACCEPTED` submission already exists for this user+problem+contest
   - If no → this is first AC
3. Send verdict to user via SSE (private)
4. If first AC:
   - Update leaderboard: score += contestProblem.points
   - Broadcast updated leaderboard to all users via SSE

**Example Timeline**:
```
14:05:00 - Student submits → Submission 1001 created, returns immediately
14:05:02 - JudgeDispatchService runs async:
           - Load 3 hidden test cases for problem 10
           - Test 1: ACCEPTED
           - Test 2: ACCEPTED
           - Test 3: ACCEPTED
           → Final verdict: ACCEPTED
14:05:03 - SubmissionResultService.finalize():
           - Check: is this student's first AC for problem 10 in contest 1?
           - Yes → Update leaderboard, score = 100
           - Send SSE verdict to STUDENT001 (only)
           - Send SSE leaderboard to ALL connected students
```

**Language IDs** (Judge0):
- 50 = C
- 54 = C++
- 71 = Python 3
- 62 = Java
- 63 = JavaScript

---

### GET /api/submissions?contestId=1
**Purpose**: Get all submissions by current student for a contest.

**Request**:
```http
GET /api/submissions?contestId=1
Header: X-Roll-No: STUDENT001
```

**Response** (200 OK):
```json
[
  {
    "submissionId": 1001,
    "problemId": 10,
    "verdict": "ACCEPTED",
    "timeMs": 125,
    "memoryKb": 45000,
    "submittedAt": "2026-06-10T14:05:00"
  },
  {
    "submissionId": 1002,
    "problemId": 10,
    "verdict": "WRONG_ANSWER",
    "timeMs": 90,
    "memoryKb": 42000,
    "submittedAt": "2026-06-10T14:08:30"
  },
  {
    "submissionId": 1003,
    "problemId": 11,
    "verdict": "PENDING",
    "timeMs": null,
    "memoryKb": null,
    "submittedAt": "2026-06-10T14:12:00"
  }
]
```

**Flow**:
1. Extract user from X-Roll-No header
2. Query `submissions` WHERE `user_id = userId AND contest_id = contestId`
3. Order by `submitted_at DESC` (newest first)
4. Return list with verdict statuses

**Verdict Values**:
- `PENDING` - Still being judged
- `ACCEPTED` - Passed all test cases
- `WRONG_ANSWER` - Failed a test case (output mismatch)
- `TIME_LIMIT_EXCEEDED` - Exceeded CPU time limit
- `MEMORY_LIMIT_EXCEEDED` - Exceeded memory limit
- `RUNTIME_ERROR` - Crash or exception
- `COMPILATION_ERROR` - Code failed to compile
- `INTERNAL_ERROR` - Judge0 issue

**Key Points**:
- Shows all attempts for all problems in this contest
- Student can see their own verdicts and try multiple times
- PENDING means still waiting for Judge0 result

---

# STUDENT APIs - Leaderboard

### GET /api/leaderboard?contestId=1
**Purpose**: Get current leaderboard for a contest.

**Request**:
```http
GET /api/leaderboard?contestId=1
Header: X-Roll-No: STUDENT001
```

**Response** (200 OK):
```json
[
  {
    "rank": 1,
    "userId": 101,
    "name": "Alice Kumar",
    "rollNo": "CS001",
    "score": 250,
    "lastAcTime": "2026-06-10T14:12:45"
  },
  {
    "rank": 2,
    "userId": 102,
    "name": "Bob Singh",
    "rollNo": "CS002",
    "score": 250,
    "lastAcTime": "2026-06-10T14:15:30"
  },
  {
    "rank": 3,
    "userId": 103,
    "name": "Carol Patel",
    "rollNo": "CS003",
    "score": 100,
    "lastAcTime": "2026-06-10T14:09:12"
  }
]
```

**Flow**:
1. Query `leaderboard` WHERE `contest_id = contestId`
2. Sort by `score DESC, last_ac_time ASC`
3. Assign rank = position in sorted list (1-indexed)
4. Return with user details

**Scoring Logic**:
- Student gets points only on **first-time AC** for each problem
- If problem worth 100 pts and contest has 3 problems → max score = 300
- Tiebreaker: student who achieved last AC earlier ranks higher (time ascending)

**Example Ranking**:
```
Alice: 2 problems AC (100+150=250), last AC at 14:12:45
Bob:   2 problems AC (100+150=250), last AC at 14:15:30
Carol: 1 problem AC (100), last AC at 14:09:12

Rank 1: Alice (same score as Bob, but earlier last AC)
Rank 2: Bob
Rank 3: Carol
```

**Key Points**:
- Does NOT show failed attempts
- Does NOT show code or other sensitive info
- Updated in real-time via SSE broadcasts

---

# STUDENT APIs - Real-Time Events (SSE)

## GET /api/events?rollNo=STUDENT001
**Purpose**: Open Server-Sent Events stream for real-time verdict and leaderboard updates.

**Request** (Browser automatically maintains connection):
```http
GET /api/events?rollNo=STUDENT001
Header: Accept: text/event-stream
```

**Response Stream** (continuous):

**Event 1 - Verdict Event** (when student's submission completes):
```
event: verdict
data: {"submissionId":1001,"problemId":10,"verdict":"ACCEPTED","timeMs":125,"memoryKb":45000}

```

**Event 2 - Leaderboard Broadcast** (when anyone gets first AC):
```
event: leaderboard
data: [{"rank":1,"userId":101,"name":"Alice Kumar","rollNo":"CS001","score":250,"lastAcTime":"2026-06-10T14:12:45"},{"rank":2,"userId":102,"name":"Bob Singh","rollNo":"CS002","score":250,"lastAcTime":"2026-06-10T14:15:30"}]

```

**Connection Lifecycle**:

```
1. Student connects → GET /api/events?rollNo=STUDENT001
2. Backend:
   - SseService.connect(userId) 
   - Creates SseEmitter
   - Stores in ConcurrentHashMap<userId, emitter>
   - Sets up auto-cleanup on timeout (30 min), error, completion
3. Connection open (shows "Connected" in UI)

4. [Student submits code]
   - SubmissionService saves it
   - JudgeDispatchService runs async
   - Judge0 returns result
   - SubmissionResultService.finalize() calls:
     - sseService.sendVerdict(userId, event) → sends to THIS student only
     - sseService.broadcastLeaderboard(entries) → sends to ALL connected students

5. Student receives verdict event (private)
6. All students receive leaderboard event (broadcast)

7. Student disconnects or 30 min passes:
   - Emitter.onTimeout() or onCompletion()
   - SseService removes from map
   - Connection closes
```

**Frontend SSE Service** (Angular):

```typescript
// src/app/services/sse.service.ts
connect(rollNo: string) {
  const source = new EventSource(`/api/events?rollNo=${rollNo}`);
  
  source.addEventListener('verdict', (event) => {
    const verdictData = JSON.parse(event.data);
    this.verdict$.next(verdictData);
    // Frontend updates submissions list, refreshes UI
  });
  
  source.addEventListener('leaderboard', (event) => {
    const leaderboardData = JSON.parse(event.data);
    this.leaderboard$.next(leaderboardData);
    // Frontend updates leaderboard table
  });
}
```

**Key Points**:
- SSE = Server-Sent Events (one-way from server to client)
- Long-lived connection (30 min timeout)
- Multiple students can be connected simultaneously
- Private verdicts only reach the submitting student
- Leaderboard broadcasts reach all connected students

---

# ADMIN APIs

## Admin: User Management

### POST /api/admin/users
**Purpose**: Create a new user.

**Request**:
```http
POST /api/admin/users
Header: X-Roll-No: ADMIN001
Content-Type: application/json

{
  "name": "Alice Kumar",
  "rollNo": "CS001",
  "branch": "CSE",
  "role": "STUDENT"
}
```

**Response** (201 Created):
```json
{
  "id": 101,
  "name": "Alice Kumar",
  "rollNo": "CS001",
  "branch": "CSE",
  "role": "STUDENT"
}
```

**Response** (400 Bad Request):
```json
{
  "error": "Roll number already exists"
}
```

**Authorization**:
- Requires `X-Roll-No: ADMIN001` (or any user with ADMIN role)
- Non-admins get 403 Forbidden

**Flow** (AdminUserService.create):
1. Check roll# not already taken (unique constraint)
2. Validate role is valid (ADMIN or STUDENT)
3. Insert into `users` table
4. Return created user

**Database Insert**:
```sql
INSERT INTO users (name, roll_no, branch, role) 
VALUES ('Alice Kumar', 'CS001', 'CSE', 'STUDENT');
```

---

### GET /api/admin/users
**Purpose**: List all users.

**Request**:
```http
GET /api/admin/users
Header: X-Roll-No: ADMIN001
```

**Response** (200 OK):
```json
[
  {
    "id": 1,
    "name": "System Admin",
    "rollNo": "ADMIN001",
    "branch": "ADMIN",
    "role": "ADMIN"
  },
  {
    "id": 101,
    "name": "Alice Kumar",
    "rollNo": "CS001",
    "branch": "CSE",
    "role": "STUDENT"
  },
  {
    "id": 102,
    "name": "Bob Singh",
    "rollNo": "CS002",
    "branch": "CSE",
    "role": "STUDENT"
  }
]
```

**Flow**:
1. Query all rows from `users` table
2. Return list

---

## Admin: Problem Management

### POST /api/admin/problems
**Purpose**: Create a new problem.

**Request**:
```http
POST /api/admin/problems
Header: X-Roll-No: ADMIN001
Content-Type: application/json

{
  "title": "Two Sum",
  "description": "Given an array of integers nums and an integer target, return the indices of the two numbers that add up to target. You may assume each input has exactly one solution, and you cannot use the same element twice.",
  "difficulty": "EASY",
  "constraints": "2 <= nums.length <= 10^4, -10^9 <= nums[i] <= 10^9, -10^9 <= target <= 10^9",
  "mediaLink": "https://example.com/problem-image.png"
}
```

**Response** (201 Created):
```json
{
  "id": 10,
  "title": "Two Sum",
  "description": "Given an array of integers...",
  "difficulty": "EASY",
  "constraints": "2 <= nums.length <= 10^4...",
  "mediaLink": "https://example.com/problem-image.png",
  "testCaseCount": 0
}
```

**Flow** (AdminProblemService.create):
1. Insert into `problems` table
2. Return created problem with testCaseCount = 0 (no test cases yet)

---

### GET /api/admin/problems
**Purpose**: List all problems (for admin to manage).

**Request**:
```http
GET /api/admin/problems
Header: X-Roll-No: ADMIN001
```

**Response** (200 OK):
```json
[
  {
    "id": 10,
    "title": "Two Sum",
    "difficulty": "EASY",
    "testCaseCount": 5
  },
  {
    "id": 11,
    "title": "Longest Substring",
    "difficulty": "MEDIUM",
    "testCaseCount": 3
  }
]
```

---

## Admin: Test Case Management

### POST /api/admin/problems/{problemId}/test-cases
**Purpose**: Add a test case to a problem.

**Request**:
```http
POST /api/admin/problems/10/test-cases
Header: X-Roll-No: ADMIN001
Content-Type: application/json

{
  "stdin": "2\n7\n[1,2,7,11,15] 9",
  "expectedOutput": "[0,1]",
  "isSample": false
}
```

**Response** (201 Created):
```json
{
  "id": 1001,
  "problemId": 10,
  "stdin": "2\n7\n[1,2,7,11,15] 9",
  "expectedOutput": "[0,1]",
  "isSample": false
}
```

**Test Case Types**:
- **Sample** (`isSample: true`) - Visible to students in problem statement
- **Hidden** (`isSample: false`) - Used only for judging (student doesn't see input/output)

**Flow** (AdminTestCaseService.create):
1. Verify problem exists
2. Insert into `test_cases` table
3. Return created test case

**Database Insert**:
```sql
INSERT INTO test_cases (problem_id, stdin, expected_output, is_sample)
VALUES (10, '2\n7\n[1,2,7,11,15] 9', '[0,1]', false);
```

**Judging Logic**:
When submission comes in:
- Check if problem has any hidden test cases
- If yes → use only hidden ones for judging
- If no → use sample test cases

```java
// From JudgeDispatchService.loadJudgeTestCases()
List<TestCase> hidden = testCaseRepository.findByProblemId(problemId)
    .stream()
    .filter(tc -> !tc.isSample())
    .toList();
if (!hidden.isEmpty()) {
    return hidden;  // Use hidden
}
return testCaseRepository.findByProblemId(problemId);  // Use samples
```

---

### GET /api/admin/problems/{problemId}/test-cases
**Purpose**: List all test cases for a problem.

**Request**:
```http
GET /api/admin/problems/10/test-cases
Header: X-Roll-No: ADMIN001
```

**Response** (200 OK):
```json
[
  {
    "id": 1001,
    "stdin": "2\n7\n[1,2,7,11,15] 9",
    "expectedOutput": "[0,1]",
    "isSample": false
  },
  {
    "id": 1002,
    "stdin": "3\n6\n[3,2,4] 6",
    "expectedOutput": "[1,2]",
    "isSample": false
  },
  {
    "id": 1003,
    "stdin": "2\n5\n[3,3] 6",
    "expectedOutput": "[0,1]",
    "isSample": true
  }
]
```

---

### DELETE /api/admin/test-cases/{testCaseId}
**Purpose**: Delete a test case.

**Request**:
```http
DELETE /api/admin/test-cases/1001
Header: X-Roll-No: ADMIN001
```

**Response** (204 No Content):
```
(empty body)
```

**Flow**:
1. Verify test case exists
2. Delete from `test_cases` table
3. Return 204

---

## Admin: Contest Management

### POST /api/admin/contests
**Purpose**: Create a new contest with problems.

**Request**:
```http
POST /api/admin/contests
Header: X-Roll-No: ADMIN001
Content-Type: application/json

{
  "title": "DSA Lab - Week 1",
  "startTime": "2026-06-10T14:00:00",
  "durationMins": 120,
  "problems": [
    {
      "problemId": 10,
      "points": 100,
      "displayOrder": 1
    },
    {
      "problemId": 11,
      "points": 150,
      "displayOrder": 2
    }
  ]
}
```

**Response** (201 Created):
```json
{
  "id": 1,
  "title": "DSA Lab - Week 1",
  "startTime": "2026-06-10T14:00:00",
  "durationMins": 120,
  "status": "UPCOMING",
  "problems": [
    {
      "problemId": 10,
      "title": "Two Sum",
      "points": 100,
      "displayOrder": 1
    },
    {
      "problemId": 11,
      "title": "Longest Substring",
      "points": 150,
      "displayOrder": 2
    }
  ]
}
```

**Flow** (AdminContestService.create):
1. Validate all problems exist
2. Insert into `contests` table with status = `UPCOMING`
3. For each problem, insert into `contest_problems` table:
   ```sql
   INSERT INTO contest_problems (contest_id, problem_id, points, display_order)
   VALUES (1, 10, 100, 1);
   ```
4. Return created contest with problems

**Key Points**:
- Contest starts in UPCOMING status (hidden from students)
- Admin must explicitly START it to make it visible
- Points are contest-specific (same problem can be worth different points in different contests)

---

### GET /api/admin/contests
**Purpose**: List all contests.

**Request**:
```http
GET /api/admin/contests
Header: X-Roll-No: ADMIN001
```

**Response** (200 OK):
```json
[
  {
    "id": 1,
    "title": "DSA Lab - Week 1",
    "startTime": "2026-06-10T14:00:00",
    "durationMins": 120,
    "status": "UPCOMING",
    "problems": [
      {
        "problemId": 10,
        "title": "Two Sum",
        "points": 100
      },
      {
        "problemId": 11,
        "title": "Longest Substring",
        "points": 150
      }
    ]
  },
  {
    "id": 2,
    "title": "DSA Lab - Week 2",
    "startTime": "2026-06-17T14:00:00",
    "durationMins": 120,
    "status": "UPCOMING",
    "problems": []
  }
]
```

---

### POST /api/admin/contests/{id}/start
**Purpose**: Start a contest (change status to ONGOING).

**Request**:
```http
POST /api/admin/contests/1/start
Header: X-Roll-No: ADMIN001
```

**Response** (200 OK):
```json
{
  "id": 1,
  "title": "DSA Lab - Week 1",
  "startTime": "2026-06-10T14:00:00",
  "durationMins": 120,
  "status": "ONGOING",
  "problems": [...]
}
```

**Flow** (AdminContestService.start):
1. Find contest by id
2. Check if already ENDED → if yes, reject (can't restart ended contest)
3. Find any other contest with status = ONGOING
4. If found → set its status to ENDED (auto-end previous)
5. Set this contest status to ONGOING
6. Commit to DB
7. Return updated contest

**Important Rule**: Only ONE contest can be ONGOING at a time.

**Timeline**:
```
14:00:00 - Admin calls /api/admin/contests/1/start
           Contest 1 status = UPCOMING → ONGOING
           If another contest was ONGOING → auto-end it
           
14:00:00 - Students now see this contest in /api/contests/current
           
14:00:01 - Students can join arena and start submitting
           
14:00:02 - Each submission is judged, verdicts SSE-pushed in real-time
```

---

### POST /api/admin/contests/{id}/end
**Purpose**: End a contest (change status to ENDED).

**Request**:
```http
POST /api/admin/contests/1/end
Header: X-Roll-No: ADMIN001
```

**Response** (200 OK):
```json
{
  "id": 1,
  "title": "DSA Lab - Week 1",
  "startTime": "2026-06-10T14:00:00",
  "durationMins": 120,
  "status": "ENDED",
  "problems": [...]
}
```

**Flow** (AdminContestService.end):
1. Find contest by id
2. Set status = ENDED
3. Commit to DB
4. Return updated contest

**What Happens After End**:
- Students still see `/api/contests/current` returns 404 (not ONGOING)
- Students cannot submit new code (SubmissionService checks contest status)
- Students can still view their submissions and leaderboard (historical data)

---

# Example: Full Contest Flow (One Student)

## Scenario
Admin Alice runs a contest with 2 students (Bob and Carol). Bob solves 1 problem, Carol solves 2.

### Timeline

**Admin Setup** (14:00-14:01):
```
1. POST /api/admin/users
   Create Bob (CS001, STUDENT)
   
2. POST /api/admin/users
   Create Carol (CS002, STUDENT)
   
3. POST /api/admin/problems
   Create "Two Sum" (EASY)
   
4. POST /api/admin/problems/{problemId}/test-cases
   Add 3 hidden test cases
   
5. POST /api/admin/contests
   Create "DSA Lab - Week 1"
   Add "Two Sum" (100 points)
   Status = UPCOMING
   
6. POST /api/admin/contests/1/start
   Status = UPCOMING → ONGOING
```

**Student Bob** (14:05):
```
1. Browser: GET /api/contests/current
   Response: Contest 1 (ONGOING)
   
2. Browser: GET /api/contests/1/problems
   Response: [Two Sum (100 points)]
   
3. Browser: GET /api/events?rollNo=CS001
   Opens SSE connection
   
4. Browser: POST /api/submissions
   Submit code for Two Sum
   Response: submissionId = 1001
   
   [Backend async judging starts]
   - Load test cases
   - Run Test 1: ACCEPTED
   - Run Test 2: ACCEPTED
   - Run Test 3: ACCEPTED
   - Verdict: ACCEPTED
   
5. [After 2 seconds] Browser receives SSE event:
   event: verdict
   data: {submissionId: 1001, verdict: "ACCEPTED", timeMs: 125, memoryKb: 45000}
   
6. Browser: GET /api/submissions?contestId=1
   Response: [Submission 1001 with ACCEPTED verdict]
   
7. Browser: GET /api/leaderboard?contestId=1
   Response: 
   [
     {rank: 1, name: "Bob Singh", score: 100, lastAcTime: "14:05:02"}
   ]
```

**Student Carol** (14:08):
```
1. Browser: GET /api/contests/current
   Response: Contest 1 (ONGOING)
   
2. Browser: GET /api/events?rollNo=CS002
   Opens SSE connection
   
3. Browser: POST /api/submissions
   Submit code for Two Sum
   Response: submissionId = 1002
   
   [Backend async judging]
   - Test 1: ACCEPTED
   - Test 2: ACCEPTED
   - Test 3: ACCEPTED
   - Verdict: ACCEPTED
   
4. [After 2 seconds] Browser receives SSE event:
   event: verdict
   data: {submissionId: 1002, verdict: "ACCEPTED", ...}
   
   BOTH Bob and Carol's browsers receive SSE event:
   event: leaderboard
   data: [{rank: 1, name: "Carol Patel", score: 100, lastAcTime: "14:08:02"},
          {rank: 2, name: "Bob Singh", score: 100, lastAcTime: "14:05:02"}]
   
   (Carol ranks higher because she got AC later, but that's tiebreaker only)
   
5. Browser: GET /api/leaderboard?contestId=1
   Response: [Carol (1st), Bob (2nd)]
```

**Admin Ends Contest** (14:59):
```
1. POST /api/admin/contests/1/end
   Status = ONGOING → ENDED
   
2. Now if Bob tries: POST /api/submissions
   Response: 400 "Contest is not ongoing"
   
3. But GET /api/leaderboard?contestId=1 still works (historical data)
```

---

# Database Relationships

```
users
├─ id (PK)
├─ name
├─ roll_no (UNIQUE)
├─ role (ADMIN or STUDENT)
└─ (1:N) submissions
   └─ (N:1) contests
   └─ (N:1) problems

problems
├─ id (PK)
├─ title
├─ description
└─ (1:N) test_cases
   └─ (N:N) contest_problems

test_cases
├─ id (PK)
├─ problem_id (FK→problems)
├─ stdin
├─ expected_output
└─ is_sample

contests
├─ id (PK)
├─ title
├─ status (UPCOMING|ONGOING|ENDED)
├─ (N:N) contest_problems
└─ (1:N) submissions
   └─ (N:1) users

contest_problems
├─ contest_id + problem_id (composite PK)
├─ points
└─ display_order

submissions
├─ id (PK)
├─ user_id (FK→users)
├─ contest_id (FK→contests)
├─ problem_id (FK→problems)
├─ code (the actual submission code)
├─ language_id
├─ judge0_token
├─ verdict (PENDING|ACCEPTED|WA|TLE|RTE|CE|IE)
├─ time_ms
├─ memory_kb
└─ submitted_at

leaderboard
├─ id (PK)
├─ contest_id (FK→contests)
├─ user_id (FK→users)
├─ score (accumulated from first ACs)
├─ last_ac_time
└─ UNIQUE(contest_id, user_id)
```

---

# Key Design Patterns

## 1. Async Submission Judging
```
POST /api/submissions
├─ Save with verdict = PENDING (immediate response)
└─ Trigger async thread (JudgeDispatchService)
   ├─ Run against test cases
   ├─ Finalize result (SubmissionResultService)
   ├─ Update verdict in DB
   ├─ Send SSE to user (private verdict)
   └─ If first AC:
      ├─ Update leaderboard
      └─ Send SSE to all (broadcast)
```

## 2. One Contest at a Time
```
When admin starts contest X:
├─ Find any contest with status = ONGOING
├─ If found:
│  └─ Set its status = ENDED
└─ Set contest X status = ONGOING

Result: Only one contest active → GET /api/contests/current unambiguous
```

## 3. First-AC Tracking
```
When submission finalizes with ACCEPTED:
├─ Check: Does user already have ACCEPTED verdict for this problem in this contest?
├─ If YES:
│  └─ Don't update leaderboard (not first AC, no points)
└─ If NO:
   ├─ Add points to leaderboard
   ├─ Broadcast new leaderboard via SSE
   └─ All students see updated rankings
```

---

# Error Scenarios

| Scenario | Response | Why |
|----------|----------|-----|
| Submit without X-Roll-No header | 401 Unauthorized | Auth interceptor rejects |
| Non-admin tries POST /api/admin/users | 403 Forbidden | Admin interceptor checks role |
| Submit to ended contest | 400 "Contest is not ongoing" | SubmissionService validates |
| Submit invalid code (empty) | 400 "Code is required" | validateSubmitRequest checks |
| Judge0 fails to compile code | Verdict = COMPILATION_ERROR | Judge0StatusMapper handles |
| Judge0 times out | Verdict = TIME_LIMIT_EXCEEDED | Judge0Client response |
| SSE connection drops | Client auto-reconnects | Browser EventSource default |
| No ongoing contest | GET /api/contests/current → 404 | ContestService finds none |

---

# Testing the Flows

## Quick Manual Test

```bash
# 1. Create admin (seeded as ADMIN001)
# 2. Create problem
curl -X POST http://localhost:8080/api/admin/problems \
  -H "X-Roll-No: ADMIN001" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Hello World",
    "description": "Print hello world",
    "difficulty": "EASY",
    "constraints": "None"
  }'

# 3. Add test cases to problem (id from response)
curl -X POST http://localhost:8080/api/admin/problems/1/test-cases \
  -H "X-Roll-No: ADMIN001" \
  -H "Content-Type: application/json" \
  -d '{
    "stdin": "",
    "expectedOutput": "hello world\n",
    "isSample": true
  }'

# 4. Create contest
curl -X POST http://localhost:8080/api/admin/contests \
  -H "X-Roll-No: ADMIN001" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Contest",
    "startTime": "2026-06-10T14:00:00",
    "durationMins": 120,
    "problems": [{
      "problemId": 1,
      "points": 100,
      "displayOrder": 1
    }]
  }'

# 5. Start contest
curl -X POST http://localhost:8080/api/admin/contests/1/start \
  -H "X-Roll-No: ADMIN001"

# 6. Create student
curl -X POST http://localhost:8080/api/admin/users \
  -H "X-Roll-No: ADMIN001" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bob",
    "rollNo": "CS001",
    "branch": "CSE",
    "role": "STUDENT"
  }'

# 7. Get current contest (as student)
curl http://localhost:8080/api/contests/current \
  -H "X-Roll-No: CS001"

# 8. Submit solution
curl -X POST http://localhost:8080/api/submissions \
  -H "X-Roll-No: CS001" \
  -H "Content-Type: application/json" \
  -d '{
    "contestId": 1,
    "problemId": 1,
    "code": "print(\"hello world\")",
    "languageId": 71
  }'

# 9. Check submissions (after 2-3 seconds for judging)
curl http://localhost:8080/api/submissions?contestId=1 \
  -H "X-Roll-No: CS001"

# 10. Check leaderboard
curl http://localhost:8080/api/leaderboard?contestId=1 \
  -H "X-Roll-No: CS001"
```

---

# Summary

| API | Method | Purpose | Auth | Async? |
|-----|--------|---------|------|--------|
| /api/contests/current | GET | Get active contest | Student | No |
| /api/contests/{id}/problems | GET | Get problems | Student | No |
| /api/submissions | POST | Submit code | Student | Yes (judge async) |
| /api/submissions | GET | List submissions | Student | No |
| /api/leaderboard | GET | Get leaderboard | Student | No |
| /api/events | GET | SSE stream | Student | Yes (streaming) |
| /api/admin/users | POST/GET | Create/list users | Admin | No |
| /api/admin/problems | POST/GET | Create/list problems | Admin | No |
| /api/admin/problems/{id}/test-cases | POST/GET/DELETE | Test case CRUD | Admin | No |
| /api/admin/contests | POST/GET | Create/list contests | Admin | No |
| /api/admin/contests/{id}/start | POST | Start contest | Admin | No |
| /api/admin/contests/{id}/end | POST | End contest | Admin | No |

This document covers all endpoints needed for a single user to participate in a contest from start to finish.
