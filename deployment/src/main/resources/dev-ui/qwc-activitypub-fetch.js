import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/text-field';
import '@vaadin/button';

/**
 * Fetches a remote (or local) document by URL, or an actor by user@host, through the extension's client.
 */
export class QwcActivitypubFetch extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 1rem; padding: 1rem; }
        .row { display: flex; gap: 0.5rem; align-items: flex-end; }
        vaadin-text-field { flex: 1; }
        pre { background: var(--lumo-contrast-5pct); padding: 0.75rem; overflow: auto; }
    `;

    static properties = {
        _target: { state: true },
        _result: { state: true },
    };

    constructor() {
        super();
        this._target = 'alice@mastodon.example';
        this._result = '';
    }

    render() {
        return html`
            <div class="row">
                <vaadin-text-field label="URL or user@host" .value=${this._target}
                    @value-changed=${e => { this._target = e.detail.value; }}></vaadin-text-field>
                <vaadin-button theme="primary" @click=${this._fetch}>Fetch</vaadin-button>
            </div>
            ${this._result ? html`<pre>${this._result}</pre>` : ''}
        `;
    }

    _fetch() {
        this._result = 'Loading...';
        this.jsonRpc.fetch({ uriOrAcct: this._target })
            .then(r => { this._result = JSON.stringify(r.result, null, 2); })
            .catch(e => { this._result = (e && e.error && e.error.message) || 'fetch() failed'; });
    }
}
customElements.define('qwc-activitypub-fetch', QwcActivitypubFetch);
