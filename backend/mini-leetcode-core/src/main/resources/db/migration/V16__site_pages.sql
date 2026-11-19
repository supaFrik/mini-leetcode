-- ============================================================
-- V16: Static site pages (policies) edited by admins in Markdown.
-- ============================================================

CREATE TABLE site_pages (
    slug VARCHAR(50) NOT NULL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content_md TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO site_pages (slug, title, content_md) VALUES
('terms', 'Terms of use',
'## Your account

- Keep your password private. You are responsible for activity on your account.
- One person, one account.

## Acceptable use

- Do not attack the judge, upload malware, or try to read other users'' code.
- Do not share solutions to running contests.

## Your code

- You keep the rights to the code you submit. We store it to grade it and to show it back to you.
- Staff may review submissions to detect cheating.

## Changes

We may update these terms. The date at the top of this page shows the last change.'),
('rules', 'Contest rules',
'## During a contest

- Work alone. No help from other people, no AI assistants, no sharing code.
- Stay on the contest page. Leaving full screen or switching tabs is logged as a violation.
- Too many violations disqualify you from the contest.

## Scoring (ACM/ICPC)

- Rank is decided by the number of problems solved.
- Ties are broken by penalty time: minutes until each accepted submission, plus 20 minutes per wrong attempt before it.

## After a contest

- Results are final once the contest ends. Staff may disqualify participants after a plagiarism review.');
