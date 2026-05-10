package com.bugdigger.branchscope.git

import com.bugdigger.branchscope.settings.BranchScopeSettings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/**
 * Resolves the "base branch" used as the diff origin for the current branch.
 *
 * Priority:
 *   1. Setting override (when not "auto") if it exists in the repo
 *   2. origin/HEAD symbolic ref → its target branch
 *   3. main, master, develop (in that order), local or origin
 */
class BaseBranchResolver(private val project: Project) {

    /** Returns a fully-qualified ref usable in `git merge-base` (e.g. "origin/main", "main"), or null. */
    fun resolve(repository: GitRepository): String? {
        val settings = BranchScopeSettings.getInstance(project)
        if (!settings.isAutoBaseBranch()) {
            val override = settings.baseBranch.trim()
            if (override.isNotEmpty() && refExists(repository, override)) {
                return override
            }
            log.info("Branch Scope: configured base branch '$override' not found, falling back to auto-detection")
        }

        originHeadTarget(repository)?.let { target ->
            val candidate = "origin/$target"
            if (refExists(repository, candidate)) return candidate
        }

        for (name in FALLBACK_NAMES) {
            val originRef = "origin/$name"
            if (refExists(repository, originRef)) return originRef
            if (refExists(repository, name)) return name
        }
        return null
    }

    private fun originHeadTarget(repository: GitRepository): String? {
        // `git rev-parse --abbrev-ref refs/remotes/origin/HEAD` prints e.g. "origin/main"
        val handler = GitLineHandler(project, repository.root, GitCommand.REV_PARSE).apply {
            addParameters("--abbrev-ref", "refs/remotes/origin/HEAD")
            setSilent(true)
        }
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) return null
        val raw = result.outputAsJoinedString.trim()
        return raw.removePrefix("origin/").ifBlank { null }
    }

    private fun refExists(repository: GitRepository, ref: String): Boolean {
        val handler = GitLineHandler(project, repository.root, GitCommand.REV_PARSE).apply {
            addParameters("--verify", "--quiet", ref)
            setSilent(true)
        }
        return Git.getInstance().runCommand(handler).success()
    }

    companion object {
        private val log = logger<BaseBranchResolver>()
        private val FALLBACK_NAMES = listOf("main", "master", "develop")
    }
}
