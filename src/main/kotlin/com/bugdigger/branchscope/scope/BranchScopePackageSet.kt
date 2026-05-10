package com.bugdigger.branchscope.scope

import com.bugdigger.branchscope.service.BranchScopeService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import java.util.concurrent.atomic.AtomicInteger

/**
 * Membership of the "Branch Modified Files" scope, driven by [BranchScopeService]'s snapshot.
 *
 * IntelliJ has THREE distinct entry points for scope membership and different parts of the IDE
 * call different overloads:
 *   - `contains(PsiFileSystemItem, NamedScopesHolder)` — the project-view scope dropdown
 *   - `contains(VirtualFile, NamedScopesHolder)`        — older VFS-only paths (deprecated)
 *   - `contains(VirtualFile, Project, NamedScopesHolder)` — Find-in-Path / NamedScopeFilter
 *
 * If any of these falls through to PackageSetBase's defaults, that path returns false and the
 * scope appears empty in that feature. So we override all three and route them to a single check.
 */
class BranchScopePackageSet : PackageSetBase() {

    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        val snapshot = BranchScopeService.getInstance(project).getSnapshot()
        val match = !snapshot.isEmpty && snapshot.byAbsolutePath.containsKey(file.path)
        logSampled(file, snapshot.size, match)
        return match
    }

    @Suppress("DEPRECATION")
    override fun contains(file: VirtualFile, holder: NamedScopesHolder): Boolean {
        val project = holder.project ?: return false
        return contains(file, project, holder)
    }

    override fun contains(file: PsiFileSystemItem, holder: NamedScopesHolder): Boolean {
        val vf = file.virtualFile ?: return false
        val project = holder.project ?: file.project
        return contains(vf, project, holder)
    }

    override fun createCopy(): BranchScopePackageSet = BranchScopePackageSet()

    override fun getText(): String = SCOPE_PATTERN

    override fun getNodePriority(): Int = 0

    private fun logSampled(file: VirtualFile, snapshotSize: Int, match: Boolean) {
        if (snapshotSize == 0 && !match) return
        val n = nonEmptyCallCount.incrementAndGet()
        if (n <= LOG_LIMIT) {
            log.warn("Branch Scope: contains(${file.path}) snapshotSize=$snapshotSize -> $match")
        } else if (n == LOG_LIMIT + 1) {
            log.warn("Branch Scope: contains() — further non-empty calls suppressed")
        }
    }

    companion object {
        const val SCOPE_PATTERN: String = "branch-modified-files"
        private val log = logger<BranchScopePackageSet>()
        private const val LOG_LIMIT = 60
        private val nonEmptyCallCount = AtomicInteger()
    }
}
