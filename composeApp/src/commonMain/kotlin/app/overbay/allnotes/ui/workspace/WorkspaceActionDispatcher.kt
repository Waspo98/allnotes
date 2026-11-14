package app.overbay.allnotes.ui.workspace

internal object WorkspaceActionDispatcher {
    fun dispatch(vm: WorkspaceViewModel, action: WorkspaceAction) {
        when (action) {
            is WorkspaceAction.SelectNotebook -> vm.selectNotebook(action.notebookId)
            is WorkspaceAction.SelectSection -> vm.selectSection(action.sectionId)
            is WorkspaceAction.SelectPage -> vm.selectPage(action.pageId)
            is WorkspaceAction.SetSearchQuery -> vm.setSearchQuery(action.query)
            WorkspaceAction.RequestSearchFocus -> vm.requestSearchFocus()
            is WorkspaceAction.ConsumeSearchFocusRequest -> vm.updateUiState {
                if (it.searchFocusRequestToken == action.requestToken) it.copy(searchFocusRequestToken = 0L) else it
            }
            WorkspaceAction.ToggleSidebar -> vm.toggleSidebar()
            is WorkspaceAction.SetMobileHierarchySheetOpen -> vm.setMobileHierarchySheetOpen(action.open)
            is WorkspaceAction.SetPageMenuOpen -> vm.updateUiState { it.copy(isPageMenuOpen = action.open) }
            is WorkspaceAction.SetMobileInsertMenuOpen -> vm.updateUiState { it.copy(isMobileInsertMenuOpen = action.open) }
            is WorkspaceAction.OpenDialog -> vm.openDialog(action.dialog)
            WorkspaceAction.DismissDialog -> vm.dismissDialog()
            is WorkspaceAction.OpenAttachmentPreview -> vm.updateUiState {
                it.copy(previewAttachment = action.attachment, isPageMenuOpen = false, isMobileInsertMenuOpen = false)
            }
            WorkspaceAction.DismissAttachmentPreview -> vm.updateUiState { it.copy(previewAttachment = null) }
            is WorkspaceAction.ShowStatusBanner -> vm.updateUiState { it.copy(statusBannerMessage = action.message) }
            WorkspaceAction.DismissStatusBanner -> vm.updateUiState { it.copy(statusBannerMessage = null) }
            WorkspaceAction.TriggerSync -> vm.triggerManualSync()
            is WorkspaceAction.ConfigureSyncServer -> vm.configureSyncServer(action.endpointUrl, action.useRemoteHttp)
            is WorkspaceAction.SaveSyncAndAuthSettings -> vm.saveSyncAndAuthSettings(
                action.endpointUrl, action.useRemoteHttp, action.authToken, action.autoSync
            )
            is WorkspaceAction.UpdateThemeSettings -> vm.updateThemeSettings(action.themeMode, action.useDynamicColor)

            is WorkspaceAction.CreateNotebook -> vm.createNotebook(action.title, action.subtitle, action.accent)
            is WorkspaceAction.UpdateActiveNotebook -> vm.updateActiveNotebook(action.title, action.subtitle, action.accent)
            is WorkspaceAction.ReorderActiveNotebook -> vm.reorderActiveNotebook(action.delta)
            WorkspaceAction.DeleteActiveNotebook -> vm.deleteActiveNotebook()
            is WorkspaceAction.CreateSection -> vm.createSection(action.title, action.accent)
            is WorkspaceAction.UpdateActiveSection -> vm.updateActiveSection(action.title, action.accent)
            is WorkspaceAction.ReorderActiveSection -> vm.reorderActiveSection(action.delta)
            WorkspaceAction.DeleteActiveSection -> vm.deleteActiveSection()
            is WorkspaceAction.CreatePage -> vm.createPage(action.title, action.asSubpageOfActive)
            is WorkspaceAction.TogglePageSubpageIndent -> vm.togglePageSubpageIndent(action.pageId)
            is WorkspaceAction.ToggleSubpageCollapse -> vm.toggleSubpageCollapse(action.parentPageId)
            is WorkspaceAction.MoveActivePageToSection -> vm.moveActivePageToSection(action.targetSectionId)
            is WorkspaceAction.ImportMarkdownPage -> vm.importMarkdownPage(action.markdown)

            is WorkspaceAction.UpdateActivePageTitle -> vm.updateActivePageTitle(action.newTitle)
            WorkspaceAction.ToggleActivePagePin -> vm.toggleActivePagePin()
            is WorkspaceAction.TogglePinPage -> vm.togglePinPage(action.pageId)
            is WorkspaceAction.RequestDeletePage -> vm.requestDeletePage(action.pageId)
            is WorkspaceAction.DeletePage -> vm.deletePage(action.pageId)
            WorkspaceAction.DeleteActivePage -> vm.deleteActivePage()
            WorkspaceAction.Undo -> vm.performUndo()
            WorkspaceAction.Redo -> vm.performRedo()

            is WorkspaceAction.SetActiveBlock -> vm.setActiveBlockId(action.blockId)
            is WorkspaceAction.UpdateActiveSelection -> vm.updateUiState {
                it.copy(
                    activeBlockId = action.blockId,
                    selectionStart = action.selectionStart,
                    selectionEnd = action.selectionEnd
                )
            }
            is WorkspaceAction.ConsumeFocusRequest -> vm.updateUiState {
                if (it.focusRequest?.requestToken == action.requestToken) it.copy(focusRequest = null) else it
            }
            WorkspaceAction.FocusOrAppendTrailingParagraph -> vm.focusOrAppendTrailingParagraph()
            is WorkspaceAction.MoveBlock -> vm.moveBlock(action.blockId, action.delta)
            is WorkspaceAction.DeleteBlock -> vm.deleteBlock(action.blockId)

            is WorkspaceAction.EditParagraphText -> vm.applyEditorResult(true) {
                EditorInputController.editParagraphWithTriggers(
                    it, action.blockId, action.newText, action.cursorOffset, vm::nextBlockId, vm::nextFocusToken
                )
            }
            is WorkspaceAction.SplitBlockAtCursor -> vm.applyEditorResult(false) {
                EditorInputController.splitBlockAtCursor(
                    it, action.blockId, action.cursorOffset, vm::nextBlockId, vm::nextFocusToken
                )
            }
            is WorkspaceAction.BackspaceAtBlockStart -> vm.applyEditorResult(false) {
                EditorInputController.backspaceAtBlockStart(it, action.blockId, vm::nextFocusToken)
            }
            is WorkspaceAction.ListEnterAtItem -> vm.applyEditorResult(false) {
                EditorInputController.handleListEnter(
                    it, action.blockId, action.itemIndex, vm::nextBlockId, vm::nextFocusToken
                )
            }
            is WorkspaceAction.ListBackspaceOnEmptyItem -> vm.applyEditorResult(false) {
                EditorInputController.handleListBackspaceOnEmpty(it, action.blockId, action.itemIndex, vm::nextFocusToken)
            }
            is WorkspaceAction.ExecuteSlashCommand -> vm.applyEditorResult(false) {
                EditorInputController.executeSlashCommand(
                    it, action.blockId, action.command, vm::nextBlockId, vm::nextFocusToken
                )
            }

            is WorkspaceAction.InsertHeading -> vm.insertHeadingBlock(action.level, action.afterBlockId)
            is WorkspaceAction.InsertParagraph -> vm.insertParagraphBlock(action.afterBlockId)
            is WorkspaceAction.InsertChecklist -> vm.insertChecklistBlock(action.afterBlockId)
            is WorkspaceAction.InsertBulletList -> vm.insertBulletListBlock(action.isNumbered, action.afterBlockId)
            is WorkspaceAction.InsertTable -> vm.insertTableBlock(action.afterBlockId)
            is WorkspaceAction.InsertCallout -> vm.insertCalloutBlock(action.afterBlockId)
            is WorkspaceAction.InsertCode -> vm.insertCodeBlock(action.afterBlockId)
            is WorkspaceAction.InsertDivider -> vm.insertDividerBlock(action.afterBlockId)
            is WorkspaceAction.InsertAttachment -> vm.insertAttachmentBlock(
                action.fileName, action.mimeType, action.sizeBytes, action.dataBase64, action.afterBlockId
            )

            is WorkspaceAction.UpdateBlockText -> vm.updateBlockText(action.blockId, action.newText)
            is WorkspaceAction.UpdateAttachmentCaption -> vm.updateAttachmentCaption(action.blockId, action.caption)
            is WorkspaceAction.ToggleSelectionSpan -> vm.toggleSelectionSpan(
                action.kind, action.url, action.blockId, action.selStart, action.selEnd
            )
            is WorkspaceAction.ToggleParagraphStyle -> vm.toggleParagraphStyle(action.blockId, action.style)
            is WorkspaceAction.ToggleActiveParagraphStyle -> vm.toggleActiveParagraphStyle(action.style)
            WorkspaceAction.ClearActiveParagraphFormatting -> vm.clearActiveParagraphFormatting()
            is WorkspaceAction.ConvertActiveBlock -> vm.convertActiveBlock(action.command)
            is WorkspaceAction.UpdateHeadingLevel -> vm.updateHeadingLevel(action.blockId, action.level)

            is WorkspaceAction.UpdateChecklistTitle -> vm.updateChecklistTitle(action.blockId, action.title)
            is WorkspaceAction.ToggleChecklistItem -> vm.toggleChecklistItem(action.blockId, action.itemId)
            is WorkspaceAction.AddChecklistItem -> vm.addChecklistItem(action.blockId, action.text)
            is WorkspaceAction.InsertChecklistItemAfter -> vm.insertChecklistItemAfter(action.blockId, action.afterItemId)
            is WorkspaceAction.UpdateChecklistItemText -> vm.updateChecklistItemText(action.blockId, action.itemId, action.text)
            is WorkspaceAction.DeleteChecklistItem -> vm.deleteChecklistItem(action.blockId, action.itemId)

            is WorkspaceAction.ToggleBulletListKind -> vm.toggleBulletListKind(action.blockId)
            is WorkspaceAction.AddBulletListItem -> vm.addBulletListItem(action.blockId, action.text)
            is WorkspaceAction.InsertBulletListItemAfter -> vm.insertBulletListItemAfter(action.blockId, action.afterItemIndex)
            is WorkspaceAction.UpdateBulletListItem -> vm.updateBulletListItem(action.blockId, action.itemIndex, action.text)
            is WorkspaceAction.DeleteBulletListItem -> vm.deleteBulletListItem(action.blockId, action.itemIndex)

            is WorkspaceAction.UpdateTableHeader -> vm.updateTableHeader(action.blockId, action.colIndex, action.title)
            is WorkspaceAction.UpdateTableCell -> vm.updateTableCell(action.blockId, action.rowIndex, action.colIndex, action.text)
            is WorkspaceAction.AddTableRow -> vm.addTableRow(action.blockId)
            is WorkspaceAction.DeleteTableRow -> vm.deleteTableRow(action.blockId, action.rowIndex)
            is WorkspaceAction.AddTableColumn -> vm.addTableColumn(action.blockId, action.headerTitle)
            is WorkspaceAction.DeleteTableColumn -> vm.deleteTableColumn(action.blockId, action.colIndex)

            is WorkspaceAction.UpdateCalloutTitle -> vm.updateCalloutTitle(action.blockId, action.title)
            is WorkspaceAction.UpdateCalloutTone -> vm.updateCalloutTone(action.blockId, action.tone)
            is WorkspaceAction.UpdateCodeLanguage -> vm.updateCodeLanguage(action.blockId, action.language)
        }
    }
}
