/* This file is part of key-abbrev.
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package io.github.wadoon.key.abbrevmgr

import de.uka.ilkd.key.core.KeYMediator
import de.uka.ilkd.key.core.KeYSelectionEvent
import de.uka.ilkd.key.core.KeYSelectionListener
import de.uka.ilkd.key.gui.KeYFileChooser
import de.uka.ilkd.key.gui.MainWindow
import de.uka.ilkd.key.gui.actions.KeyAction
import de.uka.ilkd.key.gui.extension.api.KeYGuiExtension
import de.uka.ilkd.key.gui.extension.api.TabPanel
import de.uka.ilkd.key.gui.fonticons.FontAwesomeSolid
import de.uka.ilkd.key.gui.fonticons.IconFactory
import de.uka.ilkd.key.gui.fonticons.IconFontProvider
import de.uka.ilkd.key.logic.JTerm
import de.uka.ilkd.key.nparser.KeyIO
import de.uka.ilkd.key.pp.AbbrevException
import de.uka.ilkd.key.pp.AbbrevMap
import de.uka.ilkd.key.pp.LogicPrinter
import de.uka.ilkd.key.proof.Node
import de.uka.ilkd.key.proof.Proof
import de.uka.ilkd.key.proof.mgt.TaskTreeModel
import de.uka.ilkd.key.util.parsing.BuildingException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener


private val LOGGER: Logger = LoggerFactory.getLogger(AbbrevManager::class.java)

/**
 * This is a small extension that add the abbreviation widget to KeY.
 * The abbreviation manager allows you to manage (delete, rename, load/save,...)
 * abbreviations in KeY proofs.
 *
 * @author Alexander Weigl
 * @version 1 (14.07.23)
 */
@KeYGuiExtension.Info(
    name = "Abbreviation Manager", description = "A widget for the management of abbreviation in proofs. " +
            "Allows storing and loading abbreviations of text files.", disabled = false, experimental = false
)
class AbbrevManager : KeYGuiExtension, KeYGuiExtension.LeftPanel {
    private var panel: AbbrevManagerPanel? = null

    override fun getPanels(window: MainWindow, mediator: KeYMediator): Collection<TabPanel> {
        if (panel != null) {
            panel = AbbrevManagerPanel(window, mediator)
        }
        return mutableSetOf(panel!!)
    }

}

data class AbbrevListEntry(var term: JTerm, var abbrev: String)

class AbbrevManagerPanel(private val window: MainWindow, private val mediator: KeYMediator) : JPanel(),
    TabPanel {
    private val listAbbrev = JList<AbbrevListEntry>()
    private val modelAbbrev = DefaultListModel<AbbrevListEntry>()
    private val actionLoad: KeyAction = LoadAction()
    private val actionSave: KeyAction = SaveAction()
    private val actionTransfer: KeyAction = TransferAbbrevAction()

    private val updateListListener: PropertyChangeListener =
        PropertyChangeListener { it: PropertyChangeEvent? -> updateList() }

    /**
     * Stores a reference to the proof to which the [.updateListListener] is attached to.
     * Weak ref protects for memory leakage.
     */
    private var oldProof: WeakReference<Proof>? = null

    init {
        layout = BorderLayout()
        listAbbrev.model = modelAbbrev
        listAbbrev.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>, value: Any,
                index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component? {
                val v = value as AbbrevListEntry
                val s = "${v.abbrev} : ${LogicPrinter.quickPrintTerm(v.term, mediator.services)}"
                return super.getListCellRendererComponent(
                    list, s, index, isSelected,
                    cellHasFocus
                )
            }
        }
        add(listAbbrev)

        updateList()

        mediator.addKeYSelectionListener(object : KeYSelectionListener {
            override fun selectedNodeChanged(e: KeYSelectionEvent<Node?>) {
                // if oldProof exists, remove updateListListener from it.
                if (oldProof != null) {
                    val oldp: Proof? = oldProof?.get()
                    oldp?.abbreviations()?.removePropertyChangeListener(updateListListener)
                    oldProof = null
                }
                // if currently there is a selected proof, attach updateListListener to it
                val selectedProof: Proof? = mediator.selectedProof
                if (selectedProof != null) {
                    selectedProof.abbreviations().addPropertyChangeListener(updateListListener)
                    oldProof = WeakReference(selectedProof)
                }
                updateList()
            }
        })

        mediator.notationInfo.abbrevMap.addPropertyChangeListener(updateListListener)

        val popup = JPopupMenu()
        popup.add(RemoveAbbrev())
        popup.add(ChangeAbbrev())
        popup.add(ChangeTerm())
        popup.add(ToggleActivity())
        listAbbrev.componentPopupMenu = popup
    }

    fun updateList() {
        modelAbbrev.clear()
        val selectedProof: Proof? = mediator.selectedProof
        selectedProof?.abbreviations()?.asSeq()?.toList()
            ?.sortedBy { it.abbrev }
            ?.forEach { modelAbbrev.addElement(it) }
    }

    override fun getTitle(): String = "Abbrev Manager"
    override fun getComponent(): JComponent = this
    override fun getTitleActions(): Collection<Action?> = listOf(actionLoad, actionSave, actionTransfer)

    private inner class SaveAction : KeyAction() {
        init {
            setName("Save abbreviations")
            setIcon(IconFactory.saveFile(MainWindow.TOOLBAR_ICON_SIZE))
            tooltip = "Save abbreviation to file."
        }

        override fun actionPerformed(e: ActionEvent?) {
            val fc: KeYFileChooser =
                KeYFileChooser.getFileChooser("Select file to store abbreviation map.")
            val result: Int = fc.showOpenDialog(window)
            if (result == JFileChooser.APPROVE_OPTION) {
                val abbrevMap = mediator.notationInfo.abbrevMap.export()
                val file: Path = fc.selectedFile.toPath()
                try {
                    Files.writeString(
                        file,
                        abbrevMap.stream()
                            .map { it ->
                                it.second + SEPERATOR_ABBREV_FILE +
                                        LogicPrinter.quickPrintTerm(it.first, mediator.services)
                            }
                            .collect(Collectors.joining("\n")))
                } catch (ex: IOException) {
                    LOGGER.error("File I/O error", ex)
                    JOptionPane.showMessageDialog(window, "I/O Error:" + ex.message)
                }
            }
        }
    }

    private inner class LoadAction : KeyAction() {
        init {
            setName("Load abbreviations")
            setIcon(IconFactory.openKeYFile(MainWindow.TOOLBAR_ICON_SIZE))
            tooltip = "Load abbreviation from a given file."
        }

        override fun actionPerformed(e: ActionEvent?) {
            val fc: KeYFileChooser =
                KeYFileChooser.getFileChooser("Select file to load proof or problem")
            val mainWindow: MainWindow? = window
            val result: Int = fc.showOpenDialog(mainWindow)
            if (result == JFileChooser.APPROVE_OPTION) {
                val abbrevMap: AbbrevMap = mediator.notationInfo.abbrevMap
                val kio = KeyIO(mediator.services)
                kio.abbrevMap = abbrevMap
                val file: File = fc.selectedFile

                try {
                    for (line in Files.readAllLines(file.toPath())) {
                        if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) {
                            val split = line.split(SEPERATOR_ABBREV_FILE.toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                            if (split.size == 2) {
                                val abbrevName = split[0]
                                val term: JTerm = kio.parseExpression(split[1])
                                try {
                                    abbrevMap.put(term, abbrevName.trim { it <= ' ' }, true)
                                } catch (ex: AbbrevException) {
                                    LOGGER.error(
                                        "Could not add {} with {} to abbrevMap",
                                        abbrevName, term, ex
                                    )
                                }
                            }
                        }
                    }
                } catch (ex: IOException) {
                    LOGGER.error("File I/O error", ex)
                    JOptionPane.showMessageDialog(mainWindow, "I/O Error:" + ex.message)
                }
            }
        }
    }


    private inner class RemoveAbbrev : KeyAction() {
        init {
            setName("Remove abbreviation")
            listAbbrev.addListSelectionListener { e: ListSelectionEvent? ->
                val selectedValue = listAbbrev.getSelectedValue()
                setEnabled(selectedValue != null)
            }
        }

        override fun actionPerformed(e: ActionEvent?) {
            val term: JTerm = listAbbrev.getSelectedValue().term
            mediator.notationInfo.abbrevMap.remove(term)
            window.makePrettyView()
        }
    }

    private inner class ToggleActivity : KeyAction() {
        init {
            setName("Toggle abbreviation")

            listAbbrev.addListSelectionListener(ListSelectionListener { e: ListSelectionEvent? ->
                val selectedValue = listAbbrev.getSelectedValue()
                if (selectedValue == null) {
                    setEnabled(false)
                    setName("Toggle abbreviation")
                    return@ListSelectionListener
                }
                setEnabled(true)
                val term = selectedValue.term
                if (mediator.notationInfo.abbrevMap.isEnabled(term)) {
                    setName("Disable abbreviation")
                } else {
                    setName("Enable abbreviation")
                }
            })
        }

        override fun actionPerformed(e: ActionEvent?) {
            val term: JTerm? = listAbbrev.getSelectedValue().term
            val value: Boolean = mediator.notationInfo.abbrevMap.isEnabled(term)
            mediator.notationInfo.abbrevMap.setEnabled(term, !value)
            window.makePrettyView()
        }
    }

    private inner class ChangeAbbrev : KeyAction() {
        init {
            setName("Change abbreviation")
            listAbbrev.addListSelectionListener { e: ListSelectionEvent? ->
                val selectedValue = listAbbrev.getSelectedValue()
                setEnabled(selectedValue != null)
            }
        }

        override fun actionPerformed(e: ActionEvent?) {
            val selected = listAbbrev.getSelectedValue()

            if (selected == null) {
                return
            }

            val answer: String? =
                JOptionPane.showInputDialog(
                    window, "Set new label for term: ${selected.term}",
                    selected.abbrev
                )
            if (answer == null) return
            try {
                mediator.notationInfo.abbrevMap.changeAbbrev(selected.term, answer)
                window.makePrettyView()
            } catch (ex: AbbrevException) {
                throw RuntimeException(ex)
            }
        }
    }

    private inner class ChangeTerm : KeyAction() {
        init {
            setName("Change term")
            listAbbrev.addListSelectionListener { e ->
                val selectedValue = listAbbrev.getSelectedValue()
                setEnabled(selectedValue != null)
            }
        }

        override fun actionPerformed(e: ActionEvent?) {
            val selected = listAbbrev.getSelectedValue()
            if (selected == null) return
            var prettyPrinted: String = LogicPrinter.quickPrintTerm(selected.term, mediator.services)

            while (true) { // abort if the user abort the input dialog, or the expression was
                // successfully changed.
                val answer: String? =
                    JOptionPane.showInputDialog(
                        window,
                        "Set a new term for abbreviation ${selected.abbrev}:",
                        prettyPrinted
                    )
                if (answer == null) return
                val kio = KeyIO(mediator.services)
                kio.abbrevMap = mediator.notationInfo.abbrevMap
                try {
                    val term: JTerm = kio.parseExpression(answer)
                    mediator.notationInfo.abbrevMap.changeAbbrev(
                        selected.abbrev,
                        term, true
                    )
                    window.makePrettyView()
                    return
                } catch (ex: BuildingException) {
                    LOGGER.error("Error during parsing of user entered term {}", answer, ex)
                    JOptionPane.showMessageDialog(
                        window, ex.message, "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                    prettyPrinted = answer
                } catch (ex: AbbrevException) {
                    throw RuntimeException(ex)
                }
            }
        }
    }

    private inner class TransferAbbrevAction : KeyAction() {
        private val TRANSFER_ICON: IconFontProvider = IconFontProvider(
            FontAwesomeSolid.ANGLE_DOUBLE_DOWN,
            Color.black
        )

        init {
            setName("Transfer abbreviation from...")
            setIcon(TRANSFER_ICON.get())
            tooltip = "Transfers all abbreviation from the selected proof to this proof. Best effort. No guarantees!"
        }

        override fun actionPerformed(e: ActionEvent?) {
            val selected: Proof? = mediator.selectedProof
            if (selected == null) return


            val proofs = window.proofList.model.getLoadedProofs()
                .filter { it !== selected }
                .toTypedArray()

            val from: Proof? = JOptionPane.showInputDialog(
                window,
                "Select a proof to import from. ", "Import Abbreviations",
                JOptionPane.PLAIN_MESSAGE, null, proofs, null
            ) as Proof?
            if (from == null) return
            val kio = KeyIO(mediator.services)
            kio.abbrevMap = selected.abbreviations()

            for (pair in from.abbreviations().export()) {
                // print and parse to ensure the different namespace
                val term: JTerm =
                    kio.parseExpression(
                        LogicPrinter.quickPrintTerm(pair.first, from.services)
                    )
                selected.abbreviations().forcePut(pair.second, term)
            }
            window.makePrettyView()
        }
    }

    companion object {
        /**
         * seperator between abbreviation label and term inside of stored files
         */
        const val SEPERATOR_ABBREV_FILE: String = "::=="
    }
}

private fun AbbrevMap.addPropertyChangeListener(updateListListener: PropertyChangeListener) {}

private fun AbbrevMap?.removePropertyChangeListener(updateListListener: PropertyChangeListener) {
    TODO("Not yet implemented")
}

private fun AbbrevMap.forcePut(second: String, term: JTerm) {}
private fun TaskTreeModel.getLoadedProofs(): List<Proof> = listOf()
private fun AbbrevMap.remove(term: JTerm): Boolean = TODO()

private fun AbbrevMap.asSeq() =
    export().asSequence().map { AbbrevListEntry(it.first, it.second) }
