package com.bugdigger.branchscope.service

import com.bugdigger.branchscope.git.BaseBranchResolver
import com.bugdigger.branchscope.git.BranchDiffComputer
import com.bugdigger.branchscope.git.BranchFileEntry
import com.bugdigger.branchscope.git.BranchFileStatus
import com.bugdigger.branchscope.settings.BranchScopeSettings
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.util.Alarm
import com.intellij.util.messages.Topic
import git4idea.repo.GitRepositoryManager

@Service(Service.Level.PROJECT)
class BranchScopeService(private val project: Project) : Disposable {

    @Volatile
    private var snapshot: Snapshot = Snapshot.EMPTY

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    fun getSnapshot(): Snapshot = snapshot

    /** Request a recompute. Coalesced with other requests within the debounce window. */
    fun requestRefresh() {
        if (project.isDisposed) return
        alarm.cancelAllRequests()
        alarm.addRequest({ recomputeNow() }, DEBOUNCE_MS)
    }

    private fun recomputeNow() {
        if (project.isDisposed) return
        val newSnapshot = try {
            buildSnapshot()
        } catch (t: Throwable) {
            log.warn("Branch Scope: refresh failed", t)
            Snapshot.EMPTY
        }
        snapshot = newSnapshot
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed) return@invokeLater
                project.messageBus.syncPublisher(TOPIC).onUpdate(newSnapshot)
                // Tell IntelliJ's scope-aware UIs (project view scope filter, etc.) to drop
                // their cached membership data and re-query our PackageSet. Without this the
                // scope view caches the result of contains() from the first (empty) eval.
                NamedScopeManager.getInstance(project).fireScopeListeners()
                DependencyValidationManager.getInstance(project).fireScopeListeners()
                ProjectView.getInstance(project).refresh()
            },
            { project.isDisposed }
        )
    }

    private fun buildSnapshot(): Snapshot {
        val repos = GitRepositoryManager.getInstance(project).repositories
        if (repos.isEmpty()) {
            log.warn("Branch Scope: no Git repositories in project; snapshot empty")
            return Snapshot.EMPTY
        }

        val settings = BranchScopeSettings.getInstance(project)
        val resolver = BaseBranchResolver(project)
        val computer = BranchDiffComputer(project)

        val allEntries = mutableListOf<BranchFileEntry>()
        var resolvedBase: String? = null
        for (repo in repos) {
            val base = resolver.resolve(repo)
            if (base == null) {
                log.warn("Branch Scope: could not resolve base branch for repo ${repo.root.path}")
                continue
            }
            resolvedBase = base
            val entries = computer.compute(repo, base, settings.includeUncommitted)
            log.warn(
                "Branch Scope: repo=${repo.root.path} base=$base " +
                "includeUncommitted=${settings.includeUncommitted} -> ${entries.size} diff entries"
            )
            allEntries += entries
        }

        // Untracked files don't appear in `git diff` output but conceptually belong to the
        // branch's changes once committed. Pull them from ChangeListManager (public API)
        // and add as ADDED entries when "include uncommitted" is on.
        if (settings.includeUncommitted) {
            val untracked = ChangeListManager.getInstance(project).unversionedFilesPaths
            if (untracked.isNotEmpty()) {
                val existingPaths = HashSet<String>(allEntries.size).also { paths ->
                    allEntries.forEach { paths.add(it.absolutePath) }
                }
                val repoRoots = repos.map { it.root.path }
                var added = 0
                for (filePath in untracked) {
                    val absPath = filePath.path
                    if (!existingPaths.add(absPath)) continue
                    val matchingRoot = repoRoots.firstOrNull { root ->
                        absPath == root || absPath.startsWith("$root/")
                    }
                    val rel = if (matchingRoot != null) {
                        absPath.removePrefix(matchingRoot).removePrefix("/")
                    } else {
                        filePath.name
                    }
                    allEntries += BranchFileEntry(
                        repoRelativePath = rel,
                        absolutePath = absPath,
                        status = BranchFileStatus.ADDED,
                    )
                    added++
                }
                if (added > 0) log.warn("Branch Scope: included $added untracked file(s)")
            }
        }

        val byAbsPath = allEntries.associateBy { it.absolutePath }
        log.warn("Branch Scope: snapshot built with ${allEntries.size} total entries")
        return Snapshot(allEntries, byAbsPath, resolvedBase)
    }

    override fun dispose() {
        // Alarm is disposed automatically because we registered it with `this` as parent disposable.
    }

    /** Immutable snapshot of the file set. */
    data class Snapshot(
        val entries: List<BranchFileEntry>,
        val byAbsolutePath: Map<String, BranchFileEntry>,
        val baseRef: String?,
    ) {
        val isEmpty: Boolean get() = entries.isEmpty()
        val size: Int get() = entries.size

        companion object {
            val EMPTY = Snapshot(emptyList(), emptyMap(), null)
        }
    }

    fun interface Listener {
        fun onUpdate(snapshot: Snapshot)
    }

    companion object {
        private val log = logger<BranchScopeService>()
        private const val DEBOUNCE_MS = 150

        @JvmField
        val TOPIC: Topic<Listener> = Topic.create("BranchScope.update", Listener::class.java)

        fun getInstance(project: Project): BranchScopeService = project.service()
    }
}
