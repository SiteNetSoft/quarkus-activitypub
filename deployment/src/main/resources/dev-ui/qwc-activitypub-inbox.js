import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';

/**
 * The last inbox outcomes (newest first): when, type, actor and result.
 */
export class QwcActivitypubInbox extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 1rem; padding: 1rem; }
        table { border-collapse: collapse; }
        th, td { text-align: left; padding: 0.25rem 0.75rem; border-bottom: 1px solid var(--lumo-contrast-10pct); }
        .error { color: var(--lumo-error-text-color); }
    `;

    static properties = {
        _entries: { state: true },
        _error: { state: true },
    };

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    render() {
        return html`
            <div><vaadin-button @click=${this._refresh}>Refresh</vaadin-button></div>
            ${this._renderEntries()}
        `;
    }

    _renderEntries() {
        if (this._error) {
            return html`<span class="error">${this._error}</span>`;
        }
        if (!this._entries) {
            return html`<span>Loading...</span>`;
        }
        if (this._entries.length === 0) {
            return html`<span>Nothing received yet.</span>`;
        }
        return html`
            <table>
                <tr><th>At</th><th>Type</th><th>Actor</th><th>Result</th></tr>
                ${this._entries.map(e => html`
                    <tr><td>${e.at}</td><td>${e.type}</td><td><code>${e.actor ?? ''}</code></td><td>${e.result}</td></tr>`)}
            </table>`;
    }

    _refresh() {
        this._error = null;
        this.jsonRpc.inboxLog()
            .then(r => { this._entries = r.result; })
            .catch(e => { this._error = (e && e.error && e.error.message) || 'inboxLog() failed'; });
    }
}
customElements.define('qwc-activitypub-inbox', QwcActivitypubInbox);
