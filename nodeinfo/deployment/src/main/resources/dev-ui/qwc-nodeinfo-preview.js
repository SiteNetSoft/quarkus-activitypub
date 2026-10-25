import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';

/**
 * Shows the NodeInfo 2.0 and 2.1 documents the server currently answers with.
 */
export class QwcNodeinfoPreview extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 1rem; padding: 1rem; }
        pre { background: var(--lumo-contrast-5pct); padding: 0.75rem; overflow: auto; }
    `;

    static properties = {
        _documents: { state: true },
    };

    constructor() {
        super();
        this._documents = {};
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    render() {
        return html`
            <div><vaadin-button @click=${this._refresh}>Refresh</vaadin-button></div>
            ${['2.1', '2.0'].map(v => html`
                <div>
                    <strong>NodeInfo ${v}</strong>
                    <pre>${this._documents[v] ?? 'Loading...'}</pre>
                </div>`)}
        `;
    }

    _refresh() {
        ['2.1', '2.0'].forEach(v => this.jsonRpc.document({ version: v }).then(r => {
            this._documents = { ...this._documents, [v]: JSON.stringify(r.result, null, 2) };
        }));
    }
}
customElements.define('qwc-nodeinfo-preview', QwcNodeinfoPreview);
