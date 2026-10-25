(function () {
    'use strict';

    var THEME_KEY = 'rf-theme';
    var TREE_KEY = 'rf-tree-collapsed';
    var PANELS_KEY = 'rf-panels-collapsed';

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-bs-theme', theme);
        document.querySelectorAll('[data-theme-icon]').forEach(function (el) {
            el.className = theme === 'dark' ? 'bi bi-sun' : 'bi bi-moon-stars';
        });
    }

    function storedTheme() {
        try {
            return localStorage.getItem(THEME_KEY);
        } catch (e) {
            return null;
        }
    }

    function initTheme() {
        var theme = storedTheme();
        if (!theme) {
            theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
        }
        applyTheme(theme);
    }

    function toggleTheme() {
        var next = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
        try {
            localStorage.setItem(THEME_KEY, next);
        } catch (e) {   }
        applyTheme(next);
    }

    function loadSet(storageKey) {
        try {
            return new Set(JSON.parse(localStorage.getItem(storageKey) || '[]'));
        } catch (e) {
            return new Set();
        }
    }

    function saveSet(storageKey, set) {
        try {
            localStorage.setItem(storageKey, JSON.stringify(Array.from(set)));
        } catch (e) {   }
    }

    function initCollapsibles(config, containers, keepOpen) {
        var collapsed = loadSet(config.storageKey);
        containers.forEach(function (container) {
            if (collapsed.has(container.getAttribute(config.keyAttribute)) && !keepOpen(container)) {
                container.classList.add(config.collapsedClass);
            }
            config.refresh(container);
        });
    }

    function toggleCollapsible(config, button) {
        var container = button.closest(config.selector);
        if (!container) {
            return;
        }
        container.classList.toggle(config.collapsedClass);
        var collapsed = loadSet(config.storageKey);
        var key = container.getAttribute(config.keyAttribute);
        if (container.classList.contains(config.collapsedClass)) {
            collapsed.add(key);
        } else {
            collapsed.delete(key);
        }
        saveSet(config.storageKey, collapsed);
        config.refresh(container);
    }

    var TREE = {
        storageKey: TREE_KEY,
        selector: '[data-node-path]',
        keyAttribute: 'data-node-path',
        collapsedClass: 'rf-tree-collapsed',
        refresh: updateToggleIcon
    };

    function updateToggleIcon(li) {
        var toggle = li.querySelector(':scope > .rf-tree-item > .rf-tree-toggle .bi');
        if (!toggle) {
            return;
        }
        var isCollapsed = li.classList.contains('rf-tree-collapsed');
        toggle.className = isCollapsed ? 'bi bi-caret-right-fill' : 'bi bi-caret-down-fill';
    }

    var PANELS = {
        storageKey: PANELS_KEY,
        selector: '.rf-panel[data-panel]',
        keyAttribute: 'data-panel',
        collapsedClass: 'rf-panel-collapsed',
        refresh: applyPanelState
    };

    function applyPanelState(panel) {
        var isCollapsed = panel.classList.contains('rf-panel-collapsed');
        var toggle = panel.querySelector(':scope > .rf-panel-toggle');
        if (toggle) {
            toggle.setAttribute('aria-expanded', isCollapsed ? 'false' : 'true');
        }
        var caret = panel.querySelector('[data-panel-caret]');
        if (caret) {
            caret.className = isCollapsed
                ? 'bi bi-chevron-down rf-muted ms-auto'
                : 'bi bi-chevron-up rf-muted ms-auto';
        }
    }

    function copyToClipboard(button) {
        var targetSelector = button.getAttribute('data-copy-target');
        var text = targetSelector
            ? (document.querySelector(targetSelector) || {}).innerText
            : button.getAttribute('data-copy-text');
        if (!text) {
            return;
        }
        var done = function () {
            var original = button.getAttribute('data-original-label') || button.innerHTML;
            button.setAttribute('data-original-label', original);
            button.innerHTML = '<i class="bi bi-check2"></i>';
            setTimeout(function () {
                button.innerHTML = original;
            }, 1400);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(done, function () {
                fallbackCopy(text, done);
            });
        } else {
            fallbackCopy(text, done);
        }
    }

    function fallbackCopy(text, done) {
        var area = document.createElement('textarea');
        area.value = text;
        area.setAttribute('readonly', '');
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.appendChild(area);
        area.select();
        try {
            document.execCommand('copy');
            done();
        } catch (e) {   }
        document.body.removeChild(area);
    }

    function toggleEndpoint(head) {
        var card = head.closest('.rf-endpoint');
        if (!card) {
            return;
        }
        var body = card.querySelector('.rf-endpoint-body');
        if (!body) {
            return;
        }
        var hidden = body.hasAttribute('hidden');
        if (hidden) {
            body.removeAttribute('hidden');
        } else {
            body.setAttribute('hidden', '');
        }
        head.setAttribute('aria-expanded', hidden ? 'true' : 'false');
        var caret = head.querySelector('[data-endpoint-caret]');
        if (caret) {
            caret.className = hidden ? 'bi bi-chevron-up rf-muted' : 'bi bi-chevron-down rf-muted';
        }
    }

    document.addEventListener('click', function (event) {
        var themeToggle = event.target.closest('[data-action="toggle-theme"]');
        if (themeToggle) {
            event.preventDefault();
            toggleTheme();
            return;
        }
        var sidebarToggle = event.target.closest('[data-action="toggle-sidebar"]');
        if (sidebarToggle) {
            event.preventDefault();
            var sidebar = document.querySelector('.rf-sidebar');
            if (sidebar) {
                sidebar.classList.toggle('rf-open');
            }
            return;
        }
        var treeToggle = event.target.closest('.rf-tree-toggle');
        if (treeToggle) {
            event.preventDefault();
            toggleCollapsible(TREE, treeToggle);
            return;
        }
        var panelToggle = event.target.closest('.rf-panel-toggle');
        if (panelToggle) {
            event.preventDefault();
            toggleCollapsible(PANELS, panelToggle);
            return;
        }
        var copyButton = event.target.closest('[data-action="copy"]');
        if (copyButton) {
            event.preventDefault();
            copyToClipboard(copyButton);
            return;
        }
        var endpointHead = event.target.closest('.rf-endpoint-head');
        if (endpointHead) {
            event.preventDefault();
            toggleEndpoint(endpointHead);
            return;
        }
    });

    var pendingConfirmation = null;
    var approvedForm = null;
    var busyForms = new Map();

    function isDeletion(form, button) {
        return /\/delete\/?$/.test(new URL(button && button.formAction || form.action, window.location.href).pathname);
    }

    function markBusy(form, button) {
        var status = document.getElementById('rf-operation-status');
        var label = form.getAttribute('data-busy-label') || status.getAttribute(
            isDeletion(form, button) ? 'data-deleting' :
                form.enctype === 'multipart/form-data' ? 'data-uploading' : 'data-saving');
        var controls = Array.from(form.querySelectorAll('button, input[type="submit"]'));
        var state = {button: button, html: button ? button.innerHTML : null,
            value: button ? button.value : null, aria: form.getAttribute('aria-busy'),
            controls: controls.map(function (control) { return [control, control.getAttribute('aria-disabled')]; })};
        busyForms.set(form, state);
        form.setAttribute('aria-busy', 'true');
        controls.forEach(function (control) { control.setAttribute('aria-disabled', 'true'); });
        if (button && button.tagName === 'BUTTON') {
            button.replaceChildren();
            var spinner = document.createElement('span');
            spinner.className = 'spinner-border spinner-border-sm me-2';
            spinner.setAttribute('aria-hidden', 'true');
            button.appendChild(spinner);
            button.appendChild(document.createTextNode(label));
        } else if (button) {
            button.value = label;
        }
        status.textContent = label;
    }

    document.addEventListener('click', function (event) {
        var control = event.target.closest('button, input[type="submit"]');
        if (control && control.form && busyForms.has(control.form)) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (busyForms.has(form)) {
            event.preventDefault();
            return;
        }
        var button = event.submitter || form.querySelector('button[type="submit"], input[type="submit"]');
        var confirmation = button && button.getAttribute('data-confirm') || form.getAttribute('data-confirm');
        if (confirmation && approvedForm !== form) {
            event.preventDefault();
            var modalElement = document.getElementById('rf-confirm-modal');
            document.getElementById('rf-confirm-message').textContent = confirmation;
            var accept = document.getElementById('rf-confirm-accept');
            var deleting = isDeletion(form, button);
            accept.textContent = accept.getAttribute(deleting ? 'data-delete-label' : 'data-confirm-label');
            accept.classList.toggle('btn-danger', deleting);
            accept.classList.toggle('btn-primary', !deleting);
            pendingConfirmation = {form: form, button: button, trigger: document.activeElement};
            bootstrap.Modal.getOrCreateInstance(modalElement).show();
            return;
        }
        if (event.defaultPrevented || form.method.toLowerCase() === 'get') {
            return;
        }
        markBusy(form, button);
    });

    window.addEventListener('pageshow', function () {
        busyForms.forEach(function (state, form) {
            if (state.aria === null) { form.removeAttribute('aria-busy'); }
            else { form.setAttribute('aria-busy', state.aria); }
            state.controls.forEach(function (item) {
                if (item[1] === null) { item[0].removeAttribute('aria-disabled'); }
                else { item[0].setAttribute('aria-disabled', item[1]); }
            });
            if (state.button) {
                state.button.innerHTML = state.html;
                state.button.value = state.value;
            }
        });
        busyForms.clear();
        var status = document.getElementById('rf-operation-status');
        if (status) { status.textContent = ''; }
    });

    document.addEventListener('input', function (event) {
        var source = event.target.closest('[data-slug-source]');
        if (!source) {
            return;
        }
        var target = document.querySelector(source.getAttribute('data-slug-source'));
        if (!target || target.dataset.touched === 'true') {
            return;
        }
        target.value = source.value
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-+|-+$/g, '');
    });

    document.addEventListener('change', function (event) {
        var slugField = event.target.closest('[data-slug-field]');
        if (slugField && slugField.value) {
            slugField.dataset.touched = 'true';
        }
    });

    initTheme();
    document.addEventListener('DOMContentLoaded', function () {

        var confirmModal = document.getElementById('rf-confirm-modal');
        confirmModal.addEventListener('shown.bs.modal', function () {
            document.getElementById('rf-confirm-cancel').focus();
        });
        confirmModal.addEventListener('hidden.bs.modal', function () {
            if (pendingConfirmation && pendingConfirmation.trigger) { pendingConfirmation.trigger.focus(); }
            pendingConfirmation = null;
        });
        document.getElementById('rf-confirm-accept').addEventListener('click', function () {
            if (!pendingConfirmation) { return; }
            var pending = pendingConfirmation;
            pendingConfirmation = null;
            bootstrap.Modal.getInstance(confirmModal).hide();
            approvedForm = pending.form;
            try {
                if (pending.button) { pending.form.requestSubmit(pending.button); }
                else { pending.form.requestSubmit(); }
            } finally { approvedForm = null; }
        });

        initCollapsibles(TREE, document.querySelectorAll('.rf-tree [data-node-path]'), function (li) {
            return li.querySelector('.rf-tree-link.active') !== null;
        });

        initCollapsibles(PANELS, document.querySelectorAll('.rf-panel[data-panel]'), function (panel) {
            return panel.querySelector('[data-panel-keep-open]') !== null;
        });

        document.querySelectorAll('[data-autodismiss]').forEach(function (el) {
            setTimeout(function () {
                el.classList.add('fade');
                el.style.opacity = '0';
                setTimeout(function () {
                    el.remove();
                }, 400);
            }, 6000);
        });
    });
})();
