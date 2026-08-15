/* ------------------------------------------------------------------
   JSON Tools - client behaviour.

   The page works without this file: the form POSTs and the server
   re-renders. This upgrades it to in-place updates and adds the tree
   view, the HTML preview and the client-side downloads.
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    var ctx = document.body.getAttribute('data-ctx') || '';

    // ------------------------------------------------------------------
    // Sidebar: mobile toggle and search filter
    // ------------------------------------------------------------------

    var navToggle = document.getElementById('nav-toggle');
    var sidebar = document.getElementById('sidebar');

    if (navToggle && sidebar) {
        navToggle.addEventListener('click', function () {
            var open = sidebar.classList.toggle('is-open');
            navToggle.setAttribute('aria-expanded', String(open));
        });
    }

    var search = document.getElementById('tool-search');
    if (search && sidebar) {
        search.addEventListener('input', function () {
            var term = search.value.trim().toLowerCase();
            sidebar.querySelectorAll('[data-group]').forEach(function (group) {
                var anyVisible = false;
                group.querySelectorAll('a[data-name]').forEach(function (link) {
                    var match = !term || link.getAttribute('data-name').toLowerCase().indexOf(term) !== -1;
                    link.parentElement.hidden = !match;
                    anyVisible = anyVisible || match;
                });
                group.hidden = !anyVisible;
            });
        });
    }

    // ------------------------------------------------------------------
    // Tool page
    // ------------------------------------------------------------------

    var article = document.querySelector('.tool');
    if (!article) {
        return;
    }

    var toolId = article.getAttribute('data-tool');
    var isBinaryDownload = article.getAttribute('data-binary-download') === 'true';
    var downloadName = article.getAttribute('data-download-name') || 'output.txt';
    var outputView = article.getAttribute('data-output-view');

    var form = document.getElementById('tool-form');
    var input = document.getElementById('input');
    var secondInput = document.getElementById('secondInput');
    var output = document.getElementById('output');
    var runButton = document.getElementById('run-button');
    var inputStatus = document.getElementById('input-status');

    var errorBox = document.getElementById('error-box');
    var errorMessage = document.getElementById('error-message');
    var okBox = document.getElementById('ok-box');
    var okMessage = document.getElementById('ok-message');
    var statsList = document.getElementById('stats');
    var detailWrap = document.getElementById('detail-wrap');
    var detailTable = document.getElementById('detail-table');
    var treeView = document.getElementById('tree-view');
    var previewFrame = document.getElementById('preview-frame');
    var fileInput = document.getElementById('file-input');

    var sampleData = document.getElementById('sample-data');
    var sampleData2 = document.getElementById('sample-data-2');

    var isTree = outputView === 'tree';
    var isReport = outputView === 'report';
    var isDiff = outputView === 'diff';

    // The server may already have rendered a result (no-JavaScript POST).
    if (isTree && output && output.value.trim()) {
        renderTree(output.value);
    }

    // ------------------------------------------------------------------
    // Running the tool
    // ------------------------------------------------------------------

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        run();
    });

    // Ctrl/Cmd + Enter runs from anywhere in the form.
    form.addEventListener('keydown', function (event) {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
            event.preventDefault();
            run();
        }
    });

    function collectOptions() {
        var options = {};
        Array.prototype.forEach.call(form.elements, function (element) {
            if (!element.name || element.name === 'input' || element.name === 'secondInput') {
                return;
            }
            options[element.name] = element.type === 'checkbox'
                ? String(element.checked)
                : element.value;
        });
        return options;
    }

    function requestBody() {
        return {
            input: input ? input.value : '',
            secondInput: secondInput ? secondInput.value : '',
            options: collectOptions()
        };
    }

    function run() {
        setBusy(true);
        fetch(ctx + '/api/tool/' + encodeURIComponent(toolId), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody())
        }).then(function (response) {
            return response.json();
        }).then(function (result) {
            render(result);
        }).catch(function (error) {
            render({ ok: false, output: '', error: 'Could not reach the server: ' + error.message });
        }).finally(function () {
            setBusy(false);
        });
    }

    var runLabel = runButton ? runButton.textContent.trim() : '';

    function setBusy(busy) {
        if (!runButton) {
            return;
        }
        runButton.disabled = busy;
        runButton.textContent = busy ? 'Working…' : runLabel;
    }

    function render(result) {
        var failed = !result.ok;

        // Some tools (schema validation) return ok:false but still have a body.
        if (errorBox) {
            errorBox.hidden = !failed;
            if (failed && errorMessage) {
                errorMessage.textContent = result.error || 'Something went wrong.';
                var position = document.getElementById('error-position');
                if (result.line > 0) {
                    if (!position) {
                        position = document.createElement('span');
                        position.className = 'error-position';
                        position.id = 'error-position';
                        errorBox.appendChild(position);
                    }
                    position.hidden = false;
                    position.textContent = 'line ' + result.line + ', column ' + result.column;
                } else if (position) {
                    position.hidden = true;
                }
            }
        }

        if (okBox) {
            okBox.hidden = failed;
            if (!failed && okMessage) {
                okMessage.textContent = result.output || 'Valid.';
            }
        }

        if (output) {
            output.value = failed && !result.output ? '' : (result.output || '');
            output.hidden = isReport;
        }

        if (isTree) {
            renderTree(failed ? '' : result.output);
        }
        if (previewFrame && !previewFrame.hidden) {
            previewFrame.srcdoc = result.output || '';
        }

        renderDetails(result.details || []);
        renderStats(result.stats || {});
        if (inputStatus) {
            inputStatus.textContent = describeInput();
        }
    }

    function renderStats(stats) {
        if (!statsList) {
            return;
        }
        var keys = Object.keys(stats);
        statsList.innerHTML = '';
        statsList.hidden = keys.length === 0;
        keys.forEach(function (key) {
            var li = document.createElement('li');
            var label = document.createElement('span');
            label.className = 'stat-label';
            label.textContent = key;
            var value = document.createElement('span');
            value.className = 'stat-value';
            value.textContent = stats[key];
            li.appendChild(label);
            li.appendChild(value);
            statsList.appendChild(li);
        });
    }

    function renderDetails(rows) {
        if (!detailWrap || !detailTable) {
            return;
        }
        var body = detailTable.querySelector('tbody');
        body.innerHTML = '';
        detailWrap.hidden = rows.length === 0;

        rows.forEach(function (row) {
            var tr = document.createElement('tr');
            if (isDiff) {
                tr.appendChild(cell(row.path, 'mono'));
                var typeCell = document.createElement('td');
                var tag = document.createElement('span');
                tag.className = 'tag tag-' + (row.typeClass || 'changed');
                tag.textContent = row.type;
                typeCell.appendChild(tag);
                tr.appendChild(typeCell);
                tr.appendChild(cell(row.left, 'mono'));
                tr.appendChild(cell(row.right, 'mono'));
            } else {
                tr.appendChild(cell(row.location, 'mono'));
                tr.appendChild(cell(row.message, ''));
            }
            body.appendChild(tr);
        });
    }

    function cell(text, className) {
        var td = document.createElement('td');
        td.className = className;
        td.textContent = text == null ? '' : text;
        return td;
    }

    function describeInput() {
        if (!input) {
            return '';
        }
        var text = input.value;
        var lines = text ? text.split('\n').length : 0;
        return lines + (lines === 1 ? ' line, ' : ' lines, ') + text.length.toLocaleString() + ' characters';
    }

    if (input && inputStatus) {
        input.addEventListener('input', function () {
            inputStatus.textContent = describeInput();
        });
        inputStatus.textContent = describeInput();
    }

    // ------------------------------------------------------------------
    // Pane toolbar actions
    // ------------------------------------------------------------------

    var pendingUploadTarget = null;

    article.addEventListener('click', function (event) {
        var button = event.target.closest('[data-action]');
        if (!button) {
            return;
        }
        var action = button.getAttribute('data-action');

        if (action === 'sample' && sampleData && input) {
            input.value = sampleData.value;
            input.dispatchEvent(new Event('input'));
        } else if (action === 'sample2' && sampleData2 && secondInput) {
            secondInput.value = sampleData2.value;
        } else if (action === 'clear') {
            var target = document.getElementById(button.getAttribute('data-target'));
            if (target) {
                target.value = '';
                target.dispatchEvent(new Event('input'));
                target.focus();
            }
        } else if (action === 'upload' && fileInput) {
            pendingUploadTarget = button.getAttribute('data-target');
            fileInput.click();
        } else if (action === 'url') {
            loadFromUrl(button.getAttribute('data-target'));
        } else if (action === 'copy') {
            copyOutput(button);
        } else if (action === 'download') {
            download();
        } else if (action === 'toggle-preview') {
            togglePreview(button);
        }
    });

    if (fileInput) {
        fileInput.addEventListener('change', function () {
            var file = fileInput.files && fileInput.files[0];
            if (!file) {
                return;
            }
            var reader = new FileReader();
            reader.onload = function () {
                var target = document.getElementById(pendingUploadTarget || 'input');
                if (target) {
                    target.value = String(reader.result);
                    target.dispatchEvent(new Event('input'));
                }
            };
            reader.onerror = function () {
                window.alert('That file could not be read.');
            };
            reader.readAsText(file);
            fileInput.value = '';
        });
    }

    function loadFromUrl(targetId) {
        var url = window.prompt('Load a document from a public URL:', 'https://');
        if (!url) {
            return;
        }
        fetch(ctx + '/api/fetch', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url: url })
        }).then(function (response) {
            return response.json();
        }).then(function (body) {
            if (body.error) {
                window.alert(body.error);
                return;
            }
            var target = document.getElementById(targetId || 'input');
            if (target) {
                target.value = body.content;
                target.dispatchEvent(new Event('input'));
            }
        }).catch(function (error) {
            window.alert('Could not load that URL: ' + error.message);
        });
    }

    function copyOutput(button) {
        var text = output ? output.value : '';
        if (!text) {
            return;
        }
        var done = function () {
            var original = button.textContent;
            button.textContent = 'Copied';
            window.setTimeout(function () { button.textContent = original; }, 1200);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(done).catch(function () { legacyCopy(text, done); });
        } else {
            legacyCopy(text, done);
        }
    }

    function legacyCopy(text, done) {
        var helper = document.createElement('textarea');
        helper.value = text;
        helper.setAttribute('readonly', '');
        helper.style.position = 'fixed';
        helper.style.opacity = '0';
        document.body.appendChild(helper);
        helper.select();
        try {
            document.execCommand('copy');
            done();
        } catch (e) {
            window.alert('Copying is not available in this browser.');
        }
        document.body.removeChild(helper);
    }

    function download() {
        if (isBinaryDownload) {
            downloadWorkbook();
            return;
        }
        var text = output ? output.value : '';
        if (!text) {
            window.alert('Run the tool first - there is nothing to download yet.');
            return;
        }
        saveBlob(new Blob([text], { type: 'text/plain;charset=utf-8' }), downloadName);
    }

    function downloadWorkbook() {
        fetch(ctx + '/api/excel', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody())
        }).then(function (response) {
            if (!response.ok) {
                return response.text().then(function (message) { throw new Error(message); });
            }
            return response.blob();
        }).then(function (blob) {
            saveBlob(blob, downloadName);
        }).catch(function (error) {
            window.alert(error.message || 'The workbook could not be built.');
        });
    }

    function saveBlob(blob, name) {
        var url = URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        link.download = name;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    }

    function togglePreview(button) {
        if (!previewFrame || !output) {
            return;
        }
        var showPreview = previewFrame.hidden;
        previewFrame.hidden = !showPreview;
        output.hidden = showPreview;
        button.setAttribute('aria-pressed', String(showPreview));
        if (showPreview) {
            // The frame is sandboxed with no allowances, so the markup cannot
            // run scripts or reach back into this page.
            previewFrame.srcdoc = output.value;
        }
    }

    // ------------------------------------------------------------------
    // Tree view
    // ------------------------------------------------------------------

    function renderTree(text) {
        if (!treeView) {
            return;
        }
        treeView.innerHTML = '';
        if (!text || !text.trim()) {
            treeView.hidden = true;
            if (output) {
                output.hidden = false;
            }
            return;
        }
        var parsed;
        try {
            parsed = JSON.parse(text);
        } catch (e) {
            treeView.hidden = true;
            if (output) {
                output.hidden = false;
            }
            return;
        }
        treeView.hidden = false;
        if (output) {
            output.hidden = true;
        }
        treeView.appendChild(buildNode(null, parsed, '$', true));
    }

    function buildNode(key, value, path, expanded) {
        var node = document.createElement('div');
        node.className = 'tree-node';

        var row = document.createElement('div');
        row.className = 'tree-row';
        node.appendChild(row);

        var container = value !== null && typeof value === 'object';

        if (container) {
            var toggle = document.createElement('button');
            toggle.type = 'button';
            toggle.className = 'tree-toggle';
            toggle.textContent = expanded ? '▾' : '▸';
            toggle.setAttribute('aria-label', 'Expand or collapse');
            toggle.addEventListener('click', function () {
                var collapsed = node.classList.toggle('is-collapsed');
                toggle.textContent = collapsed ? '▸' : '▾';
            });
            row.appendChild(toggle);
        } else {
            var spacer = document.createElement('span');
            spacer.className = 'tree-toggle';
            spacer.textContent = ' ';
            row.appendChild(spacer);
        }

        if (key !== null) {
            var keySpan = document.createElement('span');
            keySpan.className = typeof key === 'number' ? 'tree-index' : 'tree-key';
            keySpan.textContent = typeof key === 'number' ? '[' + key + ']' : key;
            keySpan.title = path;
            row.appendChild(keySpan);
        }

        if (container) {
            var isArray = Array.isArray(value);
            var count = isArray ? value.length : Object.keys(value).length;
            var summary = document.createElement('span');
            summary.className = 'tree-summary';
            summary.textContent = isArray
                ? '[' + count + (count === 1 ? ' item]' : ' items]')
                : '{' + count + (count === 1 ? ' key}' : ' keys}');
            row.appendChild(summary);

            var children = document.createElement('div');
            children.className = 'tree-children';
            node.appendChild(children);

            if (isArray) {
                value.forEach(function (item, index) {
                    children.appendChild(buildNode(index, item, path + '[' + index + ']', false));
                });
            } else {
                Object.keys(value).forEach(function (name) {
                    children.appendChild(buildNode(name, value[name], path + '.' + name, false));
                });
            }
            if (!expanded) {
                node.classList.add('is-collapsed');
            }
        } else {
            var valueSpan = document.createElement('span');
            valueSpan.className = 'tree-' + (value === null ? 'null' : typeof value);
            valueSpan.textContent = value === null ? 'null'
                : typeof value === 'string' ? '"' + value + '"'
                : String(value);
            row.appendChild(valueSpan);
        }
        return node;
    }
}());
