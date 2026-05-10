package com.bugdigger.branchscope.scope

import com.bugdigger.branchscope.git.BranchFileStatus
import com.bugdigger.branchscope.service.BranchScopeService
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.vcs.FileStatus

/**
 * Colors files that are modified in the current branch (committed or working-tree)
 * with the standard VCS file-status colors. Active in every project view pane, so the
 * user can spot branch-modified files at a glance — not only inside the
 * "Branch Modified Files" scope.
 *
 * Wired via the public `com.intellij.projectViewNodeDecorator` extension point.
 */
class BranchScopeNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.virtualFile ?: return
        if (file.isDirectory) return
        val project = node.project ?: return
        val entry = BranchScopeService.getInstance(project).getSnapshot().byAbsolutePath[file.path] ?: return
        val color = colorFor(entry.status).color ?: return
        data.forcedTextForeground = color
    }

    private fun colorFor(status: BranchFileStatus): FileStatus = when (status) {
        BranchFileStatus.ADDED -> FileStatus.ADDED
        BranchFileStatus.MODIFIED -> FileStatus.MODIFIED
        BranchFileStatus.DELETED -> FileStatus.DELETED
        BranchFileStatus.RENAMED -> FileStatus.MODIFIED
    }
}
