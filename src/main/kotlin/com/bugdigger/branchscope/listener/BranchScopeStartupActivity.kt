package com.bugdigger.branchscope.listener

import com.bugdigger.branchscope.service.BranchScopeService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.changes.ChangeListListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

internal class BranchScopeStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        log.warn("Branch Scope: startup activity running for ${project.name}")
        val service = BranchScopeService.getInstance(project)
        val connection = project.messageBus.connect(service)

        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
            service.requestRefresh()
        })

        connection.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() {
                service.requestRefresh()
            }
        })

        service.requestRefresh()
    }

    companion object {
        private val log = logger<BranchScopeStartupActivity>()
    }
}
