import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/select';

const STATES = ['PENDING', 'FAILED', 'GIVEN_UP', 'DELIVERED'];

/**
 * The delivery queue by state, newest first, with "retry now" for pending, failed and given-up deliveries.
 */
export class QwcActivitypubDeliveries extends LitElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 1rem; padding: 1rem; }
        .row { display: flex; gap: 0.5rem; align-items: flex-end; }
        table { border-collapse: collapse; }
        th, td { text-align: left; padding: 0.25rem 0.75rem; border-bottom: 1px solid var(--lumo-contrast-10pct); vertical-align: top; }
        .error { color: var(--lumo-error-text-color); }
    `;

    static properties = {
        _state: { state: true },
        _deliveries: { state: true },
        _error: { state: true },
        _message: { state: true },
    };

    constructor() {
        super();
        this._state = 'PENDING';
        this._items = STATES.map(s => ({ label: s, value: s }));
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    render() {
        return html`
            <div class="row">
                <vaadin-select label="State" .items=${this._items} .value=${this._state}
                    @value-changed=${e => { if (e.detail.value && e.detail.value !== this._state) { this._state = e.detail.value; this._refresh(); } }}></vaadin-select>
                <vaadin-button @click=${this._refresh}>Refresh</vaadin-button>
            </div>
            ${this._message ? html`<span>${this._message}</span>` : ''}
            ${this._renderDeliveries()}
        `;
    }

    _renderDeliveries() {
        if (this._error) {
            return html`<span class="error">${this._error}</span>`;
        }
        if (!this._deliveries) {
            return html`<span>Loading...</span>`;
        }
        if (this._deliveries.length === 0) {
            return html`<span>No ${this._state} deliveries.</span>`;
        }
        return html`
            <table>
                <tr><th>Created</th><th>Activity</th><th>Inbox</th><th>Attempts</th><th>Next attempt</th><th>Last error</th><th></th></tr>
                ${this._deliveries.map(d => html`
                    <tr>
                        <td>${d.created}</td>
                        <td><code>${d.activityId}</code></td>
                        <td><code>${d.inbox}</code></td>
                        <td>${d.attempts}</td>
                        <td>${d.state === 'PENDING' ? d.nextAttempt : ''}</td>
                        <td>${d.lastError ?? ''}</td>
                        <td>${d.state === 'DELIVERED' ? '' : html`<vaadin-button theme="small" @click=${() => this._retry(d.id)}>Retry now</vaadin-button>`}</td>
                    </tr>`)}
            </table>`;
    }

    _refresh() {
        this._error = null;
        this._deliveries = null;
        this.jsonRpc.deliveries({ state: this._state })
            .then(r => {
                if (r.result && r.result.error) {
                    this._error = r.result.error;
                } else {
                    this._deliveries = r.result;
                }
            })
            .catch(e => { this._error = errorText(e, 'deliveries() failed'); });
    }

    _retry(id) {
        this._message = null;
        this.jsonRpc.retryNow({ id: id })
            .then(r => {
                this._message = r.result.error ? r.result.error : `Delivery ${id} is due now; a delivery poll is running.`;
                this._refresh();
            })
            .catch(e => { this._message = errorText(e, 'retryNow() failed'); });
    }
}

function errorText(e, fallback) {
    return (e && e.error && e.error.message) || fallback;
}

customElements.define('qwc-activitypub-deliveries', QwcActivitypubDeliveries);
