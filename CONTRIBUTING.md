# Contributing to FIX Flow Simulator

Thank you for your interest in contributing.

## License and Contributor Agreement

FIX Flow Simulator is published under the
**FIX Flow Simulator Source Available License v1.0** (see [LICENSE](LICENSE)).

By submitting any contribution — pull request, patch, issue fix, documentation change, or
other modification — **you agree to the following**:

1. Your contribution is your original work and you have the right to submit it.
2. You grant Giorgio Gandola (the Licensor) a **perpetual, irrevocable, worldwide,
   royalty-free, sublicensable license** to use, reproduce, modify, distribute, and
   sublicense your contribution under any license terms, including Commercial Licenses.
3. This grant enables the project to be dual-licensed in the future (open + commercial)
   without requiring consent from each contributor.

If your employer owns rights to your contributions, you represent that you have received
permission to contribute on their behalf.

## What you can contribute

- Bug fixes and test cases
- Translations and documentation improvements
- New node types or features — discuss in an issue first for non-trivial changes
- UI / UX improvements

## How to contribute

1. Fork the repository on GitHub.
2. Create a feature branch (`feat/<name>`) or bugfix branch (`fix/<name>`).
3. Follow the code conventions already present in the codebase.
4. Ensure `~/maven/bin/mvn test` passes before opening a PR.
5. Open a pull request against `master` with a clear description of what and why.

## Code style

- Backend: standard Java conventions; no Lombok.
- Frontend: TypeScript strict; React functional components; no class components.
- Commit messages: Conventional Commits format (`feat:`, `fix:`, `docs:`, etc.).

## Questions

Open a GitHub issue or email [giogandola@gmail.com](mailto:giogandola@gmail.com).
