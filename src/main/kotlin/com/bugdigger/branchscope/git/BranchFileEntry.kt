package com.bugdigger.branchscope.git

enum class BranchFileStatus { ADDED, MODIFIED, DELETED, RENAMED }

/**
 * One file in the "branch modified" set.
 *
 * @param repoRelativePath forward-slash path from the repo root
 * @param absolutePath     absolute filesystem path (for VirtualFile lookup); for DELETED entries
 *                         the file no longer exists on disk
 * @param status           net status of the file vs. the base branch
 * @param originalRepoRelativePath original path before rename (only set for RENAMED)
 */
data class BranchFileEntry(
    val repoRelativePath: String,
    val absolutePath: String,
    val status: BranchFileStatus,
    val originalRepoRelativePath: String? = null,
)
