package su.nnedition.nodal

/**
 * Global configuration settings for Nodal.
 */
object NodalConfig {
    /**
     * Whether event nodes are allowed to have multiple parent nodes.
     *
     * Defaults to `false` (strict tree hierarchy).
     *
     * Can also be enabled via system property:
     * `-Dnodal.allow-multiple-parents=true`
     */
    @JvmStatic
    var allowMultipleParents: Boolean =
        System.getProperty("nodal.allow-multiple-parents", "false").toBoolean()

    /**
     * Handler for exceptions thrown inside event listeners.
     * Defaults to printing the stack trace to [System.err].
     */
    @JvmStatic
    var exceptionHandler: (Throwable) -> Unit = { error ->
        error.printStackTrace(System.err)
    }
}
