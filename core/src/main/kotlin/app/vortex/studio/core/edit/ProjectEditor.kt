package app.vortex.studio.core.edit

import app.vortex.studio.core.model.IdGenerator
import app.vortex.studio.core.model.Project
import app.vortex.studio.core.model.RandomIds

sealed class EditOutcome {
    object Applied : EditOutcome()
    object NoChange : EditOutcome()
    data class Failed(val error: EditError) : EditOutcome()
}

/**
 * Mantém o projeto atual e o histórico de desfazer/refazer. Como o modelo é imutável, cada entrada
 * do histórico é apenas uma referência ao estado anterior (barato e seguro).
 */
class ProjectEditor(
    initial: Project,
    val ids: IdGenerator = RandomIds,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val historyLimit: Int = 200,
    private val coalesceWindowMs: Long = 1_500L,
) {
    private class Entry(val label: String, val state: Project, val coalesceKey: String?, var atMs: Long)

    var project: Project = initial.normalized()
        private set

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label

    /**
     * Executa uma operação. Se nada mudar, o histórico não é alterado. Operações com a mesma
     * [coalesceKey] em sequência rápida (ex.: arrastar um controle deslizante) viram uma só entrada.
     */
    fun exec(label: String, coalesceKey: String? = null, op: (Project) -> Project): EditOutcome {
        val candidate = try { op(project).normalized() } catch (e: EditError) { return EditOutcome.Failed(e) }
        if (candidate == project) return EditOutcome.NoChange
        val now = clock()
        val last = undoStack.lastOrNull()
        val merge = coalesceKey != null && last != null && last.coalesceKey == coalesceKey && now - last.atMs <= coalesceWindowMs && redoStack.isEmpty()
        if (merge) {
            last.atMs = now
        } else {
            undoStack.addLast(Entry(label, project, coalesceKey, now))
            while (undoStack.size > historyLimit) undoStack.removeFirst()
        }
        redoStack.clear()
        project = candidate.copy(modifiedAtMs = now)
        return EditOutcome.Applied
    }

    fun undo(): Boolean {
        val e = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(Entry(e.label, project, null, clock()))
        val now = clock()
        project = e.state.copy(modifiedAtMs = now)
        return true
    }

    fun redo(): Boolean {
        val e = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(Entry(e.label, project, null, clock()))
        val now = clock()
        project = e.state.copy(modifiedAtMs = now)
        return true
    }

    /** Troca o projeto sem registrar histórico (ex.: restaurar do disco). */
    fun reset(p: Project) { project = p.normalized(); undoStack.clear(); redoStack.clear() }
}
