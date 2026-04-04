/* ============================================================
   Color Note Sync — Client-Side Application
   ============================================================ */

const COLORS = [
    { name: 'Yellow',  hex: '#FFEB3B' },
    { name: 'Green',   hex: '#4CAF50' },
    { name: 'Blue',    hex: '#2196F3' },
    { name: 'Pink',    hex: '#E91E63' },
    { name: 'Orange',  hex: '#FF9800' },
    { name: 'White',   hex: '#FFFFFF' },
    { name: 'Purple',  hex: '#9C27B0' },
    { name: 'Red',     hex: '#F44336' },
];

/* ---------- Utilities ---------- */

function api(path, opts = {}) {
    const defaults = { headers: { 'Content-Type': 'application/json' } };
    return fetch(path, { ...defaults, ...opts }).then(r => {
        if (!r.ok) throw new Error(`API ${r.status}`);
        return r.json();
    });
}

function formatDate(iso) {
    const d = new Date(iso);
    const now = new Date();
    const diff = now - d;
    if (diff < 60000) return 'just now';
    if (diff < 3600000) return Math.floor(diff / 60000) + 'm ago';
    if (diff < 86400000) return Math.floor(diff / 3600000) + 'h ago';
    return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function textColor(bgHex) {
    // Return dark text for light backgrounds
    const r = parseInt(bgHex.slice(1,3),16);
    const g = parseInt(bgHex.slice(3,5),16);
    const b = parseInt(bgHex.slice(5,7),16);
    const lum = (0.299*r + 0.587*g + 0.114*b) / 255;
    return lum > 0.6 ? '#333' : '#fff';
}

function buildColorPicker(container, selectedHex, onChange) {
    container.innerHTML = '';
    COLORS.forEach(c => {
        const sw = document.createElement('div');
        sw.className = 'color-swatch' + (c.hex === selectedHex ? ' selected' : '');
        sw.style.background = c.hex;
        if (c.hex === '#FFFFFF') sw.style.border = '2px solid #ccc';
        sw.title = c.name;
        sw.addEventListener('click', () => {
            container.querySelectorAll('.color-swatch').forEach(s => s.classList.remove('selected'));
            sw.classList.add('selected');
            onChange(c.hex);
        });
        container.appendChild(sw);
    });
}

/* ============================================================
   Notes Page
   ============================================================ */

function initNotesPage() {
    const grid = document.getElementById('notes-grid');
    const empty = document.getElementById('empty-state');
    const fab = document.getElementById('fab-new-note');
    const modal = document.getElementById('new-note-modal');
    const titleInput = document.getElementById('new-note-title');
    const colorPicker = document.getElementById('new-note-color-picker');
    const cancelBtn = document.getElementById('new-note-cancel');
    const okBtn = document.getElementById('new-note-ok');

    if (!grid) return; // not on notes page

    let selectedColor = '#FFEB3B';

    function renderNotes(notes) {
        grid.innerHTML = '';
        if (notes.length === 0) {
            empty.style.display = '';
            return;
        }
        empty.style.display = 'none';

        notes.forEach(note => {
            const card = document.createElement('div');
            card.className = 'note-card';
            card.style.background = note.color;
            card.style.color = textColor(note.color);

            const header = document.createElement('div');
            header.className = 'note-card-header';
            header.textContent = note.title || 'Untitled';
            header.style.background = note.color;

            const body = document.createElement('div');
            body.className = 'note-card-body';

            const preview = (note.items || []).slice(0, 4);
            preview.forEach(item => {
                const row = document.createElement('div');
                row.className = 'note-card-item' + (item.is_done ? ' done' : '');
                const marker = document.createElement('span');
                marker.className = item.is_done ? 'check' : 'bullet';
                marker.textContent = item.is_done ? '\u2713' : '\u2022';
                const txt = document.createElement('span');
                txt.textContent = item.text;
                row.appendChild(marker);
                row.appendChild(txt);
                body.appendChild(row);
            });

            const footer = document.createElement('div');
            footer.className = 'note-card-footer';
            footer.innerHTML = '<span>' + (note.items ? note.items.length : 0) + ' items</span><span>' + formatDate(note.updated_at) + '</span>';

            card.appendChild(header);
            card.appendChild(body);
            card.appendChild(footer);

            card.addEventListener('click', () => {
                window.location.href = '/notes/' + note.id;
            });

            grid.appendChild(card);
        });
    }

    function loadNotes() {
        api('/api/notes').then(renderNotes).catch(err => console.error('Failed to load notes', err));
    }

    // FAB -> open modal
    fab.addEventListener('click', () => {
        selectedColor = '#FFEB3B';
        titleInput.value = '';
        buildColorPicker(colorPicker, selectedColor, hex => { selectedColor = hex; });
        modal.style.display = '';
        titleInput.focus();
    });

    cancelBtn.addEventListener('click', () => { modal.style.display = 'none'; });

    modal.addEventListener('click', e => { if (e.target === modal) modal.style.display = 'none'; });

    okBtn.addEventListener('click', () => {
        const title = titleInput.value.trim() || 'Untitled';
        api('/api/notes', {
            method: 'POST',
            body: JSON.stringify({ title, color: selectedColor }),
        }).then(note => {
            modal.style.display = 'none';
            window.location.href = '/notes/' + note.id;
        }).catch(err => console.error('Failed to create note', err));
    });

    titleInput.addEventListener('keydown', e => {
        if (e.key === 'Enter') okBtn.click();
    });

    loadNotes();
}

/* ============================================================
   Note Detail Page
   ============================================================ */

function initNoteDetailPage() {
    const page = document.getElementById('note-detail-page');
    if (!page) return;

    const noteId = page.dataset.noteId;

    // DOM refs
    const detailHeader = document.getElementById('detail-header');
    const titleEl = document.getElementById('detail-title');
    const titleInput = document.getElementById('detail-title-input');
    const colorPickerEl = document.getElementById('detail-color-picker');
    const editToggleBtn = document.getElementById('edit-toggle-btn');
    const menuBtn = document.getElementById('menu-btn');
    const dropdownMenu = document.getElementById('dropdown-menu');
    const deleteNoteBtn = document.getElementById('delete-note-btn');
    const metaEl = document.getElementById('detail-meta');
    const editToolbar = document.getElementById('edit-toolbar');
    const addItemBtn = document.getElementById('add-item-btn');
    const itemsList = document.getElementById('items-list');
    const undoRedoBar = document.getElementById('undo-redo-bar');
    const undoBtn = document.getElementById('undo-btn');
    const redoBtn = document.getElementById('redo-btn');

    // Add item modal
    const addModal = document.getElementById('add-item-modal');
    const addItemText = document.getElementById('add-item-text');
    const addItemNext = document.getElementById('add-item-next');
    const addItemCancel = document.getElementById('add-item-cancel');
    const addItemOk = document.getElementById('add-item-ok');

    // Delete confirm modal
    const deleteModal = document.getElementById('delete-confirm-modal');
    const deleteCancelBtn = document.getElementById('delete-cancel');
    const deleteConfirmBtn = document.getElementById('delete-confirm');

    let note = null;
    let editMode = false;

    // Undo/redo stacks store snapshots of items
    let undoStack = [];
    let redoStack = [];

    function pushUndo() {
        undoStack.push(JSON.parse(JSON.stringify(note.items)));
        redoStack = [];
        updateUndoRedoState();
    }

    function updateUndoRedoState() {
        undoBtn.disabled = undoStack.length === 0;
        redoBtn.disabled = redoStack.length === 0;
    }

    /* --- Load note --- */
    function loadNote() {
        api('/api/notes/' + noteId).then(data => {
            note = data;
            render();
        }).catch(err => {
            console.error('Failed to load note', err);
            itemsList.innerHTML = '<li style="padding:20px;color:#999;">Note not found.</li>';
        });
    }

    /* --- Render --- */
    function render() {
        // Header color
        detailHeader.style.background = note.color;
        detailHeader.style.color = textColor(note.color);

        // Title
        titleEl.textContent = note.title || 'Untitled';
        titleInput.value = note.title || '';

        // Meta
        metaEl.textContent = 'Updated ' + formatDate(note.updated_at);

        // Toggle view/edit elements
        if (editMode) {
            page.classList.add('edit-mode');
            titleEl.style.display = 'none';
            titleInput.style.display = '';
            colorPickerEl.style.display = '';
            editToggleBtn.innerHTML = '&#10003;'; // checkmark
            editToggleBtn.title = 'Save';
            editToolbar.style.display = '';
            undoRedoBar.style.display = '';
            buildColorPicker(colorPickerEl, note.color, hex => {
                note.color = hex;
                api('/api/notes/' + noteId, { method: 'PUT', body: JSON.stringify({ color: hex }) })
                    .then(() => render());
            });
        } else {
            page.classList.remove('edit-mode');
            titleEl.style.display = '';
            titleInput.style.display = 'none';
            colorPickerEl.style.display = 'none';
            editToggleBtn.innerHTML = '&#9998;'; // pencil
            editToggleBtn.title = 'Edit';
            editToolbar.style.display = 'none';
            undoRedoBar.style.display = 'none';
        }

        renderItems();
    }

    function renderItems() {
        itemsList.innerHTML = '';
        const items = (note.items || []).sort((a, b) => a.sort_order - b.sort_order);

        items.forEach((item, idx) => {
            const li = document.createElement('li');
            li.className = 'item-row';
            li.dataset.itemId = item.id;
            li.dataset.index = idx;

            // Drag handle
            const drag = document.createElement('span');
            drag.className = 'item-drag-handle';
            drag.textContent = '\u2195';
            drag.draggable = true;

            // Check mark
            const check = document.createElement('span');
            check.className = 'item-check ' + (item.is_done ? 'done' : 'undone');
            check.textContent = item.is_done ? '\u2713' : '\u25CB';

            // Text
            const text = document.createElement('span');
            text.className = 'item-text' + (item.is_done ? ' done' : '');
            text.textContent = item.text;

            // Delete btn
            const del = document.createElement('button');
            del.className = 'item-delete-btn';
            del.textContent = '\u2715';
            del.title = 'Delete item';

            li.appendChild(drag);
            li.appendChild(check);
            li.appendChild(text);
            li.appendChild(del);

            // --- View mode: toggle done ---
            if (!editMode) {
                const toggle = () => {
                    const newDone = !item.is_done;
                    api('/api/notes/' + noteId + '/items/' + item.id, {
                        method: 'PUT',
                        body: JSON.stringify({ is_done: newDone }),
                    }).then(updated => {
                        item.is_done = updated.is_done;
                        check.className = 'item-check ' + (item.is_done ? 'done' : 'undone');
                        check.textContent = item.is_done ? '\u2713' : '\u25CB';
                        text.className = 'item-text' + (item.is_done ? ' done' : '');
                    });
                };
                check.style.cursor = 'pointer';
                text.style.cursor = 'pointer';
                check.addEventListener('click', toggle);
                text.addEventListener('click', toggle);
            }

            // --- Edit mode: delete ---
            del.addEventListener('click', () => {
                pushUndo();
                api('/api/notes/' + noteId + '/items/' + item.id, { method: 'DELETE' }).then(() => {
                    note.items = note.items.filter(i => i.id !== item.id);
                    renderItems();
                });
            });

            // --- Drag & Drop ---
            li.draggable = editMode;

            drag.addEventListener('dragstart', e => {
                li.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', idx.toString());
            });
            li.addEventListener('dragstart', e => {
                if (!editMode) { e.preventDefault(); return; }
                li.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', idx.toString());
            });
            li.addEventListener('dragend', () => { li.classList.remove('dragging'); });
            li.addEventListener('dragover', e => {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
                li.classList.add('drag-over');
            });
            li.addEventListener('dragleave', () => { li.classList.remove('drag-over'); });
            li.addEventListener('drop', e => {
                e.preventDefault();
                li.classList.remove('drag-over');
                const fromIdx = parseInt(e.dataTransfer.getData('text/plain'), 10);
                const toIdx = idx;
                if (fromIdx === toIdx) return;
                pushUndo();
                // Reorder locally
                const sorted = items.slice();
                const [moved] = sorted.splice(fromIdx, 1);
                sorted.splice(toIdx, 0, moved);
                // Assign new sort orders
                const reorderData = sorted.map((it, i) => {
                    it.sort_order = i;
                    return { id: it.id, sort_order: i };
                });
                note.items = sorted;
                renderItems();
                // Persist
                api('/api/notes/' + noteId + '/items/reorder', {
                    method: 'PUT',
                    body: JSON.stringify(reorderData),
                });
            });

            itemsList.appendChild(li);
        });
    }

    /* --- Edit mode toggle --- */
    editToggleBtn.addEventListener('click', () => {
        if (editMode) {
            // Save title if changed
            const newTitle = titleInput.value.trim();
            if (newTitle !== note.title) {
                api('/api/notes/' + noteId, { method: 'PUT', body: JSON.stringify({ title: newTitle }) })
                    .then(updated => { note.title = updated.title; note.updated_at = updated.updated_at; });
            }
            editMode = false;
            undoStack = [];
            redoStack = [];
        } else {
            editMode = true;
        }
        render();
    });

    /* --- Dropdown menu --- */
    menuBtn.addEventListener('click', e => {
        e.stopPropagation();
        dropdownMenu.style.display = dropdownMenu.style.display === 'none' ? '' : 'none';
    });
    document.addEventListener('click', () => { dropdownMenu.style.display = 'none'; });

    /* --- Delete note --- */
    deleteNoteBtn.addEventListener('click', () => {
        dropdownMenu.style.display = 'none';
        deleteModal.style.display = '';
    });
    deleteCancelBtn.addEventListener('click', () => { deleteModal.style.display = 'none'; });
    deleteModal.addEventListener('click', e => { if (e.target === deleteModal) deleteModal.style.display = 'none'; });
    deleteConfirmBtn.addEventListener('click', () => {
        api('/api/notes/' + noteId, { method: 'DELETE' }).then(() => {
            window.location.href = '/notes';
        });
    });

    /* --- Add item modal --- */
    addItemBtn.addEventListener('click', () => {
        addItemText.value = '';
        addModal.style.display = '';
        addItemText.focus();
    });
    addItemCancel.addEventListener('click', () => { addModal.style.display = 'none'; });
    addModal.addEventListener('click', e => { if (e.target === addModal) addModal.style.display = 'none'; });

    function addCurrentItem(keepOpen) {
        const text = addItemText.value.trim();
        if (!text) { addItemText.focus(); return; }
        pushUndo();
        const sortOrder = note.items.length;
        api('/api/notes/' + noteId + '/items', {
            method: 'POST',
            body: JSON.stringify({ text, sort_order: sortOrder }),
        }).then(newItem => {
            note.items.push(newItem);
            renderItems();
            if (keepOpen) {
                addItemText.value = '';
                addItemText.focus();
            } else {
                addModal.style.display = 'none';
            }
        });
    }

    addItemNext.addEventListener('click', () => addCurrentItem(true));
    addItemOk.addEventListener('click', () => addCurrentItem(false));
    addItemText.addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            addCurrentItem(false);
        }
    });

    /* --- Undo / Redo --- */
    undoBtn.addEventListener('click', () => {
        if (undoStack.length === 0) return;
        redoStack.push(JSON.parse(JSON.stringify(note.items)));
        const prev = undoStack.pop();
        applySnapshot(prev);
    });

    redoBtn.addEventListener('click', () => {
        if (redoStack.length === 0) return;
        undoStack.push(JSON.parse(JSON.stringify(note.items)));
        const next = redoStack.pop();
        applySnapshot(next);
    });

    function applySnapshot(snapshot) {
        // We restore items from snapshot by syncing with backend
        // First, figure out diffs and apply via reorder endpoint with full state
        note.items = snapshot;
        renderItems();
        updateUndoRedoState();

        // Persist: reload note from server, then reconcile
        // For simplicity, we re-push the entire ordering
        const reorderData = snapshot.map((it, i) => ({ id: it.id, sort_order: i }));
        api('/api/notes/' + noteId + '/items/reorder', {
            method: 'PUT',
            body: JSON.stringify(reorderData),
        }).then(updated => {
            // Refresh from server to stay in sync
            note = updated;
            renderItems();
        });
    }

    /* --- Init --- */
    loadNote();
}

/* ============================================================
   Boot
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
    initNotesPage();
    initNoteDetailPage();
});
