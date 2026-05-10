package com.bugdigger.branchscope.settings

import com.bugdigger.branchscope.BranchScopeBundle
import com.bugdigger.branchscope.service.BranchScopeService
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class BranchScopeConfigurable(private val project: Project) : BoundConfigurable(
    BranchScopeBundle.message("settings.branchScope.title")
) {

    override fun createPanel(): DialogPanel {
        val settings = BranchScopeSettings.getInstance(project)

        return panel {
            row(BranchScopeBundle.message("settings.branchScope.baseBranch")) {
                textField()
                    .bindText(
                        { settings.baseBranch },
                        { settings.baseBranch = it }
                    )
                    .comment(BranchScopeBundle.message("settings.branchScope.baseBranchHint"))
            }
            row {
                checkBox(BranchScopeBundle.message("settings.branchScope.includeUncommitted"))
                    .bindSelected(
                        { settings.includeUncommitted },
                        { settings.includeUncommitted = it }
                    )
            }
            row {
                checkBox(BranchScopeBundle.message("settings.branchScope.showDeletedFiles"))
                    .bindSelected(
                        { settings.showDeletedFiles },
                        { settings.showDeletedFiles = it }
                    )
            }
        }
    }

    override fun apply() {
        super.apply()
        BranchScopeService.getInstance(project).requestRefresh()
    }
}
