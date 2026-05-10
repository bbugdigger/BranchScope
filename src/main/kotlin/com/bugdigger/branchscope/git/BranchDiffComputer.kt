package com.bugdigger.branchscope.git

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/**
 * Computes the set of files changed in the current branch relative to a base ref.
 *
 * When [includeUncommitted] is true we ask git for the diff between mergeBase and the working tree,
 * which captures committed branch commits + staged + unstaged changes in a single command.
 * Untracked files are not included in v1.
 */
class BranchDiffComputer(private val project: Project) {

    fun compute(
        repository: GitRepository,
        baseRef: String,
        includeUncommitted: Boolean,
    ): List<BranchFileEntry> {
        val mergeBase = mergeBase(repository, baseRef) ?: return emptyList()

        val diffHandler = GitLineHandler(project, repository.root, GitCommand.DIFF).apply {
            addParameters("--name-status", "-M")
            if (includeUncommitted) {
                // <mergeBase> with no second ref → diff working tree vs mergeBase
                addParameters(mergeBase)
            } else {
                addParameters("$mergeBase..HEAD")
            }
            setSilent(true)
        }
        val result = Git.getInstance().runCommand(diffHandler)
        if (!result.success()) {
            log.warn("Branch Scope: git diff failed: ${result.errorOutputAsJoinedString}")
            return emptyList()
        }

        val rootPath = repository.root.path
        return result.output.mapNotNull { parseLine(it, rootPath) }
    }

    private fun mergeBase(repository: GitRepository, baseRef: String): String? {
        val handler = GitLineHandler(project, repository.root, GitCommand.MERGE_BASE).apply {
            addParameters("HEAD", baseRef)
            setSilent(true)
        }
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            log.info("Branch Scope: merge-base failed for $baseRef: ${result.errorOutputAsJoinedString}")
            return null
        }
        return result.outputAsJoinedString.trim().ifEmpty { null }
    }

    /**
     * Parses one `--name-status` line:
     *   "M\tpath/to/file"
     *   "A\tnew.kt"
     *   "D\told.kt"
     *   "R100\told/path\tnew/path"
     *   "C75\tsrc\tdst"   (treated as ADDED at dst)
     *   "T\tpath"          (type change → MODIFIED)
     */
    private fun parseLine(line: String, repoRoot: String): BranchFileEntry? {
        if (line.isBlank()) return null
        val parts = line.split('\t')
        if (parts.size < 2) return null
        val rawStatus = parts[0]
        val code = rawStatus.firstOrNull() ?: return null
        return when (code) {
            'M', 'T' -> simple(parts[1], BranchFileStatus.MODIFIED, repoRoot)
            'A' -> simple(parts[1], BranchFileStatus.ADDED, repoRoot)
            'D' -> simple(parts[1], BranchFileStatus.DELETED, repoRoot)
            'R' -> {
                if (parts.size < 3) return null
                BranchFileEntry(
                    repoRelativePath = parts[2],
                    absolutePath = "$repoRoot/${parts[2]}",
                    status = BranchFileStatus.RENAMED,
                    originalRepoRelativePath = parts[1],
                )
            }
            'C' -> {
                // copy: there's still an old file; treat the new one as ADDED
                if (parts.size < 3) return null
                simple(parts[2], BranchFileStatus.ADDED, repoRoot)
            }
            else -> null
        }
    }

    private fun simple(path: String, status: BranchFileStatus, repoRoot: String) = BranchFileEntry(
        repoRelativePath = path,
        absolutePath = "$repoRoot/$path",
        status = status,
    )

    companion object {
        private val log = logger<BranchDiffComputer>()
    }
}
