package com.bugdigger.branchscope.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "BranchScopeSettings", storages = [Storage("branchScope.xml")])
class BranchScopeSettings : PersistentStateComponent<BranchScopeSettings.State> {

    data class State(
        var baseBranch: String = AUTO,
        var includeUncommitted: Boolean = true,
        var showDeletedFiles: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    var baseBranch: String
        get() = myState.baseBranch
        set(value) { myState.baseBranch = value.ifBlank { AUTO } }

    var includeUncommitted: Boolean
        get() = myState.includeUncommitted
        set(value) { myState.includeUncommitted = value }

    var showDeletedFiles: Boolean
        get() = myState.showDeletedFiles
        set(value) { myState.showDeletedFiles = value }

    fun isAutoBaseBranch(): Boolean = myState.baseBranch.equals(AUTO, ignoreCase = true)

    companion object {
        const val AUTO = "auto"
        fun getInstance(project: Project): BranchScopeSettings = project.service()
    }
}
