(function () {
    console.log("WA AI Wrapper Injector Initialized");

    const TRIGGER = "!!!";
    const SELECTORS = {
        input: 'footer div[contenteditable="true"]',
        sendButton: 'footer button:has(span[data-icon="send"])',
        messageBubble: 'div[role="row"]',
        incomingBubble: '.message-in',
        messageText: 'span.selectable-text.copyable-text > span'
    };

    function findInput() {
        return document.querySelector(SELECTORS.input);
    }

    function findLastIncomingMessage() {
        const incoming = document.querySelectorAll(SELECTORS.incomingBubble);
        if (incoming.length === 0) return null;
        const last = incoming[incoming.length - 1];
        const textNode = last.querySelector(SELECTORS.messageText);
        return textNode ? textNode.innerText : null;
    }

    function handleSend(event) {
        const input = findInput();
        if (!input) return;

        const text = input.innerText.trim();
        if (text.startsWith(TRIGGER)) {
            event.preventDefault();
            event.stopPropagation();

            console.log("Trigger detected: " + text);

            // Clear input
            input.focus();
            document.execCommand('selectAll', false, null);
            document.execCommand('delete', false, null);

            const lastMessage = findLastIncomingMessage();
            if (window.Android) {
                window.Android.onTriggerDetected(lastMessage || "");
            }
        }
    }

    // Intercept Enter key
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            handleSend(e);
        }
    }, true);

    // Intercept click on send button
    document.addEventListener('click', function (e) {
        const sendBtn = e.target.closest('button');
        if (sendBtn && (sendBtn.querySelector('[data-icon="send"]') || sendBtn.getAttribute('aria-label') === 'Send')) {
            handleSend(e);
        }
    }, true);

    // Bridge for injection from Android
    window.injectReply = function (text) {
        const input = findInput();
        if (!input) {
            console.error("Input not found for injection");
            return;
        }

        input.focus();
        // Modern WhatsApp uses React/Draft.js, simple innerHTML might not work.
        // execCommand('insertText') is more reliable for simple fields.
        document.execCommand('insertText', false, text);

        // Click send button after a short delay
        setTimeout(() => {
            const sendBtn = document.querySelector('button[aria-label="Send"]') ||
                document.querySelector('footer button:has(span[data-icon="send"])');
            if (sendBtn) {
                sendBtn.click();
            }
        }, 500);
    };

    // Auto Mode Observer
    let autoModeEnabled = false;
    window.setAutoMode = function (enabled) {
        autoModeEnabled = enabled;
        console.log("Auto Mode: " + enabled);
        if (enabled) {
            startObserver();
        } else {
            stopObserver();
        }
    };

    let observer = null;
    function startObserver() {
        if (observer) return;
        const main = document.querySelector('#main');
        if (!main) return;

        observer = new MutationObserver((mutations) => {
            for (const mutation of mutations) {
                for (const node of mutation.addedNodes) {
                    if (node.nodeType === 1 && node.classList.contains('message-in')) {
                        const text = node.querySelector(SELECTORS.messageText)?.innerText;
                        if (text && window.Android && autoModeEnabled) {
                            window.Android.onTriggerDetected(text);
                        }
                    }
                }
            }
        });

        observer.observe(main, { childList: true, subtree: true });
    }

    function stopObserver() {
        if (observer) {
            observer.disconnect();
            observer = null;
        }
    }

})();
