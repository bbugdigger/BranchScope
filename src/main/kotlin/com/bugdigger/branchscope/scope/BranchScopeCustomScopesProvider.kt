package com.bugdigger.branchscope.scope

import com.bugdigger.branchscope.BranchScopeBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx
import com.intellij.psi.search.scope.packageSet.NamedScope

/**
 * Registers the "Branch Modified Files" scope so it appears in the project view selector
 * (and other scope-aware UIs: Find in Path, File Color settings, etc.).
 *
 * Wired via the public `com.intellij.customScopesProvider` extension point.
 */
class BranchScopeCustomScopesProvider(@Suppress("unused") private val project: Project) : CustomScopesProviderEx() {

    private val scope: NamedScope = NamedScope(
        SCOPE_ID,
        { BranchScopeBundle.message("projectView.branchScope.title") },
        AllIcons.Vcs.Branch,
        BranchScopePackageSet(),
    )

    override fun getCustomScopes(): List<NamedScope> = listOf(scope)

    /** Hide from the "search everywhere" scope chooser if needed; default false (show it). */
    override fun isVetoed(scope: NamedScope, place: CustomScopesProviderEx.ScopePlace): Boolean = false

    companion object {
        /** Stable, non-localized id used to look up the scope. */
        const val SCOPE_ID: String = "Branch Modified Files"
    }
}
